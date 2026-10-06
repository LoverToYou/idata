package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.ReportRequest;
import com.idata.dto.ReportVO;
import com.idata.dto.SqlExecuteResult;
import com.idata.service.report.ReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/list")
    public Result<List<ReportVO>> list() {
        return Result.success(reportService.listAll());
    }

    @GetMapping("/{id:\\d+}")
    public Result<ReportVO> getById(@PathVariable Long id) {
        return Result.success(reportService.getById(id));
    }

    @PostMapping("/create")
    public Result<ReportVO> create(@Valid @RequestBody ReportRequest request) {
        return Result.success(reportService.create(request));
    }

    @PutMapping("/update")
    public Result<ReportVO> update(@Valid @RequestBody ReportRequest request) {
        return Result.success(reportService.update(request));
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return Result.success();
    }

    /** 移动报表到文件夹: body {folderId: 1} 或 {folderId: null} 表示未分组 */
    @PutMapping("/{id}/move")
    public Result<ReportVO> move(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long folderId = body.get("folderId") != null ? Long.valueOf(body.get("folderId").toString()) : null;
        return Result.success(reportService.move(id, folderId));
    }

    /** 运行报表：执行已保存 SQL 并返回结果集 */
    @PostMapping("/{id}/run")
    public Result<SqlExecuteResult> run(@PathVariable Long id) {
        SqlExecuteResult result = reportService.run(id);
        if (result.getErrorMessage() != null) {
            return Result.error(500, result.getErrorMessage());
        }
        return Result.success(result);
    }

    /** 编辑态预览：执行未保存的 SQL */
    @PostMapping("/preview")
    public Result<SqlExecuteResult> preview(@RequestBody Map<String, Object> body) {
        Long datasourceId = body.get("datasourceId") != null
                ? Long.valueOf(body.get("datasourceId").toString()) : null;
        String sql = body.get("sql") != null ? body.get("sql").toString() : null;
        SqlExecuteResult result = reportService.preview(datasourceId, sql);
        if (result.getErrorMessage() != null) {
            return Result.error(500, result.getErrorMessage());
        }
        return Result.success(result);
    }
}
