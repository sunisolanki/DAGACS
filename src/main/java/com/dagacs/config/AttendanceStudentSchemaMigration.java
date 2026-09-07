package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent schema migration for M3.4 (secure authenticated Student resolution).
 * <p>
 * Hibernate's {@code ddl-auto: update} can add a new column to an existing table
 * but does not reliably create a unique index with a stable name. This migration
 * guarantees the {@code students.email} column and its {@code uk_student_email}
 * unique index exist. It is guarded by {@code INFORMATION_SCHEMA}, idempotent,
 * non-destructive, and never seeds data. It does not touch any attendance table
 * or any frozen M3.2/M3.3 constraint.
 * </p>
 */
@Component
public class AttendanceStudentSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AttendanceStudentSchemaMigration.class);

    private static final String STUDENT_TABLE = "students";
    private static final String EMAIL_COLUMN = "email";
    private static final String EMAIL_INDEX = "uk_student_email";

    private final JdbcTemplate jdbcTemplate;

    public AttendanceStudentSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureStudentEmailColumn();
        ensureStudentEmailUniqueIndex();
    }

    private void ensureStudentEmailColumn() {
        if (columnExists(STUDENT_TABLE, EMAIL_COLUMN)) {
            log.info("Schema migration: '{}'.{} already exists; no change required.",
                    STUDENT_TABLE, EMAIL_COLUMN);
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + STUDENT_TABLE
                + " ADD COLUMN " + EMAIL_COLUMN + " VARCHAR(255) NULL");
        log.info("Schema migration: added '{}.{}.'.", STUDENT_TABLE, EMAIL_COLUMN);
    }

    private void ensureStudentEmailUniqueIndex() {
        if (uniqueIndexExists(STUDENT_TABLE, EMAIL_INDEX)) {
            log.info("Schema migration: unique index '{}' on '{}' already exists; no change required.",
                    EMAIL_INDEX, STUDENT_TABLE);
            return;
        }
        // The column must exist before the index can be created. If the column was
        // just added (or already existed from a prior run) this is safe.
        jdbcTemplate.execute("ALTER TABLE " + STUDENT_TABLE
                + " ADD CONSTRAINT " + EMAIL_INDEX + " UNIQUE (" + EMAIL_COLUMN + ")");
        log.info("Schema migration: added unique index '{}' on '{}.{}'.",
                EMAIL_INDEX, STUDENT_TABLE, EMAIL_COLUMN);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        if (!tableExists(tableName)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean uniqueIndexExists(String tableName, String indexName) {
        if (!tableExists(tableName)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? "
                        + "AND CONSTRAINT_TYPE = 'UNIQUE'",
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }
}
