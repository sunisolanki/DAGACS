package com.dagacs.config;

import com.dagacs.config.AcademicSessionLegacySchemaMigration.ColumnMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicSessionLegacySchemaMigrationTest {

    private static final String UTF8MB4 = "utf8mb4";
    private static final String UNICODE_CI = "utf8mb4_unicode_ci";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments args;

    @InjectMocks
    private AcademicSessionLegacySchemaMigration migration;

    private void sessionTableExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("academic_sessions")))
                .thenReturn(1);
    }

    private void subjectsTableExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("subjects")))
                .thenReturn(1);
    }

    private void sessionColumn(String column, ColumnMeta meta) {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq("academic_sessions"), eq(column))).thenReturn(meta);
    }

    private void subjectsColumn(String column, ColumnMeta meta) {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq("subjects"), eq(column))).thenReturn(meta);
    }

    private static ColumnMeta nullableMeta(String type) {
        return new ColumnMeta(type, true, null, null, null, "", null);
    }

    @Test
    void missingTable_doesNotAlter() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("academic_sessions")))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("subjects")))
                .thenReturn(0);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).query(anyString(), any(ResultSetExtractor.class), any());
    }

    @Test
    void missingColumn_doesNotAlterThatColumnButStillProcessesOthers() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", null);
        sessionColumn("duration_hours", nullableMeta("int(11)"));
        sessionColumn("lecture_periods", nullableMeta("int(11)"));
        sessionColumn("credits", nullableMeta("int(11)"));
        subjectsColumn("department", nullableMeta("varchar(255)"));

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void allAlreadyNullable_doesNotAlter() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", nullableMeta("varchar(255)"));
        sessionColumn("duration_hours", nullableMeta("int(11)"));
        sessionColumn("lecture_periods", nullableMeta("int(11)"));
        sessionColumn("credits", nullableMeta("int(11)"));
        subjectsColumn("department", nullableMeta("varchar(255)"));

        migration.run(args);

        verify(jdbcTemplate, never()).execute(contains("ALTER TABLE"));
    }

    @Test
    void notNullLegacyColumns_alterPreservingMetadataDefinition() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", new ColumnMeta(
                "varchar(9)", false, "x", UTF8MB4, UNICODE_CI, "", "hello"));
        sessionColumn("duration_hours", new ColumnMeta(
                "int(11)", false, "5", null, null, "", null));
        sessionColumn("lecture_periods", new ColumnMeta(
                "int(11)", false, null, null, null, "", null));
        sessionColumn("credits", new ColumnMeta(
                "int(11)", false, null, null, null, "", null));
        subjectsColumn("department", new ColumnMeta(
                "varchar(255)", false, null, UTF8MB4, UNICODE_CI, "", null));

        migration.run(args);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(5)).execute(sql.capture());
        List<String> statements = sql.getAllValues();

        String semesterStatement = statements.get(0);
        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN semester "
                + "varchar(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci "
                + "NULL DEFAULT 'x' COMMENT 'hello'", semesterStatement);
        assertFalse(semesterStatement.contains("varchar(255)"),
                "the semester ALTER must use the metadata type, not a hardcoded guess");
        assertFalse(semesterStatement.contains("VARCHAR(255)"),
                "the semester ALTER must use the metadata type, not a hardcoded guess");

        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN duration_hours "
                + "int(11) NULL DEFAULT 5", statements.get(1));
        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN lecture_periods "
                + "int(11) NULL", statements.get(2));
        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN credits "
                + "int(11) NULL", statements.get(3));

        String departmentStatement = statements.get(4);
        assertEquals("ALTER TABLE subjects MODIFY COLUMN department "
                + "varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL",
                departmentStatement);
        assertTrue(departmentStatement.contains("varchar(255)"),
                "varchar(255) is allowed here only because the metadata said so");
    }

    @Test
    void mixedNullability_onlyAltersNotNullColumns() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", new ColumnMeta(
                "varchar(9)", false, null, UTF8MB4, UNICODE_CI, "", null));
        sessionColumn("duration_hours", nullableMeta("int(11)"));
        sessionColumn("lecture_periods", nullableMeta("int(11)"));
        sessionColumn("credits", nullableMeta("int(11)"));
        subjectsColumn("department", nullableMeta("varchar(255)"));

        migration.run(args);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).execute(sql.capture());
        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN semester "
                + "varchar(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL",
                sql.getValue());
        verify(jdbcTemplate, never()).execute(contains("ALTER TABLE subjects"));
    }

    @Test
    void autoIncrementExtra_isPreserved() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", new ColumnMeta(
                "int(11)", false, null, null, null, "auto_increment", null));
        sessionColumn("duration_hours", nullableMeta("int(11)"));
        sessionColumn("lecture_periods", nullableMeta("int(11)"));
        sessionColumn("credits", nullableMeta("int(11)"));
        subjectsColumn("department", nullableMeta("varchar(255)"));

        migration.run(args);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).execute(sql.capture());
        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN semester int(11) NULL AUTO_INCREMENT",
                sql.getValue());
    }

    @Test
    void unreproducibleExtra_isSkippedWithoutAlter() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", new ColumnMeta(
                "int(11)", false, null, null, null, "DEFAULT_GENERATED", null));
        sessionColumn("duration_hours", nullableMeta("int(11)"));
        sessionColumn("lecture_periods", nullableMeta("int(11)"));
        sessionColumn("credits", nullableMeta("int(11)"));
        subjectsColumn("department", nullableMeta("varchar(255)"));

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void preQuotedMariaDbDefault_isPreservedVerbatim() {
        sessionTableExists();
        subjectsTableExists();
        sessionColumn("semester", new ColumnMeta(
                "varchar(20)", false, "'y'", null, null, "", null));
        sessionColumn("duration_hours", nullableMeta("int(11)"));
        sessionColumn("lecture_periods", nullableMeta("int(11)"));
        sessionColumn("credits", nullableMeta("int(11)"));
        subjectsColumn("department", nullableMeta("varchar(255)"));

        migration.run(args);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).execute(sql.capture());
        assertEquals("ALTER TABLE academic_sessions MODIFY COLUMN semester varchar(20) NULL DEFAULT 'y'",
                sql.getValue());
    }
}