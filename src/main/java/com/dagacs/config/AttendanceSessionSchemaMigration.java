package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent schema migration for M3.2 (attendance session & marking workflow).
 * <p>
 * Hibernate's {@code ddl-auto: update} can add new columns, foreign keys, and new
 * unique constraints, but it can never DROP the obsolete M3.1 unique constraint
 * {@code uk_attendance_student_subject_section_date} on {@code attendance_records}.
 * That index is referenced by the {@code student_id} foreign key, so it must be
 * dropped by relocating the FK first.
 * </p>
 * <p>
 * This migration is guarded by {@code INFORMATION_SCHEMA} checks, is idempotent,
 * non-destructive, and never seeds data.
 * </p>
 */
@Component
public class AttendanceSessionSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSessionSchemaMigration.class);

    private static final String SESSION_TABLE = "attendance_sessions";
    private static final String RECORD_TABLE = "attendance_records";

    private final JdbcTemplate jdbcTemplate;

    public AttendanceSessionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSessionTeacherColumn();
        ensureSessionUniqueConstraint();
        ensureRecordSessionAndMarkedByColumns();
        ensureRecordSessionAndMarkedByFks();
        relocateRecordStudentFk();
        dropLegacyRecordUniqueConstraint();
        ensureRecordSessionUniqueConstraint();
    }

    private void ensureSessionTeacherColumn() {
        if (!columnExists(SESSION_TABLE, "teacher_id")) {
            jdbcTemplate.execute("ALTER TABLE " + SESSION_TABLE + " ADD COLUMN teacher_id BIGINT NULL");
            log.info("Schema migration: added '{}.teacher_id'.", SESSION_TABLE);
        }
        // NOT NULL is enforced by Hibernate's mapping when DDL is generated. On the
        // (empty) dev table Hibernate creates it NOT NULL; this migration does not
        // force a data-destructive backfill.
    }

    private void ensureSessionUniqueConstraint() {
        if (uniqueConstraintExists(SESSION_TABLE, "uk_session_class")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + SESSION_TABLE
                + " ADD CONSTRAINT uk_session_class UNIQUE (subject_code, section_code, date, lecture_period)");
        log.info("Schema migration: added unique 'uk_session_class'.");
    }

    private void ensureRecordSessionAndMarkedByColumns() {
        if (!columnExists(RECORD_TABLE, "session_id")) {
            jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE + " ADD COLUMN session_id BIGINT NULL");
            log.info("Schema migration: added '{}.session_id'.", RECORD_TABLE);
        }
        if (!columnExists(RECORD_TABLE, "marked_by")) {
            jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE + " ADD COLUMN marked_by BIGINT NULL");
            log.info("Schema migration: added '{}.marked_by'.", RECORD_TABLE);
        }
    }

    private void ensureRecordSessionAndMarkedByFks() {
        addForeignKeyIfMissing("session_id", SESSION_TABLE, "fk_record_session");
        addForeignKeyIfMissing("marked_by", "teachers", "fk_record_marked_by");
    }

    /**
     * A foreign key on {@code student_id} references the legacy unique index, so the
     * index cannot be dropped directly. Recreate that FK (MySQL then creates its own
     * supporting index) before the legacy unique index is removed, and restore the FK
     * afterwards so {@code attendance_records.student_id} stays a valid foreign key.
     */
    private void relocateRecordStudentFk() {
        boolean hasStudentFk = foreignKeysOnColumn(RECORD_TABLE, "student_id").stream()
                .anyMatch(name -> !"UK_ATTENDANCE_STUDENT_SESSION".equalsIgnoreCase(name));
        if (!hasStudentFk) {
            addForeignKeyIfMissing("student_id", "students", "fk_record_student");
        }
    }

    private void dropLegacyRecordUniqueConstraint() {
        if (!uniqueConstraintExists(RECORD_TABLE, "uk_attendance_student_subject_section_date")) {
            return;
        }
        // A FK on student_id may still reference this index; drop such FKs first.
        for (String fk : foreignKeysOnColumn(RECORD_TABLE, "student_id")) {
            if ("FK_RECORD_STUDENT".equalsIgnoreCase(fk)) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE + " DROP FOREIGN KEY " + fk);
            log.info("Schema migration: dropped FK '{}' on {} prior to legacy index removal.", fk, RECORD_TABLE);
        }
        jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE
                + " DROP INDEX uk_attendance_student_subject_section_date");
        log.info("Schema migration: dropped legacy unique 'uk_attendance_student_subject_section_date'.");
        // Restore the student FK (MySQL creates a supporting index automatically).
        if (!hasForeignKey(RECORD_TABLE, "fk_record_student")
                && foreignKeysOnColumn(RECORD_TABLE, "student_id").isEmpty()) {
            jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE
                    + " ADD CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES students (id)");
            log.info("Schema migration: restored FK 'fk_record_student'.");
        }
    }

    private void ensureRecordSessionUniqueConstraint() {
        if (!uniqueConstraintExists(RECORD_TABLE, "uk_attendance_student_session")) {
            jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE
                    + " ADD CONSTRAINT uk_attendance_student_session UNIQUE (session_id, student_id)");
            log.info("Schema migration: added unique 'uk_attendance_student_session'.");
        }
    }

    private void addForeignKeyIfMissing(String column, String refTable, String constraintName) {
        if (hasForeignKey(RECORD_TABLE, constraintName)
                || foreignKeysOnColumn(RECORD_TABLE, column).stream()
                        .anyMatch(name -> !constraintName.equalsIgnoreCase(name))) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + RECORD_TABLE
                + " ADD CONSTRAINT " + constraintName
                + " FOREIGN KEY (" + column + ") REFERENCES " + refTable + " (id)");
        log.info("Schema migration: added FK '{}' on {}.{}.", constraintName, RECORD_TABLE, column);
    }

    private boolean hasForeignKey(String tableName, String constraintName) {
        return foreignKeysOnColumn(tableName, null).stream()
                .anyMatch(name -> name.equalsIgnoreCase(constraintName));
    }

    private List<String> foreignKeysOnColumn(String tableName, String columnName) {
        if (tableName == null) {
            return List.of();
        }
        if (columnName == null) {
            return jdbcTemplate.query(
                    "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                            + "AND REFERENCED_TABLE_NAME IS NOT NULL",
                    (rs, i) -> rs.getString(1), tableName);
        }
        return jdbcTemplate.query(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? "
                        + "AND REFERENCED_TABLE_NAME IS NOT NULL",
                (rs, i) -> rs.getString(1), tableName, columnName);
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

    private boolean uniqueConstraintExists(String tableName, String constraintName) {
        if (!tableExists(tableName)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? "
                        + "AND CONSTRAINT_TYPE = 'UNIQUE'",
                Integer.class, tableName, constraintName);
        return count != null && count > 0;
    }
}
