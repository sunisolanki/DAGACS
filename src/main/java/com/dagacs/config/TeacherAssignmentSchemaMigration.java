package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * M9.3 repository-tracked, idempotent schema + data migration for
 * {@code teacher_subject_section_assignments}.
 *
 * <p>The pre-M9.3 table carried a {@code subject_id} column referencing the
 * subject directly and no subject-offering identity. M9.3 moves the canonical
 * subject identity to {@code subject_offering_id}. The legacy {@code subject_id}
 * column is intentionally PRESERVED (deprecated compatibility column); nothing
 * drops or destructively rewrites it.</p>
 *
 * <h3>Guarantees</h3>
 * <ul>
 *   <li>Re-runnable: every step guards on INFORMATION_SCHEMA or a
 *       {@code subject_offering_id IS NULL} predicate, so a second run (or a run
 *       against an already-migrated/fresh schema) performs no redundant DDL/DML.</li>
* <li>Data-preserving: unmapped legacy rows are never deleted and never
 *       guessed. A legacy row maps only when it resolves to EXACTLY ONE offering
 *       (same subject AND same AcademicSession via Section -&gt; Batch); ambiguous
 *       or unmatched rows stay NULL and are logged, never silently guessed.</li>
 *   <li>Compatibility: the legacy {@code subject_id} column is relaxed to NULL
 *       (never dropped) so new INSERTs that omit it comply with strict MySQL.</li>
 *   <li>Schema hardening is gated on data: {@code NOT NULL} and the
 *       {@code uk_assignment} unique constraint are only enforced when every
 *       legacy row was mapped and migrations produced no duplicates.</li>
 * </ul>
 */
