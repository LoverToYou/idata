package com.idata.service.datasource;

import com.idata.dto.ConnectionTestRequest;
import com.idata.dto.DatasourceRequest;
import com.idata.dto.DatasourceVO;
import com.idata.entity.DatasourceConfig;
import com.idata.mapper.DatasourceConfigMapper;
import com.idata.utils.PasswordEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    public List<DatasourceVO> listAll() {
        return datasourceConfigMapper.selectList(null)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
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
        DatasourceConfig config = toEntity(request);
        config.setPassword(passwordEncryptor.encrypt(request.getPassword()));
        datasourceConfigMapper.insert(config);
        return toVO(config);
    }

    public DatasourceVO update(DatasourceRequest request) {
        DatasourceConfig config = datasourceConfigMapper.selectById(request.getId());
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + request.getId());
        }
        config.setName(request.getName());
        config.setType(request.getType());
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setDatabaseName(request.getDatabaseName());
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

    public boolean testConnection(ConnectionTestRequest request) {
        String url = buildJdbcUrl(request.getType(), request.getHost(),
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

    private String buildJdbcUrl(String type, String host, Integer port, String databaseName) {
        if ("MYSQL".equalsIgnoreCase(type)) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                    host, port, databaseName != null ? databaseName : "");
        } else if ("HIVE".equalsIgnoreCase(type)) {
            return String.format("jdbc:hive2://%s:%d/%s",
                    host, port, databaseName != null ? databaseName : "default");
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + type);
    }

    public Connection getConnection(Long datasourceId) throws SQLException {
        DatasourceConfig config = getEntityById(datasourceId);
        String url = buildJdbcUrl(config.getType(), config.getHost(),
                config.getPort(), config.getDatabaseName());
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        props.setProperty("password", config.getPassword());
        return DriverManager.getConnection(url, props);
    }

    public Connection getConnection(ConnectionTestRequest request) throws SQLException {
        String url = buildJdbcUrl(request.getType(), request.getHost(),
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
