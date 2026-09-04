package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repository-tracked, idempotent schema migration.
 * <p>
 * Guarantees that {@code sections.subject_id} is nullable. Hibernate's
 * {@code ddl-auto: update} cannot relax an existing NOT NULL column, so this
 * migration explicitly relaxes it. It runs after the schema is created, checks
 * {@code INFORMATION_SCHEMA}, and only issues the ALTER when the column is still
 * NOT NULL. The ALTER keeps the column type ({@code BIGINT}) and therefore does
 * not drop the existing foreign key on {@code subject_id}; it also never touches
 * row data, so no existing Section data is lost.
 * </p>
 * <p>
 * Reproductibility: the file lives in the repository and runs on every startup
 * (existing and fresh databases). On a fresh database where Hibernate already
 * creates the column as nullable (per {@code Section.subject} mapping), the
 * migration is a no-op; if the schema originates NOT NULL, it is relaxed.
 * </p>
 */
@Component
public class SectionSubjectSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SectionSubjectSchemaMigration.class);

    private static final String TABLE = "sections";
    private static final String COLUMN = "subject_id";

    private final JdbcTemplate jdbcTemplate;

    public SectionSubjectSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(TABLE)) {
            log.info("Schema migration: table '{}' not present yet; skipping. "
                    + "Hibernate creates it with nullable '{}' per the Section entity mapping.", TABLE, COLUMN);
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

        jdbcTemplate.execute("ALTER TABLE " + TABLE + " MODIFY COLUMN " + COLUMN + " BIGINT NULL");
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