@Component
public class TeacherAssignmentSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeacherAssignmentSchemaMigration.class);

    static final String TABLE = "teacher_subject_section_assignments";
    static final String OFFERING_COLUMN = "subject_offering_id";
    static final String LEGACY_SUBJECT_COLUMN = "subject_id";
    static final String UNIQUE_CONSTRAINT = "uk_assignment";

    static final String SQL_UNMATCHED_COUNT =
            "SELECT COUNT(*) FROM " + TABLE + " WHERE " + OFFERING_COLUMN + " IS NULL";
    static final String SQL_DUPLICATE_COUNT =
            "SELECT COUNT(*) FROM ("
                    + "SELECT teacher_id, " + OFFERING_COLUMN + ", section_id "
                    + "FROM " + TABLE + " WHERE " + OFFERING_COLUMN + " IS NOT NULL "
                    + "GROUP BY teacher_id, " + OFFERING_COLUMN + ", section_id "
                    + "HAVING COUNT(*) > 1) dup";

    private final JdbcTemplate jdbcTemplate;

    public TeacherAssignmentSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(TABLE)) {
            log.info("Teacher-assignment migration: table '{}' not present yet; skipping.", TABLE);
            return;
        }

        // 1. Ensure the canonical column exists (nullable) so legacy rows can be backfilled.
        if (!columnExists(TABLE, OFFERING_COLUMN)) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN "
                    + OFFERING_COLUMN + " BIGINT NULL");
            log.info("Teacher-assignment migration: added nullable column '{}.{}'.",
                    TABLE, OFFERING_COLUMN);
        }

        // 2. Relax the deprecated legacy subject_id column to NULL so that new
        //    INSERTs (which no longer map subject_id) comply. The column itself is
        //    preserved (deprecated compatibility column) and never dropped.
        if (columnExists(TABLE, LEGACY_SUBJECT_COLUMN)) {
            ensureLegacySubjectColumnNullable();
        }

        // 3. Backfill legacy rows only when the legacy subject_id column exists
        //    (it does not on a fresh schema). Each row maps to exactly one offering
        //    (same subject, same AcademicSession via Section -> Batch). Ambiguity or
        //    no match leaves the row NULL; nothing is silently guessed.
        if (columnExists(TABLE, LEGACY_SUBJECT_COLUMN)) {
            int mapped = backfill();
            log.info("Teacher-assignment migration: backfilled {} legacy row(s) from {}.",
                    mapped, LEGACY_SUBJECT_COLUMN);
        }

        long unmatched = jdbcTemplate.queryForObject(SQL_UNMATCHED_COUNT, Long.class);
        log.info("Teacher-assignment migration: {} row(s) without a subject_offering_id remain.",
                unmatched);

        long duplicates = 0L;
        if (columnExists(TABLE, LEGACY_SUBJECT_COLUMN)) {
            duplicates = jdbcTemplate.queryForObject(SQL_DUPLICATE_COUNT, Long.class);
            log.info("Teacher-assignment migration: {} duplicate (teacher, offering, section) "
                    + "triples detected.", duplicates);
        }

        if (unmatched == 0 && duplicates == 0) {
            ensureOfferingNotNull();
            ensureUniqueConstraint();
            log.info("Teacher-assignment migration: schema hardened — {} enforced NOT NULL and "
                    + "{} added (no unmapped rows, no duplicates).",
                    OFFERING_COLUMN, UNIQUE_CONSTRAINT);
        } else {
            if (unmatched > 0) {
                log.warn("Teacher-assignment migration: {} row(s) have no subject_offering_id "
                        + "({} not enforced). These rows will NOT authorize any teacher access.",
                        unmatched, OFFERING_COLUMN);
            }
            if (duplicates > 0) {
                log.warn("Teacher-assignment migration: {} duplicate (teacher, offering, section) "
                        + "triples were preserved; {} NOT enforced to avoid a failed ALTER.",
                        duplicates, UNIQUE_CONSTRAINT);
            }
        }
    }

    /**
     * Maps each legacy rows whose subject_id is unique per AcademicSession.
     * Idempotent: only NULL rows are considered.
     */
    private int backfill() {
        String sql = "UPDATE " + TABLE + " a "
                + "JOIN sections sec ON sec.id = a.section_id "
                + "JOIN batches b ON b.id = sec.batch_id "
                + "JOIN subject_offerings o ON o.subject_id = a." + LEGACY_SUBJECT_COLUMN + " "
                + "JOIN semesters s ON s.id = o.semester_id "
                + "SET a." + OFFERING_COLUMN + " = o.id "
                + "WHERE a." + OFFERING_COLUMN + " IS NULL "
                + "AND s.academic_session_id = b.academic_session_id "
                + "AND NOT EXISTS (SELECT 1 FROM subject_offerings o2 "
                + "JOIN semesters s2 ON s2.id = o2.semester_id "
                + "WHERE o2.subject_id = a." + LEGACY_SUBJECT_COLUMN + " "
                + "AND s2.academic_session_id = b.academic_session_id "
                + "AND o2.id <> o.id)";
        return jdbcTemplate.update(sql);
    }

    private void ensureLegacySubjectColumnNullable() {
        String nullability = columnNullability(TABLE, LEGACY_SUBJECT_COLUMN);
        if (nullability != null && !"NO".equalsIgnoreCase(nullability)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " MODIFY COLUMN "
                + LEGACY_SUBJECT_COLUMN + " BIGINT NULL");
        log.info("Teacher-assignment migration: legacy '{}' relaxed to NULL "
                + "(deprecated compatibility column, retained).", LEGACY_SUBJECT_COLUMN);
    }

    private void ensureOfferingNotNull() {
        String nullability = columnNullability(TABLE, OFFERING_COLUMN);
        if (nullability != null && !"NO".equalsIgnoreCase(nullability)) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " MODIFY COLUMN "
                    + OFFERING_COLUMN + " BIGINT NOT NULL");
        }
    }

    private void ensureUniqueConstraint() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
                Integer.class, TABLE, UNIQUE_CONSTRAINT);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + UNIQUE_CONSTRAINT
                    + " UNIQUE (teacher_id, " + OFFERING_COLUMN + ", section_id)");
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