package com.idata.service.folder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idata.dto.FolderRequest;
import com.idata.dto.FolderVO;
import com.idata.entity.Dashboard;
import com.idata.entity.Folder;
import com.idata.entity.Report;
import com.idata.mapper.DashboardMapper;
import com.idata.mapper.FolderMapper;
import com.idata.mapper.ReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件夹服务：报表 / 数据看板的分组管理。
 * 删除文件夹时不会删除内容，而是把子文件夹和其中的报表/看板移动到上级目录。
 */
@Service
public class FolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderService.class);
    private static final Set<String> BIZ_TYPES = Set.of("REPORT", "DASHBOARD");

    private final FolderMapper folderMapper;
    private final ReportMapper reportMapper;
    private final DashboardMapper dashboardMapper;

    public FolderService(FolderMapper folderMapper, ReportMapper reportMapper, DashboardMapper dashboardMapper) {
        this.folderMapper = folderMapper;
        this.reportMapper = reportMapper;
        this.dashboardMapper = dashboardMapper;
    }

    public List<FolderVO> list(String bizType) {
        requireBizType(bizType);
        return folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getBizType, bizType)
                        .orderByAsc(Folder::getSortOrder)
                        .orderByAsc(Folder::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public FolderVO create(FolderRequest req) {
        requireBizType(req.getBizType());
        checkNameConflict(req.getBizType(), req.getParentId(), req.getName(), null);
        Folder folder = new Folder();
        folder.setName(req.getName());
        folder.setParentId(req.getParentId());
        folder.setBizType(req.getBizType());
        folder.setSortOrder(0);
        folderMapper.insert(folder);
        return toVO(folder);
    }

    public FolderVO update(FolderRequest req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("文件夹ID不能为空");
        }
        Folder folder = folderMapper.selectById(req.getId());
        if (folder == null) {
            throw new IllegalArgumentException("文件夹不存在: " + req.getId());
        }
        String name = req.getName() != null ? req.getName() : folder.getName();
        Long parentId = req.getParentId();
        if (parentId != null) {
            if (parentId.equals(folder.getId())) {
                throw new IllegalArgumentException("不能把文件夹移动到自身");
            }
            Folder parent = folderMapper.selectById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父文件夹不存在: " + parentId);
            }
            if (!parent.getBizType().equals(folder.getBizType())) {
                throw new IllegalArgumentException("不能跨业务移动文件夹");
            }
            if (isDescendant(folder.getId(), parentId)) {
                throw new IllegalArgumentException("不能把文件夹移动到它的子文件夹中");
            }
        }
        checkNameConflict(folder.getBizType(), parentId, name, folder.getId());

        folder.setName(name);
        folder.setParentId(parentId);
        folderMapper.updateById(folder);
        return toVO(folder);
    }

    /**
     * 删除文件夹：子文件夹与其中的报表/看板移动到上级目录（不删除内容）。
     */
    public void delete(Long id) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new IllegalArgumentException("文件夹不存在: " + id);
        }
        Long parentId = folder.getParentId();

        folderMapper.update(null, new LambdaUpdateWrapper<Folder>()
                .eq(Folder::getParentId, id)
                .set(Folder::getParentId, parentId));

        if ("REPORT".equals(folder.getBizType())) {
            reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                    .eq(Report::getFolderId, id)
                    .set(Report::getFolderId, parentId));
        } else {
            dashboardMapper.update(null, new LambdaUpdateWrapper<Dashboard>()
                    .eq(Dashboard::getFolderId, id)
                    .set(Dashboard::getFolderId, parentId));
        }
        folderMapper.deleteById(id);
        log.info("Deleted folder {} ({}), contents moved to parent {}", id, folder.getBizType(), parentId);
    }

    /** 收集某文件夹及其所有子孙文件夹 ID（含自身） */
    public List<Long> descendantIds(String bizType, Long folderId) {
        requireBizType(bizType);
        List<Folder> all = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getBizType, bizType));
        List<Long> result = new java.util.ArrayList<>();
        collect(all, folderId, result);
        return result;
    }

    private void collect(List<Folder> all, Long current, List<Long> acc) {
        if (current == null || acc.contains(current)) return;
        acc.add(current);
        for (Folder f : all) {
            if (current.equals(f.getParentId())) {
                collect(all, f.getId(), acc);
            }
        }
    }

    private boolean isDescendant(Long folderId, Long candidateChildId) {
        List<Folder> all = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getBizType, folderMapper.selectById(folderId).getBizType()));
        List<Long> ids = new java.util.ArrayList<>();
        collect(all, folderId, ids);
        return ids.contains(candidateChildId);
    }

    private void checkNameConflict(String bizType, Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<Folder> wrapper = new LambdaQueryWrapper<Folder>()
                .eq(Folder::getBizType, bizType)
                .eq(Folder::getName, name);
        if (parentId == null) {
            wrapper.isNull(Folder::getParentId);
        } else {
            wrapper.eq(Folder::getParentId, parentId);
        }
        folderMapper.selectList(wrapper).stream()
                .filter(f -> excludeId == null || !f.getId().equals(excludeId))
                .findFirst()
                .ifPresent(f -> {
                    throw new IllegalArgumentException("同级目录下已存在同名文件夹: " + name);
                });
    }

    private void requireBizType(String bizType) {
        if (bizType == null || !BIZ_TYPES.contains(bizType)) {
            throw new IllegalArgumentException("业务类型只能是 REPORT 或 DASHBOARD");
        }
    }

    private FolderVO toVO(Folder folder) {
        FolderVO vo = new FolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setParentId(folder.getParentId());
        vo.setBizType(folder.getBizType());
        vo.setSortOrder(folder.getSortOrder());
        vo.setCreatedAt(folder.getCreatedAt());
        vo.setUpdatedAt(folder.getUpdatedAt());
        return vo;
    }
}
