package com.idata.engine.datax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Executes DataX jobs by writing JSON config to temp files and invoking DataX via Python.
 */
@Component
public class DataXRunner {

    private static final Logger log = LoggerFactory.getLogger(DataXRunner.class);

    private final String dataxHome;

    public DataXRunner(@Value("${idata.datax.home:/opt/datax}") String dataxHome) {
        this.dataxHome = dataxHome;
    }

    /**
     * Execute a DataX job from a JSON config string.
     *
     * @param dataxJson the DataX job JSON configuration
     * @return DataXResult containing the process exit code, stdout, and stderr
     */
    public DataXResult execute(String dataxJson) {
        Path tempFile = null;
        try {
            // Write the JSON to a temporary file
            tempFile = Files.createTempFile("datax-", ".json");
            Files.writeString(tempFile, dataxJson, StandardCharsets.UTF_8);

            String dataxScript = dataxHome + "/bin/datax.py";
            String[] cmd = {"python", dataxScript, tempFile.toAbsolutePath().toString()};

            log.info("Executing DataX: {} {} {}", cmd[0], cmd[1], cmd[2]);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Capture stdout and stderr
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader stderrReader = new BufferedReader(
                         new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = stdoutReader.readLine()) != null) {
                    stdout.append(line).append(System.lineSeparator());
                    log.info("[DataX stdout] {}", line);
                }
                while ((line = stderrReader.readLine()) != null) {
                    stderr.append(line).append(System.lineSeparator());
                    log.warn("[DataX stderr] {}", line);
                }
            }

            int exitCode = process.waitFor();
            log.info("DataX process exited with code: {}", exitCode);

            return new DataXResult(exitCode, stdout.toString(), stderr.toString());

        } catch (IOException e) {
            throw new RuntimeException("Failed to execute DataX job: I/O error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DataX job was interrupted", e);
        } finally {
            // Clean up the temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Result of a DataX job execution.
     */
    public static class DataXResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public DataXResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
