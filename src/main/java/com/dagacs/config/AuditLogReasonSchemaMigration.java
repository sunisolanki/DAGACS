package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent schema migration for {@code attendance_audit_logs.reason}.
 * <p>
 * Hibernate's {@code ddl-auto: update} cannot relax an existing NOT NULL column.
 * This migration ensures {@code reason} is nullable, matching the corrected SOW
 * specification where reason is optional.
 * </p>
 * <p>
 * On a fresh database Hibernate creates the column as nullable per the entity
 * mapping. On an existing database where the column is NOT NULL, this migration
 * relaxes it. No row data is touched.
 * </p>
 */
@Component
public class AuditLogReasonSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditLogReasonSchemaMigration.class);

    private static final String TABLE = "attendance_audit_logs";
    private static final String COLUMN = "reason";

    private final JdbcTemplate jdbcTemplate;

    public AuditLogReasonSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(TABLE)) {
            log.info("Schema migration: table '{}' not present yet; skipping.", TABLE);
            return;
        }

        String nullability = columnNullability(TABLE, COLUMN);
        if (nullability == null) {
            log.info("Schema migration: column '{}.{}' not found; nothing to do.", TABLE, COLUMN);
            return;
        }

        if ("YES".equalsIgnoreCase(nullability)) {
            log.info("Schema migration: '{}.{}' is already nullable; no change required.", TABLE, COLUMN);
            return;
        }

        jdbcTemplate.execute("ALTER TABLE " + TABLE + " MODIFY COLUMN " + COLUMN + " VARCHAR(255) NULL");
        log.info("Schema migration: applied ALTER making '{}.{}' nullable.", TABLE, COLUMN);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private String columnNullability(String tableName, String columnName) {
        return jdbcTemplate.query(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                rs -> rs.next() ? rs.getString("IS_NULLABLE") : null,
                tableName, columnName);
    }
}
