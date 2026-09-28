package com.idata.service.report;

import com.idata.dto.ReportRequest;
import com.idata.dto.ReportVO;
import com.idata.dto.SqlExecuteResult;
import com.idata.entity.DatasourceConfig;
import com.idata.entity.Folder;
import com.idata.entity.Report;
import com.idata.mapper.DatasourceConfigMapper;
import com.idata.mapper.FolderMapper;
import com.idata.mapper.ReportMapper;
import com.idata.service.sql.ParameterService;
import com.idata.service.sql.SqlExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 报表服务：报表定义的 CRUD，以及运行时执行报表 SQL 取数。
 * 复用 SQL 执行器与参数管理（SQL 中的 ${参数} 会被替换）。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final ReportMapper reportMapper;
    private final DatasourceConfigMapper datasourceConfigMapper;
    private final SqlExecutorService sqlExecutorService;
    private final ParameterService parameterService;
    private final FolderMapper folderMapper;

    public ReportService(ReportMapper reportMapper,
                         DatasourceConfigMapper datasourceConfigMapper,
                         SqlExecutorService sqlExecutorService,
                         ParameterService parameterService,
                         FolderMapper folderMapper) {
        this.reportMapper = reportMapper;
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.sqlExecutorService = sqlExecutorService;
        this.parameterService = parameterService;
        this.folderMapper = folderMapper;
    }

    public List<ReportVO> listAll() {
        List<Report> reports = reportMapper.selectList(null);
        Map<Long, String> datasourceNameMap = datasourceConfigMapper.selectList(null).stream()
                .collect(Collectors.toMap(DatasourceConfig::getId, DatasourceConfig::getName, (a, b) -> a));
        return reports.stream()
                .map(r -> toVO(r, datasourceNameMap.get(r.getDatasourceId())))
                .collect(Collectors.toList());
    }

    public ReportVO getById(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("报表不存在: " + id);
        }
        DatasourceConfig ds = datasourceConfigMapper.selectById(report.getDatasourceId());
        return toVO(report, ds != null ? ds.getName() : null);
    }

    public ReportVO create(ReportRequest req) {
        requireDatasource(req.getDatasourceId());

        Report report = new Report();
        report.setName(req.getName());
        report.setDescription(req.getDescription());
        report.setDatasourceId(req.getDatasourceId());
        report.setSqlContent(req.getSqlContent());
        report.setChartType(req.getChartType() != null ? req.getChartType() : "TABLE");
        report.setChartConfig(req.getChartConfig() != null ? req.getChartConfig() : "{}");
        report.setRefreshInterval(req.getRefreshInterval() != null ? req.getRefreshInterval() : 0);
        report.setFolderId(validateFolder(req.getFolderId()));
        reportMapper.insert(report);

        DatasourceConfig ds = datasourceConfigMapper.selectById(report.getDatasourceId());
        return toVO(report, ds != null ? ds.getName() : null);
    }

    public ReportVO update(ReportRequest req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("报表ID不能为空");
        }
        Report report = reportMapper.selectById(req.getId());
        if (report == null) {
            throw new IllegalArgumentException("报表不存在: " + req.getId());
        }

        if (req.getDatasourceId() != null && !req.getDatasourceId().equals(report.getDatasourceId())) {
            requireDatasource(req.getDatasourceId());
            report.setDatasourceId(req.getDatasourceId());
        }
        if (req.getName() != null) {
            report.setName(req.getName());
        }
        if (req.getDescription() != null) {
            report.setDescription(req.getDescription());
        }
        if (req.getSqlContent() != null) {
            report.setSqlContent(req.getSqlContent());
        }
        if (req.getChartType() != null) {
            report.setChartType(req.getChartType());
        }
        if (req.getChartConfig() != null) {
            report.setChartConfig(req.getChartConfig());
        }
        if (req.getRefreshInterval() != null) {
            report.setRefreshInterval(req.getRefreshInterval());
        }
        if (req.getFolderId() != null) {
            report.setFolderId(validateFolder(req.getFolderId()));
        }
        reportMapper.updateById(report);

        DatasourceConfig ds = datasourceConfigMapper.selectById(report.getDatasourceId());
        return toVO(report, ds != null ? ds.getName() : null);
    }

    public void delete(Long id) {
        if (reportMapper.selectById(id) == null) {
            throw new IllegalArgumentException("报表不存在: " + id);
        }
        reportMapper.deleteById(id);
        log.info("Deleted report {}", id);
    }

    /**
     * 执行已保存报表的查询 SQL 并返回结果集。
     */
    public SqlExecuteResult run(Long id) {
        return run(id, null);
    }

    /**
     * 执行已保存报表的查询 SQL，支持传入参数覆盖（用于看板全局筛选联动）。
     * 优先取看板传入的值，未覆盖的占位符回落到参数管理中的配置。
     */
    public SqlExecuteResult run(Long id, Map<String, String> overrides) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("报表不存在: " + id);
        }
        return executeSql(report.getDatasourceId(), report.getSqlContent(), overrides);
    }

    /**
     * 编辑态预览：直接执行未保存的 SQL。
     */
    public SqlExecuteResult preview(Long datasourceId, String sql) {
        requireDatasource(datasourceId);
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        return executeSql(datasourceId, sql);
    }

    private SqlExecuteResult executeSql(Long datasourceId, String sql) {
        return executeSql(datasourceId, sql, null);
    }

    private SqlExecuteResult executeSql(Long datasourceId, String sql, Map<String, String> overrides) {
        String resolvedSql = parameterService.resolveParams(sql);
        resolvedSql = applyOverrides(resolvedSql, overrides);
        log.info("Running report SQL on datasource {}: {}", datasourceId, resolvedSql);
        SqlExecuteResult result = sqlExecutorService.execute(datasourceId, resolvedSql);
        if (result.getErrorMessage() != null) {
            log.warn("Report SQL failed on datasource {}: {}", datasourceId, result.getErrorMessage());
        }
        return result;
    }

    /**
     * 用传入的参数值替换 ${key} 占位符（仅替换 overrides 中存在的键）。
     */
    private String applyOverrides(String sql, Map<String, String> overrides) {
        if (sql == null || sql.isBlank() || overrides == null || overrides.isEmpty()) {
            return sql;
        }
        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER_PATTERN.matcher(sql);
        while (m.find()) {
            String value = overrides.get(m.group(1));
            if (value != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 移动报表到指定文件夹（folderId 为 null 表示移到未分组）。
     */
    public ReportVO move(Long id, Long folderId) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("报表不存在: " + id);
        }
        report.setFolderId(validateFolder(folderId));
        reportMapper.updateById(report);
        DatasourceConfig ds = datasourceConfigMapper.selectById(report.getDatasourceId());
        return toVO(report, ds != null ? ds.getName() : null);
    }

    private Long validateFolder(Long folderId) {
        if (folderId == null) {
            return null;
        }
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("文件夹不存在: " + folderId);
        }
        if (!"REPORT".equals(folder.getBizType())) {
            throw new IllegalArgumentException("该文件夹不是报表文件夹");
        }
        return folderId;
    }

    private void requireDatasource(Long datasourceId) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("请选择数据源");
        }
        if (datasourceConfigMapper.selectById(datasourceId) == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }
    }

    private ReportVO toVO(Report report, String datasourceName) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setName(report.getName());
        vo.setDescription(report.getDescription());
        vo.setDatasourceId(report.getDatasourceId());
        vo.setDatasourceName(datasourceName);
        vo.setSqlContent(report.getSqlContent());
        vo.setChartType(report.getChartType());
        vo.setChartConfig(report.getChartConfig());
        vo.setRefreshInterval(report.getRefreshInterval());
        vo.setFolderId(report.getFolderId());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }
}
