package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.ConnectionTestRequest;
import com.idata.dto.DatasourceRequest;
import com.idata.dto.DatasourceVO;
import com.idata.service.datasource.DatasourceService;
import com.idata.service.datasource.HiveMetaService;
import com.idata.service.datasource.JdbcMetaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
public class DatasourceController {

    private final DatasourceService datasourceService;
    private final HiveMetaService hiveMetaService;
    private final JdbcMetaService jdbcMetaService;

    public DatasourceController(DatasourceService datasourceService,
                                HiveMetaService hiveMetaService,
                                JdbcMetaService jdbcMetaService) {
        this.datasourceService = datasourceService;
        this.hiveMetaService = hiveMetaService;
        this.jdbcMetaService = jdbcMetaService;
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

    // --- Generic JDBC metadata endpoints ---

    @GetMapping("/{id}/accessible-databases")
    public Result<List<String>> listAccessibleDatabases(@PathVariable Long id) {
        return Result.success(jdbcMetaService.listAccessibleDatabases(id));
    }

    @GetMapping("/{id}/databases")
    public Result<List<String>> listDatabases(@PathVariable Long id) {
        return Result.success(jdbcMetaService.listDatabases(id));
    }

    @GetMapping("/{id}/tables")
    public Result<List<Map<String, String>>> listTables(@PathVariable Long id,
                                                        @RequestParam(required = false) String database) {
        return Result.success(jdbcMetaService.listTables(id, database));
    }

    @GetMapping("/{id}/tables/{tableName}/columns")
    public Result<List<Map<String, String>>> listColumns(
            @PathVariable Long id,
            @PathVariable String tableName,
            @RequestParam(required = false) String database) {
        return Result.success(jdbcMetaService.listColumns(id, tableName, database));
    }

    @GetMapping("/{id}/tables/{tableName}/ddl")
    public Result<String> getTableDdl(
            @PathVariable Long id,
            @PathVariable String tableName,
            @RequestParam(required = false) String database) {
        return Result.success(jdbcMetaService.getTableDdl(id, tableName, database));
    }

    @GetMapping("/{id}/tables/{tableName}/comment")
    public Result<String> getTableComment(
            @PathVariable Long id,
            @PathVariable String tableName,
            @RequestParam(required = false) String database) {
        return Result.success(jdbcMetaService.getTableComment(id, tableName, database));
    }
}
