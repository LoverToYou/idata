package com.idata.service.python;

import com.idata.entity.PythonRun;
import com.idata.entity.PythonScript;
import com.idata.mapper.PythonRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executes Python scripts asynchronously on a small fixed thread pool.
 * Writes the script to a temp file, runs {@code python3}, feeds {@code params} to stdin,
 * enforces a timeout, and records stdout/stderr/exit code back onto the {@code python_run} row.
 */
@Component
public class PythonRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonRunner.class);

    private final PythonRunMapper pythonRunMapper;
    private final ExecutorService executor;
    private final ConcurrentHashMap<Long, Process> running = new ConcurrentHashMap<>();

    public PythonRunner(PythonRunMapper pythonRunMapper) {
        this.pythonRunMapper = pythonRunMapper;
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "python-runner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedule execution in the background. Returns immediately; the run row is
     * updated by the worker thread when the process finishes.
     */
    public void execute(PythonScript script, String params, PythonRun run) {
        executor.submit(() -> runProcess(script, params, run));
    }

    /**
     * Execute in the calling thread (workflow nodes) and return the final run row
     * after the process finishes. Blocks until completion or timeout.
     */
    public PythonRun executeSync(PythonScript script, String params, PythonRun run) {
        runProcess(script, params, run);
        return pythonRunMapper.selectById(run.getId());
    }

    /**
     * Force-kill the process backing a run (used for cancellation).
     */
    public void kill(Long runId) {
        Process process = running.get(runId);
        if (process != null) {
            process.destroyForcibly();
        }
    }

    private void runProcess(PythonScript script, String params, PythonRun run) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("py-", ".py");
            Files.writeString(tempFile, script.getContent(), StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("python3", tempFile.toAbsolutePath().toString()).start();
            running.put(run.getId(), process);

            try (OutputStream os = process.getOutputStream()) {
                if (params != null && !params.isEmpty()) {
                    os.write(params.getBytes(StandardCharsets.UTF_8));
                }
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outThread = readAsync(process.getInputStream(), stdout);
            Thread errThread = readAsync(process.getErrorStream(), stderr);

            int timeout = script.getTimeoutSeconds() != null && script.getTimeoutSeconds() > 0
                    ? script.getTimeoutSeconds() : 60;
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            boolean timedOut = !completed;
            if (timedOut) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                stderr.append("(执行超时超过 ").append(timeout).append(" 秒，已强制终止)");
            }

            outThread.join(3000);
            errThread.join(3000);

            int exitCode = process.exitValue();
            PythonRun update = new PythonRun();
            update.setId(run.getId());
            update.setStdout(stdout.toString());
            update.setStderr(stderr.toString());
            update.setExitCode(exitCode);
            update.setFinishedAt(LocalDateTime.now());

            PythonRun current = pythonRunMapper.selectById(run.getId());
            if (current != null && "CANCELLED".equals(current.getStatus())) {
                // 用户主动取消，保留 CANCELLED，只补输出
            } else {
                update.setStatus(timedOut || exitCode != 0 ? "FAILED" : "SUCCESS");
            }
            pythonRunMapper.updateById(update);
            log.info("Python run {} finished, status={}, exit={}", run.getId(), update.getStatus(), exitCode);
        } catch (Exception e) {
            log.error("Python run {} failed: {}", run.getId(), e.getMessage(), e);
            PythonRun update = new PythonRun();
            update.setId(run.getId());
            update.setStatus("FAILED");
            update.setStderr("执行异常: " + e.getMessage());
            update.setFinishedAt(LocalDateTime.now());
            try {
                pythonRunMapper.updateById(update);
            } catch (Exception ex) {
                log.error("Failed to persist Python run {} result: {}", run.getId(), ex.getMessage(), ex);
            }
        } finally {
            if (run.getId() != null) {
                running.remove(run.getId());
            }
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Thread readAsync(InputStream is, StringBuilder sb) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                log.warn("Failed reading process stream: {}", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
