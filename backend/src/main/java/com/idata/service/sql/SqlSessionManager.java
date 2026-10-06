package com.idata.service.sql;

import com.idata.service.datasource.DatasourceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理 SQL 编辑器会话：同一 sessionId 内复用同一个 JDBC 连接，保持 use db、
 * 会话变量、事务等连接级状态；空闲超时自动回收，防连接泄漏。
 */
@Service
public class SqlSessionManager {

    private final DatasourceService datasourceService;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Value("${idata.sql-session.idle-timeout:1800}")
    private long idleTimeoutSeconds;

    @Value("${idata.sql-session.max-sessions:1000}")
    private int maxSessions;

    public SqlSessionManager(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    /**
     * 在会话连接上执行回调。sessionId 为空时保持一次性连接行为（向后兼容）；
     * 非空时复用/重建会话连接，执行完毕不关闭连接。
     */
    public <T> T run(String sessionId, Long datasourceId, SqlConnectionCallback<T> callback) throws SQLException {
        return run(sessionId, datasourceId, null, callback);
    }

    /**
     * 在会话连接上执行回调。
     *
     * <p>会话按「数据源 + 执行引擎」绑定：切换数据源或引擎（HIVE/SPARK）时会自动重建连接，
     * 避免同一个会话在错误引擎上执行。sessionId 为空时保持一次性连接行为。
     */
    public <T> T run(String sessionId, Long datasourceId, String engine, SqlConnectionCallback<T> callback)
            throws SQLException {
        if (sessionId == null || sessionId.isBlank()) {
            try (Connection conn = datasourceService.getConnection(datasourceId, engine)) {
                return callback.doInConnection(conn);
            }
        }
        return runInSession(sessionId, datasourceId, engine, callback);
    }

    private <T> T runInSession(String sessionId, Long datasourceId, String engine, SqlConnectionCallback<T> callback)
            throws SQLException {
        Session session = sessions.get(sessionId);
        if (session == null) {
            if (sessions.size() >= maxSessions) {
                throw new IllegalStateException("会话数量已达上限(" + maxSessions + ")，请关闭不再使用的编辑器");
            }
            session = sessions.computeIfAbsent(sessionId, k -> new Session());
        }
        session.lock.lock();
        try {
            ensureConnection(session, datasourceId, engine);
            session.lastAccessTime = System.currentTimeMillis();
            return callback.doInConnection(session.connection);
        } catch (SQLException | RuntimeException e) {
            handleFailure(sessionId, session, e);
            throw e;
        } finally {
            session.lock.unlock();
        }
    }

    private void ensureConnection(Session session, Long datasourceId, String engine) throws SQLException {
        String normalizedEngine = engine == null || engine.isBlank() ? null : engine.trim().toUpperCase();
        boolean changed = session.connection != null
                && (!session.datasourceId.equals(datasourceId) || !java.util.Objects.equals(session.engine, normalizedEngine));
        if (changed) {
            closeQuietly(session.connection);
            session.connection = null;
        }
        if (session.connection == null) {
            session.connection = datasourceService.getConnection(datasourceId, engine);
            session.datasourceId = datasourceId;
            session.engine = normalizedEngine;
        }
    }

    private void handleFailure(String sessionId, Session session, Exception e) {
        boolean dead = false;
        try {
            if (session.connection == null || !session.connection.isValid(3)) {
                dead = true;
            }
        } catch (SQLException ignored) {
            dead = true;
        }
        if (dead) {
            // 数据源被删或连接已失效：移除会话，避免后续请求一直撞上死连接
            sessions.remove(sessionId, session);
            closeQuietly(session.connection);
            session.connection = null;
        }
    }

    /** 显式关闭会话连接，幂等。 */
    public void close(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Session session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.lock.lock();
        try {
            closeQuietly(session.connection);
            session.connection = null;
        } finally {
            session.lock.unlock();
        }
    }

    /** 定期清理空闲超时会话。正在执行的会话（锁被占用）跳过，下次再清理。 */
    @Scheduled(fixedDelayString = "${idata.sql-session.sweep-ms:60000}")
    public void sweep() {
        long now = System.currentTimeMillis();
        long timeoutMs = idleTimeoutSeconds * 1000;
        for (String sessionId : sessions.keySet()) {
            Session session = sessions.get(sessionId);
            if (session == null) {
                continue;
            }
            if (now - session.lastAccessTime <= timeoutMs) {
                continue;
            }
            if (session.lock.tryLock()) {
                try {
                    sessions.remove(sessionId, session);
                    closeQuietly(session.connection);
                    session.connection = null;
                } finally {
                    session.lock.unlock();
                }
            }
        }
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    public interface SqlConnectionCallback<T> {
        T doInConnection(Connection conn) throws SQLException;
    }

    private static class Session {
        Long datasourceId;
        /** 当前连接对应的执行引擎（HIVE / SPARK），引擎变化时重建连接 */
        String engine;
        Connection connection;
        final ReentrantLock lock = new ReentrantLock();
        volatile long lastAccessTime = System.currentTimeMillis();
    }
}
