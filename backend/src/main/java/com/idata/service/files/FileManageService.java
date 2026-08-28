package com.idata.service.files;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idata.dto.FileManageVO;
import com.idata.dto.PageResult;
import com.idata.entity.FileManage;
import com.idata.entity.UdfDefinition;
import com.idata.mapper.FileManageMapper;
import com.idata.mapper.UdfDefinitionMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileManageService {

    private final FileManageMapper fileManageMapper;
    private final HdfsStorageService hdfsStorageService;
    private final UdfDefinitionMapper udfDefinitionMapper;

    public FileManageService(FileManageMapper fileManageMapper,
                             HdfsStorageService hdfsStorageService,
                             UdfDefinitionMapper udfDefinitionMapper) {
        this.fileManageMapper = fileManageMapper;
        this.hdfsStorageService = hdfsStorageService;
        this.udfDefinitionMapper = udfDefinitionMapper;
    }

    /** 字节数组写入 HDFS 并登记（UDF 打包产物等） */
    public FileManageVO uploadBytes(byte[] data, String fileName, String description) {
        String uri;
        try {
            uri = hdfsStorageService.uploadBytes(data, fileName);
        } catch (IOException e) {
            throw new RuntimeException("上传到 HDFS 失败: " + e.getMessage());
        }
        FileManage fm = new FileManage();
        fm.setFileName(fileName);
        fm.setFilePath(uri);
        fm.setFileSize((long) data.length);
        fm.setFileExt(extOf(fileName));
        fm.setDescription(description);
        fileManageMapper.insert(fm);
        return toVO(fileManageMapper.selectById(fm.getId()));
    }

    public FileManageVO upload(MultipartFile file, String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String uri;
        try {
            uri = hdfsStorageService.upload(file);
        } catch (IOException e) {
            throw new RuntimeException("上传到 HDFS 失败: " + e.getMessage());
        }
        FileManage fm = new FileManage();
        fm.setFileName(file.getOriginalFilename());
        fm.setFilePath(uri);
        fm.setFileSize(file.getSize());
        fm.setFileExt(extOf(file.getOriginalFilename()));
        fm.setDescription(description);
        fileManageMapper.insert(fm);
        return toVO(fileManageMapper.selectById(fm.getId()));
    }

    public PageResult<FileManageVO> page(String keyword, String fileExt, int pageNum, int pageSize) {
        LambdaQueryWrapper<FileManage> wrapper = new LambdaQueryWrapper<FileManage>()
                .orderByDesc(FileManage::getUpdatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(FileManage::getFileName, keyword)
                    .or().like(FileManage::getFilePath, keyword));
        }
        if (StringUtils.isNotBlank(fileExt)) {
            wrapper.eq(FileManage::getFileExt, fileExt.trim().toLowerCase());
        }
        Page<FileManage> page = new Page<>(pageNum, pageSize);
        Page<FileManage> result = fileManageMapper.selectPage(page, wrapper);

        PageResult<FileManageVO> pr = new PageResult<>();
        pr.setData(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        pr.setTotal(result.getTotal());
        pr.setPage((int) result.getCurrent());
        pr.setPageSize((int) result.getSize());
        return pr;
    }

    /** 供 UDF 选择 jar：只返回扩展名为 jar 的文件 */
    public List<FileManageVO> listJars(String keyword) {
        LambdaQueryWrapper<FileManage> wrapper = new LambdaQueryWrapper<FileManage>()
                .eq(FileManage::getFileExt, "jar")
                .orderByDesc(FileManage::getUpdatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(FileManage::getFileName, keyword.trim());
        }
        return fileManageMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public FileManageVO getById(Long id) {
        return toVO(getEntity(id));
    }

    /** 下载结果：原始文件名 + 可读流 */
    public record DownloadItem(String fileName, InputStream stream) {
    }

    public DownloadItem download(Long id) {
        FileManage fm = getEntity(id);
        try {
            return new DownloadItem(fm.getFileName(), hdfsStorageService.download(fm.getFilePath()));
        } catch (java.io.FileNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("下载文件失败: " + e.getMessage());
        }
    }

    /** 被 UDF 引用时拒绝删除，防止悬空引用 */
    public void delete(Long id) {
        FileManage fm = getEntity(id);
        Long refCount = udfDefinitionMapper.selectCount(
                new LambdaQueryWrapper<UdfDefinition>().eq(UdfDefinition::getJarPath, fm.getFilePath()));
        if (refCount != null && refCount > 0) {
            throw new IllegalArgumentException("该文件已被 " + refCount + " 个 UDF 引用，请先删除相关 UDF");
        }
        try {
            hdfsStorageService.delete(fm.getFilePath());
        } catch (IOException e) {
            throw new RuntimeException("删除 HDFS 文件失败: " + e.getMessage());
        }
        fileManageMapper.deleteById(id);
    }

    /** 供 UdfService 按 fileId 取文件实体 */
    public FileManage getEntity(Long id) {
        FileManage fm = fileManageMapper.selectById(id);
        if (fm == null) {
            throw new IllegalArgumentException("文件不存在: " + id);
        }
        return fm;
    }

    private FileManageVO toVO(FileManage fm) {
        FileManageVO vo = new FileManageVO();
        vo.setId(fm.getId());
        vo.setFileName(fm.getFileName());
        vo.setFilePath(fm.getFilePath());
        vo.setFileSize(fm.getFileSize());
        vo.setFileExt(fm.getFileExt());
        vo.setDescription(fm.getDescription());
        vo.setCreatedAt(fm.getCreatedAt());
        vo.setUpdatedAt(fm.getUpdatedAt());
        return vo;
    }

    private String extOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
