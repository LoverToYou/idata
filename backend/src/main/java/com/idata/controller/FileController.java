package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.FileManageVO;
import com.idata.dto.PageResult;
import com.idata.service.files.FileManageService;
import com.idata.service.files.HdfsStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileManageService fileManageService;
    private final HdfsStorageService hdfsStorageService;

    public FileController(FileManageService fileManageService, HdfsStorageService hdfsStorageService) {
        this.fileManageService = fileManageService;
        this.hdfsStorageService = hdfsStorageService;
    }

    /** 列出 HDFS 文件管理存储目录下实际存在的 jar 文件（含非经文件管理上传的），供 UDF 表单选择 */
    @GetMapping("/hdfs-jars")
    public Result<List<FileManageVO>> hdfsJars() {
        try {
            return Result.success(hdfsStorageService.listJars());
        } catch (Exception e) {
            throw new RuntimeException("列出 HDFS jar 失败: " + e.getMessage(), e);
        }
    }

    @PostMapping("/upload")
    public Result<FileManageVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String description) {
        return Result.success(fileManageService.upload(file, description));
    }

    @GetMapping("/page")
    public Result<PageResult<FileManageVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fileExt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(fileManageService.page(keyword, fileExt, page, pageSize));
    }

    @GetMapping("/jars")
    public Result<List<FileManageVO>> jars(@RequestParam(required = false) String keyword) {
        return Result.success(fileManageService.listJars(keyword));
    }

    @GetMapping("/{id}")
    public Result<FileManageVO> getById(@PathVariable Long id) {
        return Result.success(fileManageService.getById(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        FileManageService.DownloadItem item = fileManageService.download(id);
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(item.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(item.stream()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileManageService.delete(id);
        return Result.success();
    }
}
