package com.dagacs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Repository-tracked, idempotent schema migration for legacy master-data columns.
 * <p>
 * Academic Session deployments created before the master data correction may still
 * hold the deprecated {@code semester}, {@code duration_hours}, {@code lecture_periods}
 * and {@code credits} columns, and Subject deployments may still hold the old
 * free-text {@code department} column. These columns are NOT part of the active
 * domain, but if any of them is still {@code NOT NULL} in the production database,
 * every INSERT on {@code academic_sessions} / {@code subjects} would be rejected
 * because the active entities never write them. Hibernate's {@code ddl-auto: update}
 * cannot relax an existing NOT NULL column, so this migration does it explicitly.
 * </p>
 * <p>
 * Each candidate column is guarded: it is only altered if the table exists, the
 * column exists, and the current definition is still NOT NULL. The ALTER never
 * assumes a type: it reads the column's current definition from
 * {@code INFORMATION_SCHEMA.COLUMNS} (exact column type, character set, collation,
 * default, extra, comment) and restates it verbatim, changing ONLY the
 * {@code NOT NULL} flag to {@code NULL}. It never drops a column, never touches
 * row data, and never changes column content. A bare {@code MODIFY COLUMN} would
 * reset untagged attributes (verified locally: the unicode collation falls back to
 * the table default and any default/comment is dropped), which is why the full
 * definition is rebuilt from metadata. On a fresh database (or one created after
 * the master data correction) every candidate is a no-op. This runs on every
 * startup, so any deployment that reaches this code self-heals once, then becomes
 * a no-op.
 * </p>
 */
@Component
public class AcademicSessionLegacySchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AcademicSessionLegacySchemaMigration.class);

    private static final List<LegacyColumn> COLUMNS = Arrays.asList(
            new LegacyColumn("academic_sessions", "semester"),
            new LegacyColumn("academic_sessions", "duration_hours"),
            new LegacyColumn("academic_sessions", "lecture_periods"),
            new LegacyColumn("academic_sessions", "credits"),
            new LegacyColumn("subjects", "department")
    );

    private static final String TABLE_EXISTS_SQL =
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";

    private static final String COLUMN_METADATA_SQL =
            "SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_SET_NAME, "
                    + "COLLATION_NAME, EXTRA, COLUMN_COMMENT "
                    + "FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

    private final JdbcTemplate jdbcTemplate;

    public AcademicSessionLegacySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (LegacyColumn column : COLUMNS) {
            relaxIfNotNull(column);
        }
    }

    private void relaxIfNotNull(LegacyColumn column) {
        if (!tableExists(column.table)) {
            log.info("Schema migration: table '{}' not present yet; skipping legacy column '{}'.",
                    column.table, column.column);
            return;
        }

        ColumnMeta meta = columnMetadata(column);
        if (meta == null) {
            log.info("Schema migration: legacy column '{}.{}' not found; nothing to do.",
                    column.table, column.column);
            return;
        }

        if (meta.nullable) {
            log.info("Schema migration: legacy column '{}.{}' is already nullable; no change required.",
                    column.table, column.column);
            return;
        }

        if (!meta.canReconstructExactly()) {
            log.warn("Schema migration: column '{}.{}' has EXTRA metadata '{}' which cannot be "
                    + "reproduced safely; skipping to avoid altering attributes other than nullability.",
                    column.table, column.column, meta.extra);
            return;
        }

        jdbcTemplate.execute("ALTER TABLE " + column.table + " MODIFY COLUMN "
                + column.column + " " + meta.alterDefinition());
        log.info("Schema migration: relaxed legacy column '{}.{}' from NOT NULL to NULL, "
                + "preserving its existing definition.", column.table, column.column);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(TABLE_EXISTS_SQL, Integer.class, tableName);
        return count != null && count > 0;
    }

    private ColumnMeta columnMetadata(LegacyColumn column) {
        return jdbcTemplate.query(COLUMN_METADATA_SQL, rs -> rs.next() ? ColumnMeta.fromRow(rs) : null,
                column.table, column.column);
    }

    /**
     * The current definition of a candidate column as read from INFORMATION_SCHEMA.
     * Visible to the same package so the migration test can build exact stubs.
     */
    static final class ColumnMeta {
        final String columnType;
        final boolean nullable;
        final String defaultValue;
        final String characterSet;
        final String collation;
        final String extra;
        final String comment;

        ColumnMeta(String columnType, boolean nullable, String defaultValue, String characterSet,
                   String collation, String extra, String comment) {
            this.columnType = columnType;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.characterSet = characterSet;
            this.collation = collation;
            this.extra = extra;
            this.comment = comment;
        }

        static ColumnMeta fromRow(ResultSet rs) throws SQLException {
            return new ColumnMeta(
                    rs.getString("COLUMN_TYPE"),
                    "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                    rs.getString("COLUMN_DEFAULT"),
                    rs.getString("CHARACTER_SET_NAME"),
                    rs.getString("COLLATION_NAME"),
                    rs.getString("EXTRA"),
                    rs.getString("COLUMN_COMMENT"));
        }

        /**
         * True when the metadata can be reproduced in a MODIFY COLUMN statement
         * without altering anything other than nullability.
         */
        boolean canReconstructExactly() {
            return extra == null || extra.isEmpty() || "auto_increment".equalsIgnoreCase(extra);
        }

        /**
         * Rebuilds the column definition from the metadata. The character set and
         * collation must follow the type directly (before NULL); a bare MODIFY would
         * otherwise fall back to the table defaults for untagged attributes.
         */
        String alterDefinition() {
            StringBuilder definition = new StringBuilder(columnType);
            if (characterSet != null && !characterSet.isEmpty()) {
                definition.append(" CHARACTER SET ").append(characterSet);
            }
            if (collation != null && !collation.isEmpty()) {
                definition.append(" COLLATE ").append(collation);
            }
            definition.append(" NULL");
            if (defaultValue != null) {
                definition.append(" DEFAULT ").append(defaultLiteral(defaultValue));
            }
            if (extra != null && "auto_increment".equalsIgnoreCase(extra)) {
                definition.append(" AUTO_INCREMENT");
            }
            if (comment != null && !comment.isEmpty()) {
                definition.append(" COMMENT '").append(comment.replace("'", "''")).append('\'');
            }
            return definition.toString();
        }

        /**
         * Renders a default literal read from the metadata. MariaDB returns already
         * quoted literals (e.g. {@code 'x'}); MySQL returns bare values. Both are
         * accepted so the migration is engine-agnostic.
         */
        private static String defaultLiteral(String raw) {
            if (raw.length() >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
                return raw;
            }
            if (raw.matches("-?\\d+(\\.\\d+)?")) {
                return raw;
            }
            return "'" + raw.replace("'", "''") + "'";
        }
    }

    private static final class LegacyColumn {
        final String table;
        final String column;

        LegacyColumn(String table, String column) {
            this.table = table;
            this.column = column;
        }
    }
}