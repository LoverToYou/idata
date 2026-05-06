package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.ConnectionTestRequest;
import com.idata.dto.DatasourceRequest;
import com.idata.dto.DatasourceVO;
import com.idata.service.datasource.DatasourceService;
import com.idata.service.datasource.HiveMetaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
public class DatasourceController {

    private final DatasourceService datasourceService;
    private final HiveMetaService hiveMetaService;

    public DatasourceController(DatasourceService datasourceService, HiveMetaService hiveMetaService) {
        this.datasourceService = datasourceService;
        this.hiveMetaService = hiveMetaService;
    }

    @GetMapping("/list")
    public Result<List<DatasourceVO>> list() {
        return Result.success(datasourceService.listAll());
    }

    @GetMapping("/{id}")
    public Result<DatasourceVO> getById(@PathVariable Long id) {
        return Result.success(datasourceService.getById(id));
    }

    @PostMapping("/create")
    public Result<DatasourceVO> create(@Valid @RequestBody DatasourceRequest request) {
        return Result.success(datasourceService.create(request));
    }

    @PutMapping("/update")
    public Result<DatasourceVO> update(@Valid @RequestBody DatasourceRequest request) {
        return Result.success(datasourceService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return Result.success();
    }

    @PostMapping("/test-connection")
    public Result<Boolean> testConnection(@Valid @RequestBody ConnectionTestRequest request) {
        boolean ok = datasourceService.testConnection(request);
        return Result.success(ok);
    }

    @PostMapping("/test-connection/{id}")
    public Result<Boolean> testConnectionById(@PathVariable Long id) {
        boolean ok = datasourceService.testConnectionById(id);
        return Result.success(ok);
    }

    // --- Hive metadata endpoints ---

    @GetMapping("/{id}/hive/databases")
    public Result<List<String>> hiveDatabases(@PathVariable Long id) {
        return Result.success(hiveMetaService.listDatabases(id));
    }

    @GetMapping("/{id}/hive/{database}/tables")
    public Result<List<String>> hiveTables(@PathVariable Long id, @PathVariable String database) {
        return Result.success(hiveMetaService.listTables(id, database));
    }

    @GetMapping("/{id}/hive/{database}/tables/{tableName}/schema")
    public Result<List<Map<String, String>>> hiveTableSchema(
            @PathVariable Long id, @PathVariable String database, @PathVariable String tableName) {
        return Result.success(hiveMetaService.describeTable(id, database, tableName));
    }

    @GetMapping("/{id}/hive/{database}/tables/{tableName}/partitions")
    public Result<List<Map<String, String>>> hivePartitions(
            @PathVariable Long id, @PathVariable String database, @PathVariable String tableName) {
        return Result.success(hiveMetaService.listPartitions(id, database, tableName));
    }
}
