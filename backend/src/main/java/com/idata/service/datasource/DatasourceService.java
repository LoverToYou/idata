package com.idata.service.datasource;

import com.idata.dto.ConnectionTestRequest;
import com.idata.dto.DatasourceRequest;
import com.idata.dto.DatasourceVO;
import com.idata.entity.DatasourceConfig;
import com.idata.mapper.DatasourceConfigMapper;
import com.idata.utils.PasswordEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idata.dto.PageResult;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class DatasourceService {

    private final DatasourceConfigMapper datasourceConfigMapper;
    private final PasswordEncryptor passwordEncryptor;

    public DatasourceService(DatasourceConfigMapper datasourceConfigMapper,
                             @Value("${idata.encryption.key}") String encryptionKey) {
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.passwordEncryptor = new PasswordEncryptor(encryptionKey);
    }

    public List<DatasourceVO> listAll(String keyword) {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(DatasourceConfig::getName, keyword);
        }
        wrapper.orderByDesc(DatasourceConfig::getUpdatedAt);
        return datasourceConfigMapper.selectList(wrapper)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public List<DatasourceVO> listAll() {
        return listAll(null);
    }

    public PageResult<DatasourceVO> listPage(String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<DatasourceConfig>()
                .orderByDesc(DatasourceConfig::getUpdatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(DatasourceConfig::getName, keyword);
        }
        Page<DatasourceConfig> page = new Page<>(pageNum, pageSize);
        Page<DatasourceConfig> result = datasourceConfigMapper.selectPage(page, wrapper);
        List<DatasourceVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        PageResult<DatasourceVO> pr = new PageResult<>();
        pr.setData(list);
        pr.setTotal(result.getTotal());
        pr.setPage((int) result.getCurrent());
        pr.setPageSize((int) result.getSize());
        return pr;
    }

    public DatasourceVO getById(Long id) {
        DatasourceConfig config = datasourceConfigMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        return toVO(config);
    }

    public DatasourceConfig getEntityById(Long id) {
        DatasourceConfig config = datasourceConfigMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        decryptPassword(config);
        return config;
    }

    public DatasourceVO create(DatasourceRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        validateAddress(request.getType(), request.getJdbcUrl(), request.getHost(), request.getPort());
        DatasourceConfig config = toEntity(request);
        config.setType(normalizeType(request.getType(), request.getJdbcUrl()));
        config.setPassword(passwordEncryptor.encrypt(request.getPassword()));
        datasourceConfigMapper.insert(config);
        return toVO(config);
    }

    public DatasourceVO update(DatasourceRequest request) {
        DatasourceConfig config = datasourceConfigMapper.selectById(request.getId());
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + request.getId());
        }
        validateAddress(request.getType(), request.getJdbcUrl(), request.getHost(), request.getPort());
        config.setName(request.getName());
        config.setType(normalizeType(request.getType(), request.getJdbcUrl()));
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setDatabaseName(request.getDatabaseName());
        config.setJdbcUrl(request.getJdbcUrl());
        config.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            config.setPassword(passwordEncryptor.encrypt(request.getPassword()));
        }
        config.setProps(request.getProps());
        datasourceConfigMapper.updateById(config);
        return toVO(datasourceConfigMapper.selectById(request.getId()));
    }

    public void delete(Long id) {
        if (datasourceConfigMapper.selectById(id) == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        datasourceConfigMapper.deleteById(id);
    }

    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        datasourceConfigMapper.deleteByIds(ids);
    }

    public boolean testConnection(ConnectionTestRequest request) {
        validateAddress(request.getType(), request.getJdbcUrl(), request.getHost(), request.getPort());
        String url = resolveUrl(request.getType(), request.getJdbcUrl(), request.getHost(),
                request.getPort(), request.getDatabaseName());
        Properties props = new Properties();
        if (request.getUsername() != null) {
            props.setProperty("user", request.getUsername());
        }
        if (request.getPassword() != null) {
            props.setProperty("password", request.getPassword());
        }

        try (Connection conn = DriverManager.getConnection(url, props)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            throw new RuntimeException("连接失败: " + e.getMessage());
        }
    }

    public boolean testConnectionById(Long id) {
        DatasourceConfig config = getEntityById(id);
        return testConnection(toTestRequest(config));
    }

    /**
     * JDBC URL 优先；未填写时根据类型用 host/port/databaseName 拼装。
     */
    private String resolveUrl(String type, String jdbcUrl, String host, Integer port, String databaseName) {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl.trim();
        }
        return buildJdbcUrl(normalizeType(type, jdbcUrl), host, port, databaseName);
    }

    /**
     * 类型归一化：类型为空时按 JDBC URL 前缀推断（jdbc:mysql -> MYSQL，jdbc:hive2 -> HIVE）。
     */
    private String normalizeType(String type, String jdbcUrl) {
        if (type != null && !type.isBlank()) {
            return type.toUpperCase();
        }
        if (jdbcUrl != null) {
            String u = jdbcUrl.toLowerCase();
            if (u.startsWith("jdbc:mysql")) return "MYSQL";
            if (u.startsWith("jdbc:hive2")) return "HIVE";
        }
        return type;
    }

    /**
     * 地址校验：JDBC URL 与「主机+端口」至少提供一种。
     */
    private void validateAddress(String type, String jdbcUrl, String host, Integer port) {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return;
        }
        if (host == null || host.isBlank() || port == null) {
            throw new IllegalArgumentException("请填写 JDBC URL，或完整填写主机地址与端口");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("请选择数据源类型");
        }
    }

    private String buildJdbcUrl(String type, String host, Integer port, String databaseName) {
        if ("MYSQL".equalsIgnoreCase(type)) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                    host, port, databaseName != null ? databaseName : "");
        } else if ("HIVE".equalsIgnoreCase(type)) {
            return String.format("jdbc:hive2://%s:%d/%s;auth=noSasl",
                    host, port, databaseName != null ? databaseName : "default");
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + type);
    }

    public Connection getConnection(Long datasourceId) throws SQLException {
        DatasourceConfig config = getEntityById(datasourceId);
        String url = resolveUrl(config.getType(), config.getJdbcUrl(), config.getHost(),
                config.getPort(), config.getDatabaseName());
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        props.setProperty("password", config.getPassword());
        return DriverManager.getConnection(url, props);
    }

    public Connection getConnection(ConnectionTestRequest request) throws SQLException {
        String url = resolveUrl(request.getType(), request.getJdbcUrl(), request.getHost(),
                request.getPort(), request.getDatabaseName());
        Properties props = new Properties();
        if (request.getUsername() != null) props.setProperty("user", request.getUsername());
        if (request.getPassword() != null) props.setProperty("password", request.getPassword());
        return DriverManager.getConnection(url, props);
    }

    private ConnectionTestRequest toTestRequest(DatasourceConfig config) {
        ConnectionTestRequest req = new ConnectionTestRequest();
        req.setType(config.getType());
        req.setHost(config.getHost());
        req.setPort(config.getPort());
        req.setDatabaseName(config.getDatabaseName());
        req.setJdbcUrl(config.getJdbcUrl());
        req.setUsername(config.getUsername());
        req.setPassword(config.getPassword());
        return req;
    }

    private DatasourceConfig toEntity(DatasourceRequest request) {
        DatasourceConfig config = new DatasourceConfig();
        config.setName(request.getName());
        config.setType(request.getType());
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setDatabaseName(request.getDatabaseName());
        config.setJdbcUrl(request.getJdbcUrl());
        config.setUsername(request.getUsername());
        config.setProps(request.getProps());
        return config;
    }

    private DatasourceVO toVO(DatasourceConfig config) {
        DatasourceVO vo = new DatasourceVO();
        vo.setId(config.getId());
        vo.setName(config.getName());
        vo.setType(config.getType());
        vo.setHost(config.getHost());
        vo.setPort(config.getPort());
        vo.setDatabaseName(config.getDatabaseName());
        vo.setJdbcUrl(config.getJdbcUrl());
        vo.setUsername(config.getUsername());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    private void decryptPassword(DatasourceConfig config) {
        try {
            config.setPassword(passwordEncryptor.decrypt(config.getPassword()));
        } catch (Exception e) {
            // if decryption fails, keep the raw password (might be already plaintext)
        }
    }
}
