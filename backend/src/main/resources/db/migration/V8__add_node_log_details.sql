ALTER TABLE node_execution_log
    ADD COLUMN datax_json TEXT DEFAULT NULL COMMENT 'DataX job JSON configuration' AFTER log_path,
    ADD COLUMN output_log LONGTEXT DEFAULT NULL COMMENT 'Execution stdout/stderr output' AFTER datax_json;
