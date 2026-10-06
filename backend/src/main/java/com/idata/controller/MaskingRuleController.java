package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.MaskingRuleRequest;
import com.idata.dto.MaskingRuleVO;
import com.idata.dto.PageResult;
import com.idata.service.datasource.MaskingRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/masking-rule")
public class MaskingRuleController {

    private final MaskingRuleService maskingRuleService;

    public MaskingRuleController(MaskingRuleService maskingRuleService) {
        this.maskingRuleService = maskingRuleService;
    }

    @GetMapping("/list")
    public Result<List<MaskingRuleVO>> list(@RequestParam(required = false) String keyword) {
        return Result.success(maskingRuleService.listAll(keyword));
    }

    @GetMapping("/page")
    public Result<PageResult<MaskingRuleVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(maskingRuleService.listPage(keyword, page, pageSize));
    }

    @GetMapping("/{id:\\d+}")
    public Result<MaskingRuleVO> getById(@PathVariable Long id) {
        return Result.success(maskingRuleService.getById(id));
    }

    @PostMapping("/create")
    public Result<MaskingRuleVO> create(@Valid @RequestBody MaskingRuleRequest req) {
        return Result.success(maskingRuleService.create(req));
    }

    @PutMapping("/update")
    public Result<MaskingRuleVO> update(@Valid @RequestBody MaskingRuleRequest req) {
        return Result.success(maskingRuleService.update(req));
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        maskingRuleService.delete(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        maskingRuleService.deleteBatch(ids);
        return Result.success();
    }
}
