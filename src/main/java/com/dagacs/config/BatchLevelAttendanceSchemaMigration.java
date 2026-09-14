package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Phase-2 repository-tracked, idempotent schema migration for batch-level
 * (section-less) teaching and attendance.
 *
 * <p>Introduces the batch-mode domain on top of the Section-based model without
 * touching historical data: every NOT NULL column previously scoped to a
 * Section is relaxed to NULL (new batch-mode rows leave it unset) and nullable
 * {@code batch_*} / {@code batch_id} columns are added. Legacy rows are never
 * rewritten and never guessed. The single Section-mode academic identity
 * (SubjectOffering) and the existing unique constraints are preserved.</p>
 *
 * <h3>Guarantees</h3>
 * <ul>
 *   <li>Re-runnable: every step guards on INFORMATION_SCHEMA before any DDL.</li>
 *   <li>Data-preserving: no row is updated, deleted or remapped here; a
 *       batch-mode assignment/session/record is only ever created by the
 *       application services, never migrated from Section-mode data.</li>
 *   <li>New unique constraints ({@code uk_assignment_batch},
 *       {@code uk_session_class_batch}) are installed only when no duplicate
 *       keys exist, mirroring the M9.3 hardening stance.</li>
 *   <li>The XOR invariant {@code exactly one of {section, batch}} is enforced
 *       at the service boundary; no DB CHECK constraint is added because its
 *       availability varies across MySQL versions.</li>
 * </ul>
 */
@Component
public class BatchLevelAttendanceSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchLevelAttendanceSchemaMigration.class);

    static final String STUDENTS = "students";
    static final String ASSIGNMENTS = "teacher_subject_section_assignments";
    static final String SESSIONS = "attendance_sessions";
    static final String RECORDS = "attendance_records";
    static final String AUDIT_LOGS = "attendance_audit_logs";

    static final String CONSTRAINTS_ASSIGNMENT_BATCH = "uk_assignment_batch";
    static final String CONSTRAINTS_SESSION_BATCH = "uk_session_class_batch";

    private final JdbcTemplate jdbcTemplate;

    public BatchLevelAttendanceSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureNullable(STUDENTS, "section_id");
        ensureNullable(ASSIGNMENTS, "section_id");
        ensureAddNullable(ASSIGNMENTS, "batch_id");
        ensureNullable(SESSIONS, "section_code");
        ensureAddNullable(SESSIONS, "batch_code");
        ensureNullable(RECORDS, "section_id");
        ensureAddNullable(RECORDS, "batch_id");
        ensureAddNullable(AUDIT_LOGS, "batch", "VARCHAR(255)");
        ensureAddNullable(AUDIT_LOGS, "batch_name", "VARCHAR(255)");

        ensureAssignmentBatchConstraint();
        ensureSessionBatchConstraint();
        log.info("Batch-level attendance migration: schema review complete.");
    }

    /**
     * Relaxes a NOT NULL column to NULL on existing deployments so that new
     * batch-mode rows may leave it unset. Guarded on nullability; no-op on a
     * fresh schema where Hibernate already creates the column as nullable.
     */
    private void ensureNullable(String table, String column) {
        if (!tableExists(table) || !columnExists(table, column)) {
            return;
        }
        String nullability = columnNullability(table, column);
        if (nullability != null && !"NO".equalsIgnoreCase(nullability)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " BIGINT NULL");
        log.info("Batch-level attendance migration: relaxed '{}.{}' to NULL.", table, column);
    }

    /**
     * Adds a nullable BIGINT column when missing (fresh create is handled by
     * Hibernate; legacy deployments get it here).
     */
    private void ensureAddNullable(String table, String column) {
        if (!tableExists(table)) {
            return;
        }
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " BIGINT NULL");
        log.info("Batch-level attendance migration: added nullable column '{}.{}'.", table, column);
    }

    /**
     * Adds nullable VARCHAR snapshot columns for the audit log (batch rows
     * cannot be represented in the NOT NULL Section snapshots).
     */
    private void ensureAddNullable(String table, String column, String type) {
        if (!tableExists(table)) {
            return;
        }
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + " NULL");
        log.info("Batch-level attendance migration: added nullable column '{}.{}'.", table, column);
    }

    /**
     * Installs {@code uk_assignment_batch (teacher_id, subject_offering_id,
     * batch_id)} only when no duplicate triples exist.
     */
    private void ensureAssignmentBatchConstraint() {
        if (!tableExists(ASSIGNMENTS) || !columnExists(ASSIGNMENTS, "batch_id")) {
            return;
        }
        long duplicates = duplicates(ASSIGNMENTS,
                "teacher_id, subject_offering_id, batch_id", "batch_id IS NOT NULL");
        if (duplicates > 0) {
            log.warn("Batch-level attendance migration: {} duplicate (teacher, offering, batch) "
                    + "triples; {} not enforced.", duplicates, CONSTRAINTS_ASSIGNMENT_BATCH);
            return;
        }
        ensureConstraint(ASSIGNMENTS, CONSTRAINTS_ASSIGNMENT_BATCH,
                "UNIQUE (teacher_id, subject_offering_id, batch_id)");
    }

    /**
     * Installs {@code uk_session_class_batch (subject_code, batch_code, date,
     * lecture_period)} only when no duplicate keys exist.
     */
    private void ensureSessionBatchConstraint() {
        if (!tableExists(SESSIONS) || !columnExists(SESSIONS, "batch_code")) {
            return;
        }
        long duplicates = duplicates(SESSIONS,
                "subject_code, batch_code, date, lecture_period", "batch_code IS NOT NULL");
        if (duplicates > 0) {
            log.warn("Batch-level attendance migration: {} duplicate (subject, batch, date, period) "
                    + "keys; {} not enforced.", duplicates, CONSTRAINTS_SESSION_BATCH);
            return;
        }
        ensureConstraint(SESSIONS, CONSTRAINTS_SESSION_BATCH,
                "UNIQUE (subject_code, batch_code, date, lecture_period)");
    }

    private long duplicates(String table, String columns, String where) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT " + columns + " FROM " + table
                        + " WHERE " + where
                        + " GROUP BY " + columns + " HAVING COUNT(*) > 1) dup",
                Long.class);
    }

    private void ensureConstraint(String table, String name, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
                Integer.class, table, name);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " " + definition);
            log.info("Batch-level attendance migration: added constraint '{}' on '{}'.", name, table);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
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