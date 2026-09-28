package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.FolderRequest;
import com.idata.dto.FolderVO;
import com.idata.service.folder.FolderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folder")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping("/list")
    public Result<List<FolderVO>> list(@RequestParam String bizType) {
        return Result.success(folderService.list(bizType));
    }

    @PostMapping("/create")
    public Result<FolderVO> create(@Valid @RequestBody FolderRequest request) {
        return Result.success(folderService.create(request));
    }

    @PutMapping("/update")
    public Result<FolderVO> update(@Valid @RequestBody FolderRequest request) {
        return Result.success(folderService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return Result.success();
    }
}
