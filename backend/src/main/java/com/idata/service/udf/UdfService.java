package com.idata.service.udf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idata.dto.DatasourceVO;
import com.idata.dto.PageResult;
import com.idata.dto.UdfDefinitionRequest;
import com.idata.dto.UdfDefinitionVO;
import com.idata.entity.DatasourceConfig;
import com.idata.entity.FileManage;
import com.idata.entity.UdfDefinition;
import com.idata.mapper.UdfDefinitionMapper;
import com.idata.service.datasource.DatasourceService;
import com.idata.service.files.FileManageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UdfService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern CLASS_NAME = Pattern.compile("^[a-zA-Z0-9_$.]+$");

    private final UdfDefinitionMapper udfDefinitionMapper;
    private final UdfRegisterService udfRegisterService;
    private final DatasourceService datasourceService;
    private final FileManageService fileManageService;

    public UdfService(UdfDefinitionMapper udfDefinitionMapper,
                      UdfRegisterService udfRegisterService,
                      DatasourceService datasourceService,
                      FileManageService fileManageService) {
        this.udfDefinitionMapper = udfDefinitionMapper;
        this.udfRegisterService = udfRegisterService;
        this.datasourceService = datasourceService;
        this.fileManageService = fileManageService;
    }

    // ---------- 创建 / 查询 ----------

    public UdfDefinitionVO create(UdfDefinitionRequest req) {
        validate(req);
        ensureHiveDatasource(req.getDatasourceId());
        UdfDefinition udf = new UdfDefinition();
        applyRequest(udf, req);
        applyJarSource(udf, req);
        udf.setRegisterStatus("UNREGISTERED");
        udfDefinitionMapper.insert(udf);
        return toVO(udfDefinitionMapper.selectById(udf.getId()));
    }

    public PageResult<UdfDefinitionVO> page(String keyword, Long datasourceId, String registerStatus,
                                            int pageNum, int pageSize) {
        LambdaQueryWrapper<UdfDefinition> wrapper = new LambdaQueryWrapper<UdfDefinition>()
                .orderByDesc(UdfDefinition::getUpdatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(UdfDefinition::getName, keyword)
                    .or().like(UdfDefinition::getClassName, keyword)
                    .or().like(UdfDefinition::getJarFileName, keyword));
        }
        if (datasourceId != null) {
            wrapper.eq(UdfDefinition::getDatasourceId, datasourceId);
        }
        if (StringUtils.isNotBlank(registerStatus)) {
            wrapper.eq(UdfDefinition::getRegisterStatus, registerStatus);
        }
        Page<UdfDefinition> page = new Page<>(pageNum, pageSize);
        Page<UdfDefinition> result = udfDefinitionMapper.selectPage(page, wrapper);
        Map<Long, String> dsNames = buildDatasourceNameMap(result.getRecords());

        PageResult<UdfDefinitionVO> pr = new PageResult<>();
        pr.setData(result.getRecords().stream()
                .map(u -> toVO(u, dsNames.get(u.getDatasourceId())))
                .collect(Collectors.toList()));
        pr.setTotal(result.getTotal());
        pr.setPage((int) result.getCurrent());
        pr.setPageSize((int) result.getSize());
        return pr;
    }

    public UdfDefinitionVO getById(Long id) {
        return toVO(getEntity(id));
    }

    /** 供 SQL 编辑器按数据源拉取 UDF 列表 */
    public List<UdfDefinitionVO> listByDatasource(Long datasourceId, String databaseName) {
        ensureHiveDatasource(datasourceId);
        LambdaQueryWrapper<UdfDefinition> wrapper = new LambdaQueryWrapper<UdfDefinition>()
                .eq(UdfDefinition::getDatasourceId, datasourceId)
                .orderByAsc(UdfDefinition::getDatabaseName)
                .orderByAsc(UdfDefinition::getName);
        if (StringUtils.isNotBlank(databaseName)) {
            wrapper.eq(UdfDefinition::getDatabaseName, databaseName);
        }
        List<UdfDefinition> list = udfDefinitionMapper.selectList(wrapper);
        Map<Long, String> dsNames = buildDatasourceNameMap(list);
        return list.stream()
                .map(u -> toVO(u, dsNames.get(u.getDatasourceId())))
                .collect(Collectors.toList());
    }

    // ---------- 更新 / 删除 ----------

    public UdfDefinitionVO update(UdfDefinitionRequest req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        validate(req);
        ensureHiveDatasource(req.getDatasourceId());
        UdfDefinition udf = getEntity(req.getId());
        if (StringUtils.isNotBlank(req.getJarPath())) {
            applyJarPath(udf, req.getJarPath());
        } else if (req.getFileId() != null) {
            applyJar(udf, req.getFileId());
        }
        applyRequest(udf, req);
        // 内容变更后需重新注册
        udf.setRegisterStatus("UNREGISTERED");
        udf.setRegisterMessage(null);
        udf.setRegisterSql(null);
        udfDefinitionMapper.updateById(udf);
        return toVO(udfDefinitionMapper.selectById(udf.getId()));
    }

    /** 已注册时先注销（Hive 不可达则拒绝删除）。jar 文件归文件管理模块管理，此处不删除 */
    public void delete(Long id) {
        UdfDefinition udf = getEntity(id);
        if ("REGISTERED".equals(udf.getRegisterStatus())) {
            try {
                udfRegisterService.unregister(udf);
            } catch (Exception e) {
                throw new IllegalArgumentException("函数仍处于已注册状态且注销失败，请先确认 Hive 可访问: " + e.getMessage());
            }
        }
        udfDefinitionMapper.deleteById(id);
    }

    // ---------- 注册相关 ----------

    /** 注册为永久函数；无论成败都返回 VO（状态与消息内聚在 VO 中） */
    public UdfDefinitionVO register(Long id) {
        UdfDefinition udf = getEntity(id);
        String createSql = udfRegisterService.buildCreateSql(udf);
        try {
            udfRegisterService.register(udf);
            udf.setRegisterStatus("REGISTERED");
            udf.setRegisterMessage("注册成功");
        } catch (Exception e) {
            udf.setRegisterStatus("FAILED");
            udf.setRegisterMessage(truncate(e.getMessage(), 2000));
        }
        udf.setRegisterSql(createSql);
        udfDefinitionMapper.updateById(udf);
        return toVO(udfDefinitionMapper.selectById(udf.getId()));
    }

    public UdfDefinitionVO unregister(Long id) {
        UdfDefinition udf = getEntity(id);
        try {
            udfRegisterService.unregister(udf);
            udf.setRegisterStatus("UNREGISTERED");
            udf.setRegisterMessage("已注销");
        } catch (Exception e) {
            udf.setRegisterStatus("FAILED");
            udf.setRegisterMessage("注销失败: " + truncate(e.getMessage(), 2000));
        }
        udf.setRegisterSql(null);
        udfDefinitionMapper.updateById(udf);
        return toVO(udfDefinitionMapper.selectById(udf.getId()));
    }

    /** 用 DESCRIBE FUNCTION 校准本地状态与 Hive metastore 的一致性 */
    public UdfDefinitionVO verify(Long id) {
        UdfDefinition udf = getEntity(id);
        try {
            boolean exists = udfRegisterService.exists(udf);
            udf.setRegisterStatus(exists ? "REGISTERED" : "UNREGISTERED");
            udf.setRegisterMessage(exists ? "函数存在于 Hive metastore" : "函数不存在于 Hive metastore");
            udfDefinitionMapper.updateById(udf);
        } catch (Exception e) {
            udf.setRegisterStatus("FAILED");
            udf.setRegisterMessage("校验失败: " + truncate(e.getMessage(), 2000));
            udfDefinitionMapper.updateById(udf);
        }
        return toVO(udfDefinitionMapper.selectById(udf.getId()));
    }

    // ---------- 内部工具 ----------

    private UdfDefinition getEntity(Long id) {
        UdfDefinition udf = udfDefinitionMapper.selectById(id);
        if (udf == null) {
            throw new IllegalArgumentException("UDF 不存在: " + id);
        }
        return udf;
    }

    private void validate(UdfDefinitionRequest req) {
        if (!IDENTIFIER.matcher(req.getName()).matches()) {
            throw new IllegalArgumentException("函数名只能包含字母、数字、下划线，且以字母或下划线开头");
        }
        String db = normalizeDatabase(req.getDatabaseName());
        if (!IDENTIFIER.matcher(db).matches()) {
            throw new IllegalArgumentException("数据库名不合法: " + db);
        }
        if (!CLASS_NAME.matcher(req.getClassName()).matches()) {
            throw new IllegalArgumentException("实现类名不合法: " + req.getClassName());
        }
    }

    private void ensureHiveDatasource(Long datasourceId) {
        DatasourceConfig ds = datasourceService.getEntityById(datasourceId);
        if (!"HIVE".equalsIgnoreCase(ds.getType())) {
            throw new IllegalArgumentException("UDF 仅支持 Hive 数据源");
        }
    }

    /** jar 入参：优先用户手动填的 HDFS 路径，回退文件管理记录 */
    private void applyJarSource(UdfDefinition udf, UdfDefinitionRequest req) {
        if (StringUtils.isNotBlank(req.getJarPath())) {
            applyJarPath(udf, req.getJarPath());
            return;
        }
        applyJar(udf, req.getFileId());
    }

    /** 用户手动指定 JAR 的 HDFS 路径（如 hdfs://127.0.0.1:9000/data/x.jar），不再依赖文件管理模块 */
    private void applyJarPath(UdfDefinition udf, String jarPath) {
        String p = jarPath.trim();
        if (!p.startsWith("hdfs://") && !p.startsWith("file://")) {
            throw new IllegalArgumentException("JAR 路径必须以 hdfs:// 或 file:// 开头");
        }
        if (!p.toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException("JAR 路径必须以 .jar 结尾");
        }
        udf.setJarPath(p);
        int slash = p.lastIndexOf('/');
        udf.setJarFileName(slash >= 0 ? p.substring(slash + 1) : p);
        udf.setJarSize(null);
    }

    /** 从文件管理记录取 jar（校验扩展名），jarPath 即 HDFS 完整 URI */
    private void applyJar(UdfDefinition udf, Long fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("请先选择 JAR 文件");
        }
        FileManage fm = fileManageService.getEntity(fileId);
        if (!"jar".equals(fm.getFileExt())) {
            throw new IllegalArgumentException("所选文件不是 JAR 文件: " + fm.getFileName());
        }
        udf.setJarPath(fm.getFilePath());
        udf.setJarFileName(fm.getFileName());
        udf.setJarSize(fm.getFileSize());
    }

    private void applyRequest(UdfDefinition udf, UdfDefinitionRequest req) {
        udf.setName(req.getName().trim());
        udf.setClassName(req.getClassName().trim());
        udf.setDatabaseName(normalizeDatabase(req.getDatabaseName()));
        udf.setDatasourceId(req.getDatasourceId());
        udf.setFunctionType(req.getFunctionType() == null || req.getFunctionType().isBlank()
                ? "UDF" : req.getFunctionType());
        udf.setDescription(req.getDescription());
    }

    private String normalizeDatabase(String db) {
        return (db == null || db.isBlank()) ? "default" : db.trim();
    }

    private Map<Long, String> buildDatasourceNameMap(List<UdfDefinition> udfs) {
        List<Long> ids = udfs.stream()
                .map(UdfDefinition::getDatasourceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return datasourceService.listAll().stream()
                .filter(ds -> ids.contains(ds.getId()))
                .collect(Collectors.toMap(DatasourceVO::getId, DatasourceVO::getName));
    }

    private UdfDefinitionVO toVO(UdfDefinition udf) {
        return toVO(udf, null);
    }

    private UdfDefinitionVO toVO(UdfDefinition udf, String datasourceName) {
        UdfDefinitionVO vo = new UdfDefinitionVO();
        vo.setId(udf.getId());
        vo.setName(udf.getName());
        vo.setClassName(udf.getClassName());
        vo.setJarFileName(udf.getJarFileName());
        vo.setJarPath(udf.getJarPath());
        vo.setJarSize(udf.getJarSize());
        vo.setDatabaseName(udf.getDatabaseName());
        vo.setDatasourceId(udf.getDatasourceId());
        vo.setDatasourceName(datasourceName);
        vo.setFunctionType(udf.getFunctionType());
        vo.setDescription(udf.getDescription());
        vo.setRegisterStatus(udf.getRegisterStatus());
        vo.setRegisterMessage(udf.getRegisterMessage());
        vo.setRegisterSql(udf.getRegisterSql());
        vo.setCreatedAt(udf.getCreatedAt());
        vo.setUpdatedAt(udf.getUpdatedAt());
        return vo;
    }

    private String truncate(String msg, int max) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= max ? msg : msg.substring(0, max);
    }
}
