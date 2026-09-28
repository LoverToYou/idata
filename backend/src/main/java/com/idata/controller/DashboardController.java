package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.DashboardDataVO;
import com.idata.dto.DashboardRequest;
import com.idata.dto.DashboardVO;
import com.idata.service.dashboard.DashboardService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/list")
    public Result<List<DashboardVO>> list() {
        return Result.success(dashboardService.listAll());
    }

    @GetMapping("/{id}")
    public Result<DashboardVO> getById(@PathVariable Long id) {
        return Result.success(dashboardService.getById(id));
    }

    @PostMapping("/create")
    public Result<DashboardVO> create(@Valid @RequestBody DashboardRequest request) {
        return Result.success(dashboardService.create(request));
    }

    @PutMapping("/update")
    public Result<DashboardVO> update(@Valid @RequestBody DashboardRequest request) {
        return Result.success(dashboardService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dashboardService.delete(id);
        return Result.success();
    }

    /** 大屏取数：一次性返回看板内所有卡片的数据 */
    @GetMapping("/{id}/data")
    public Result<DashboardDataVO> data(@PathVariable Long id) {
        return Result.success(dashboardService.getData(id));
    }

    /** 大屏取数（带全局筛选参数）：body: { params: { key: value } } */
    @PostMapping("/{id}/data")
    @SuppressWarnings("unchecked")
    public Result<DashboardDataVO> dataWithParams(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, String> params = null;
        if (body != null && body.get("params") instanceof Map<?, ?> raw) {
            params = new java.util.HashMap<>();
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) raw).entrySet()) {
                if (e.getValue() != null) {
                    params.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
        return Result.success(dashboardService.getData(id, params));
    }

    /** 移动看板到文件夹: body {folderId: 1} 或 {folderId: null} 表示未分组 */
    @PutMapping("/{id}/move")
    public Result<DashboardVO> move(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long folderId = body.get("folderId") != null ? Long.valueOf(body.get("folderId").toString()) : null;
        return Result.success(dashboardService.move(id, folderId));
    }

    /** 复制看板 */
    @PostMapping("/{id}/copy")
    public Result<DashboardVO> copy(@PathVariable Long id) {
        return Result.success(dashboardService.copy(id));
    }
}
