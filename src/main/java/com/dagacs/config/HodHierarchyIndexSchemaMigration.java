package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent index migration for the M12 HOD academic-hierarchy queries.
 *
 * <p>Only indexes that a <em>measured, specific</em> Phase 2 query needs are
 * added. The rest of the hierarchy traversal is already served by existing
 * foreign-key and unique-constraint indexes (verified against
 * {@code information_schema.STATISTICS}, not assumed):</p>
 * <ul>
 *   <li>{@code academic_sessions.program_id}, {@code semesters.academic_session_id},
 *       {@code batches.academic_session_id} (leading column of
 *       {@code uk_batch_session_name}), {@code sections.batch_id} (leading
 *       column of {@code uk_section_batch_name}) — all already present.</li>
 *   <li>{@code subject_offerings.semester_id} — already present.</li>
 *   <li>{@code teacher_subject_section_assignments.section_id} and
 *       {@code .subject_offering_id} — already present.</li>
 * </ul>
 *
 * <p>The one genuine gap is {@code students}: the scoped student list and the
 * scoped section aggregate filter on {@code academic_session_id},
 * {@code semester_id} and {@code section_id} together. Four separate
 * single-column foreign-key indexes exist, but MySQL will drive the query from
 * only one of them and filter the rest row by row; a single composite index
 * removes that work. It is a pure index addition — no column, constraint or row
 * is altered, so no data is touched.</p>
 *
 * <p><b>Guarantees:</b> re-runnable (every step guards on
 * {@code INFORMATION_SCHEMA.STATISTICS} before any DDL), data-preserving (DDL
 * only), and it never drops or redefines an existing index.</p>
 */
@Component
public class HodHierarchyIndexSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HodHierarchyIndexSchemaMigration.class);

    private static final String STUDENTS = "students";
    private static final String IDX_STUDENTS_ACADEMIC_CONTEXT = "idx_students_academic_context";
    private static final String[] IDX_STUDENTS_COLUMNS = {
            "academic_session_id", "semester_id", "section_id"
    };

    private final JdbcTemplate jdbcTemplate;

    public HodHierarchyIndexSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureCompositeIndex(STUDENTS, IDX_STUDENTS_ACADEMIC_CONTEXT, IDX_STUDENTS_COLUMNS);
        log.info("HOD hierarchy index migration: schema review complete.");
    }

    private void ensureCompositeIndex(String table, String indexName, String... columns) {
        if (!tableExists(table)) {
            return;
        }
        if (indexExists(table, indexName)) {
            return;
        }
        // Every indexed column must exist, otherwise the DDL would fail on a
        // partially-migrated deployment; skip rather than break startup.
        for (String column : columns) {
            if (!columnExists(table, column)) {
                log.warn("HOD hierarchy index migration: column '{}.{}' is missing; "
                        + "skipping index '{}'.", table, column, indexName);
                return;
            }
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + indexName
                + " (" + String.join(", ", columns) + ")");
        log.info("HOD hierarchy index migration: added index '{}' on '{}({})'.",
                indexName, table, String.join(", ", columns));
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

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }
}
