package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.PageResult;
import com.idata.dto.UdfDefinitionRequest;
import com.idata.dto.UdfDefinitionVO;
import com.idata.service.udf.UdfService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/udf")
public class UdfController {

    private final UdfService udfService;

    public UdfController(UdfService udfService) {
        this.udfService = udfService;
    }

    @PostMapping("/create")
    public Result<UdfDefinitionVO> create(@Valid @RequestBody UdfDefinitionRequest request) {
        return Result.success(udfService.create(request));
    }

    @GetMapping("/page")
    public Result<PageResult<UdfDefinitionVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String registerStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(udfService.page(keyword, datasourceId, registerStatus, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<UdfDefinitionVO> getById(@PathVariable Long id) {
        return Result.success(udfService.getById(id));
    }

    @PutMapping("/update")
    public Result<UdfDefinitionVO> update(@Valid @RequestBody UdfDefinitionRequest request) {
        return Result.success(udfService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        udfService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/register")
    public Result<UdfDefinitionVO> register(@PathVariable Long id) {
        return Result.success(udfService.register(id));
    }

    @PostMapping("/{id}/unregister")
    public Result<UdfDefinitionVO> unregister(@PathVariable Long id) {
        return Result.success(udfService.unregister(id));
    }

    @PostMapping("/{id}/verify")
    public Result<UdfDefinitionVO> verify(@PathVariable Long id) {
        return Result.success(udfService.verify(id));
    }

    @GetMapping("/by-datasource")
    public Result<List<UdfDefinitionVO>> byDatasource(@RequestParam Long datasourceId,
                                                      @RequestParam(required = false) String databaseName) {
        return Result.success(udfService.listByDatasource(datasourceId, databaseName));
    }
}
