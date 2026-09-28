package com.idata.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idata.dto.*;
import com.idata.entity.Dashboard;
import com.idata.entity.DashboardItem;
import com.idata.entity.Folder;
import com.idata.entity.Report;
import com.idata.mapper.DashboardItemMapper;
import com.idata.mapper.DashboardMapper;
import com.idata.mapper.FolderMapper;
import com.idata.mapper.ReportMapper;
import com.idata.service.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 看板服务：看板与卡片布局的 CRUD，以及大屏一次性取数。
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final int GRID_COLS = 12;

    private final DashboardMapper dashboardMapper;
    private final DashboardItemMapper dashboardItemMapper;
    private final ReportMapper reportMapper;
    private final ReportService reportService;
    private final FolderMapper folderMapper;

    public DashboardService(DashboardMapper dashboardMapper,
                            DashboardItemMapper dashboardItemMapper,
                            ReportMapper reportMapper,
                            ReportService reportService,
                            FolderMapper folderMapper) {
        this.dashboardMapper = dashboardMapper;
        this.dashboardItemMapper = dashboardItemMapper;
        this.reportMapper = reportMapper;
        this.reportService = reportService;
        this.folderMapper = folderMapper;
    }

    public List<DashboardVO> listAll() {
        List<Dashboard> dashboards = dashboardMapper.selectList(null);
        if (dashboards.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = dashboards.stream().map(Dashboard::getId).collect(Collectors.toList());
        List<DashboardItem> allItems = dashboardItemMapper.selectList(
                new LambdaQueryWrapper<DashboardItem>().in(DashboardItem::getDashboardId, ids));
        Map<Long, List<DashboardItem>> itemsByDashboard = allItems.stream()
                .collect(Collectors.groupingBy(DashboardItem::getDashboardId));

        return dashboards.stream()
                .map(d -> toVO(d, itemsByDashboard.getOrDefault(d.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    public DashboardVO getById(Long id) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new IllegalArgumentException("看板不存在: " + id);
        }
        return toVO(dashboard, loadItems(id));
    }

    public DashboardVO create(DashboardRequest req) {
        Dashboard dashboard = new Dashboard();
        dashboard.setName(req.getName());
        dashboard.setDescription(req.getDescription());
        dashboard.setRefreshInterval(req.getRefreshInterval() != null ? req.getRefreshInterval() : 60);
        dashboard.setFilters(req.getFilters());
        dashboard.setFitScale(req.getFitScale() != null ? req.getFitScale() : true);
        dashboard.setFolderId(validateFolder(req.getFolderId()));
        dashboardMapper.insert(dashboard);
        saveItems(dashboard.getId(), req.getItems());
        return getById(dashboard.getId());
    }

    public DashboardVO update(DashboardRequest req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("看板ID不能为空");
        }
        Dashboard dashboard = dashboardMapper.selectById(req.getId());
        if (dashboard == null) {
            throw new IllegalArgumentException("看板不存在: " + req.getId());
        }
        if (req.getName() != null) {
            dashboard.setName(req.getName());
        }
        if (req.getDescription() != null) {
            dashboard.setDescription(req.getDescription());
        }
        if (req.getRefreshInterval() != null) {
            dashboard.setRefreshInterval(req.getRefreshInterval());
        }
        if (req.getFilters() != null) {
            dashboard.setFilters(req.getFilters());
        }
        if (req.getFitScale() != null) {
            dashboard.setFitScale(req.getFitScale());
        }
        if (req.getFolderId() != null) {
            dashboard.setFolderId(validateFolder(req.getFolderId()));
        }
        dashboardMapper.updateById(dashboard);

        if (req.getItems() != null) {
            dashboardItemMapper.delete(new LambdaQueryWrapper<DashboardItem>()
                    .eq(DashboardItem::getDashboardId, dashboard.getId()));
            saveItems(dashboard.getId(), req.getItems());
        }
        return getById(dashboard.getId());
    }

    public void delete(Long id) {
        if (dashboardMapper.selectById(id) == null) {
            throw new IllegalArgumentException("看板不存在: " + id);
        }
        dashboardItemMapper.delete(new LambdaQueryWrapper<DashboardItem>()
                .eq(DashboardItem::getDashboardId, id));
        dashboardMapper.deleteById(id);
        log.info("Deleted dashboard {}", id);
    }

    /**
     * 大屏取数：执行看板内所有卡片对应报表的 SQL。
     * 支持传入全局筛选参数（覆盖 SQL 中的 ${参数} 占位）。
     * 单张卡片失败不影响其他卡片，错误记录在该卡片的 errorMessage 中。
     */
    public DashboardDataVO getData(Long id) {
        return getData(id, null);
    }

    public DashboardDataVO getData(Long id, Map<String, String> params) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new IllegalArgumentException("看板不存在: " + id);
        }
        List<DashboardItem> items = loadItems(id);
        DashboardDataVO data = new DashboardDataVO();
        List<DashboardDataVO.CardData> cards = new ArrayList<>();

        for (DashboardItem item : items) {
            DashboardDataVO.CardData card = new DashboardDataVO.CardData();
            card.setReportId(item.getReportId());
            Report report = reportMapper.selectById(item.getReportId());
            if (report == null) {
                card.setTitle(item.getTitle());
                card.setChartType("TABLE");
                card.setColumns(Collections.emptyList());
                card.setRows(Collections.emptyList());
                card.setErrorMessage("报表不存在: " + item.getReportId());
                cards.add(card);
                continue;
            }

            card.setTitle(item.getTitle() != null && !item.getTitle().isBlank() ? item.getTitle() : report.getName());
            card.setChartType(report.getChartType());
            card.setChartConfig(report.getChartConfig());

            try {
                SqlExecuteResult result = reportService.run(report.getId(), params);
                card.setColumns(result.getColumns() != null ? result.getColumns() : Collections.emptyList());
                card.setRows(result.getRows() != null ? result.getRows() : Collections.emptyList());
                card.setElapsedMs(result.getElapsedMs());
                card.setErrorMessage(result.getErrorMessage());
            } catch (Exception e) {
                log.warn("Dashboard {} card report {} failed: {}", id, item.getReportId(), e.getMessage());
                card.setColumns(Collections.emptyList());
                card.setRows(Collections.emptyList());
                card.setErrorMessage(e.getMessage());
            }
            cards.add(card);
        }

        data.setCards(cards);
        return data;
    }

    /**
     * 复制看板（含卡片布局与筛选配置）。
     */
    public DashboardVO copy(Long id) {
        Dashboard source = dashboardMapper.selectById(id);
        if (source == null) {
            throw new IllegalArgumentException("看板不存在: " + id);
        }
        Dashboard target = new Dashboard();
        target.setName(source.getName() + " - 副本");
        target.setDescription(source.getDescription());
        target.setRefreshInterval(source.getRefreshInterval());
        target.setFilters(source.getFilters());
        target.setFitScale(source.getFitScale());
        target.setFolderId(source.getFolderId());
        dashboardMapper.insert(target);

        for (DashboardItem item : loadItems(id)) {
            DashboardItem copyItem = new DashboardItem();
            copyItem.setDashboardId(target.getId());
            copyItem.setReportId(item.getReportId());
            copyItem.setTitle(item.getTitle());
            copyItem.setPosX(item.getPosX());
            copyItem.setPosY(item.getPosY());
            copyItem.setWidth(item.getWidth());
            copyItem.setHeight(item.getHeight());
            copyItem.setAlertConfig(item.getAlertConfig());
            dashboardItemMapper.insert(copyItem);
        }
        log.info("Copied dashboard {} -> {}", id, target.getId());
        return getById(target.getId());
    }

    /**
     * 移动看板到指定文件夹（folderId 为 null 表示移到未分组）。
     */
    public DashboardVO move(Long id, Long folderId) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new IllegalArgumentException("看板不存在: " + id);
        }
        dashboard.setFolderId(validateFolder(folderId));
        dashboardMapper.updateById(dashboard);
        return getById(id);
    }

    private Long validateFolder(Long folderId) {
        if (folderId == null) {
            return null;
        }
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("文件夹不存在: " + folderId);
        }
        if (!"DASHBOARD".equals(folder.getBizType())) {
            throw new IllegalArgumentException("该文件夹不是看板文件夹");
        }
        return folderId;
    }

    private List<DashboardItem> loadItems(Long dashboardId) {
        return dashboardItemMapper.selectList(new LambdaQueryWrapper<DashboardItem>()
                .eq(DashboardItem::getDashboardId, dashboardId)
                .orderByAsc(DashboardItem::getPosY)
                .orderByAsc(DashboardItem::getPosX));
    }

    private void saveItems(Long dashboardId, List<DashboardItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DashboardItemRequest req : items) {
            if (req.getReportId() == null || reportMapper.selectById(req.getReportId()) == null) {
                throw new IllegalArgumentException("报表不存在: " + req.getReportId());
            }
            DashboardItem item = new DashboardItem();
            item.setDashboardId(dashboardId);
            item.setReportId(req.getReportId());
            item.setTitle(req.getTitle());
            item.setPosX(clamp(req.getPosX(), 0, 0, GRID_COLS - 1));
            item.setPosY(clamp(req.getPosY(), 0, 0, 10000));
            item.setWidth(clamp(req.getWidth(), 4, 1, GRID_COLS));
            item.setHeight(clamp(req.getHeight(), 5, 1, 200));
            item.setAlertConfig(req.getAlertConfig());
            dashboardItemMapper.insert(item);
        }
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int v = value != null ? value : defaultValue;
        return Math.max(min, Math.min(max, v));
    }

    private DashboardVO toVO(Dashboard dashboard, List<DashboardItem> items) {
        DashboardVO vo = new DashboardVO();
        vo.setId(dashboard.getId());
        vo.setName(dashboard.getName());
        vo.setDescription(dashboard.getDescription());
        vo.setRefreshInterval(dashboard.getRefreshInterval());
        vo.setFilters(dashboard.getFilters());
        vo.setFitScale(dashboard.getFitScale());
        vo.setFolderId(dashboard.getFolderId());
        vo.setCreatedAt(dashboard.getCreatedAt());
        vo.setUpdatedAt(dashboard.getUpdatedAt());

        List<DashboardItemVO> itemVOs = new ArrayList<>();
        for (DashboardItem item : items) {
            DashboardItemVO itemVO = new DashboardItemVO();
            itemVO.setId(item.getId());
            itemVO.setReportId(item.getReportId());
            itemVO.setTitle(item.getTitle());
            itemVO.setPosX(item.getPosX());
            itemVO.setPosY(item.getPosY());
            itemVO.setWidth(item.getWidth());
            itemVO.setHeight(item.getHeight());
            itemVO.setAlertConfig(item.getAlertConfig());
            Report report = reportMapper.selectById(item.getReportId());
            if (report != null) {
                itemVO.setReportName(report.getName());
                itemVO.setChartType(report.getChartType());
                itemVO.setChartConfig(report.getChartConfig());
            }
            itemVOs.add(itemVO);
        }
        vo.setItems(itemVOs);
        return vo;
    }
}
