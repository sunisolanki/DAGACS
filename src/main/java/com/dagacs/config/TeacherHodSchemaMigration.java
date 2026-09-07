package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent schema migration for the M6.1 HOD binding columns on
 * {@code teachers}.
 * <p>
 * Adds {@code is_hod} (TINYINT(1) NOT NULL DEFAULT 0) and {@code department_id}
 * (BIGINT NULL, FK to {@code departments}) only when they are missing. This is
 * non-destructive: existing teacher rows keep working, new columns carry safe
 * defaults matching the entity mapping ({@code isHod=false}, nullable
 * department). On a fresh database Hibernate's {@code ddl-auto: update} creates
 * equivalent columns itself; this migration only guarantees the columns exist on
 * databases that predate the entity change.
 * </p>
 */
@Component
public class TeacherHodSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeacherHodSchemaMigration.class);

    private static final String TABLE = "teachers";
    private static final String HOD_COLUMN = "is_hod";
    private static final String DEPT_COLUMN = "department_id";
    private static final String FK_NAME = "fk_teacher_department";

    private final JdbcTemplate jdbcTemplate;

    public TeacherHodSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(TABLE)) {
            log.info("Schema migration: table '{}' not present yet; skipping.", TABLE);
            return;
        }

        ensureHodColumn();
        ensureDepartmentColumn();
        ensureDepartmentForeignKey();
    }

    private void ensureHodColumn() {
        if (columnExists(TABLE, HOD_COLUMN)) {
            log.info("Schema migration: '{}.{}' already exists; no change required.", TABLE, HOD_COLUMN);
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + HOD_COLUMN
                + " TINYINT(1) NOT NULL DEFAULT 0");
        log.info("Schema migration: added '{}.{}' TINYINT(1) NOT NULL DEFAULT 0.", TABLE, HOD_COLUMN);
    }

    private void ensureDepartmentColumn() {
        if (columnExists(TABLE, DEPT_COLUMN)) {
            log.info("Schema migration: '{}.{}' already exists; no change required.", TABLE, DEPT_COLUMN);
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + DEPT_COLUMN + " BIGINT NULL");
        log.info("Schema migration: added '{}.{}' BIGINT NULL.", TABLE, DEPT_COLUMN);
    }

    private void ensureDepartmentForeignKey() {
        if (constraintExists(FK_NAME)) {
            log.info("Schema migration: foreign key '{}' already exists; no change required.", FK_NAME);
            return;
        }
        if (!departmentsTableReady()) {
            log.info("Schema migration: cannot add foreign key '{}' until 'departments' exists; skipping.", FK_NAME);
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + FK_NAME
                + " FOREIGN KEY (" + DEPT_COLUMN + ") REFERENCES departments(id)");
        log.info("Schema migration: added foreign key '{}' to departments(id).", FK_NAME);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean departmentsTableReady() {
        return tableExists("departments");
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
                Integer.class, TABLE, constraintName);
        return count != null && count > 0;
    }
}