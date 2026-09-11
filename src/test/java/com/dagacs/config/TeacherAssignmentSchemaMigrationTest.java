package com.dagacs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static com.dagacs.config.TeacherAssignmentSchemaMigration.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * M9.3 migration behaviour, verified against a mocked JdbcTemplate:
 * empty/fresh schemas, successful backfill, unmatched rows, duplicate triples,
 * nullable-preservation, unique-constraint gating and idempotent re-runs.
 * Assertions never expect destructive SQL (no deletes, no column drops).
 */
@ExtendWith(MockitoExtension.class)
class TeacherAssignmentSchemaMigrationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments args;

    @InjectMocks
    private TeacherAssignmentSchemaMigration migration;

    private void tablePresent() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TABLE)))
                .thenReturn(1);
    }

    private void offeringColumnPresent(boolean present) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(TABLE), eq(OFFERING_COLUMN))).thenReturn(present ? 1 : 0);
    }

    private void legacySubjectColumnPresent(boolean present) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(TABLE), eq(LEGACY_SUBJECT_COLUMN))).thenReturn(present ? 1 : 0);
    }

    private void unmatched(long count) {
        when(jdbcTemplate.queryForObject(eq(SQL_UNMATCHED_COUNT), eq(Long.class))).thenReturn(count);
    }

    private void duplicates(long count) {
        when(jdbcTemplate.queryForObject(eq(SQL_DUPLICATE_COUNT), eq(Long.class))).thenReturn(count);
    }

    private void offeringNullability(String value) {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq(TABLE), eq(OFFERING_COLUMN))).thenReturn(value);
    }

    private void legacySubjectNullability(String value) {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq(TABLE), eq(LEGACY_SUBJECT_COLUMN))).thenReturn(value);
    }

    private void uniqueConstraint(boolean present) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(TABLE), eq(UNIQUE_CONSTRAINT))).thenReturn(present ? 1 : 0);
    }

    @Test
    void missingTable_doesNothing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TABLE)))
                .thenReturn(0);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void freshSchema_addsOfferingColumnSkippedBackfillAndHardens() {
        tablePresent();
        offeringColumnPresent(false);
        legacySubjectColumnPresent(false);
        unmatched(0);
        offeringNullability("YES");
        uniqueConstraint(false);

        migration.run(args);

        verify(jdbcTemplate).execute(contains("ADD COLUMN subject_offering_id BIGINT NULL"));
        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate).execute(contains("MODIFY COLUMN subject_offering_id BIGINT NOT NULL"));
        verify(jdbcTemplate).execute(contains("ADD CONSTRAINT uk_assignment UNIQUE (teacher_id, subject_offering_id, section_id)"));
    }

    @Test
    void alreadyMigrated_noLegacyColumn_isCompleteNoOp() {
        tablePresent();
        offeringColumnPresent(true);
        legacySubjectColumnPresent(false);
        unmatched(0);
        offeringNullability("NO");
        uniqueConstraint(true);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void legacyRow_backfillsWhenExactlyOneOffering_thenHardens() {
        tablePresent();
        offeringColumnPresent(true);
        legacySubjectColumnPresent(true);
        legacySubjectNullability("NO");
        when(jdbcTemplate.update(anyString())).thenReturn(1);
        unmatched(0);
        duplicates(0);
        offeringNullability("NO");
        uniqueConstraint(false);

        migration.run(args);

        verify(jdbcTemplate).update(anyString());
        verify(jdbcTemplate).execute(contains("ADD CONSTRAINT uk_assignment UNIQUE (teacher_id, subject_offering_id, section_id)"));
        verify(jdbcTemplate, never()).execute(contains("MODIFY COLUMN subject_offering_id BIGINT NOT NULL"));
    }

    @Test
    void unmatchedRows_preservedKeepNullableAndWarned() {
        tablePresent();
        offeringColumnPresent(true);
        legacySubjectColumnPresent(true);
        legacySubjectNullability("NO");
        when(jdbcTemplate.update(anyString())).thenReturn(0);
        unmatched(2);
        duplicates(0);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(contains("MODIFY COLUMN subject_offering_id BIGINT NOT NULL"));
        verify(jdbcTemplate, never()).execute(contains("ADD CONSTRAINT uk_assignment"));
        verify(jdbcTemplate, never()).execute(contains("DELETE"));
        verify(jdbcTemplate, never()).execute(contains("DROP"));
    }

    @Test
    void duplicateTriples_preservedAndUniqueUnenforced() {
        tablePresent();
        offeringColumnPresent(true);
        legacySubjectColumnPresent(true);
        legacySubjectNullability("NO");
        when(jdbcTemplate.update(anyString())).thenReturn(1);
        unmatched(0);
        duplicates(1);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(contains("ADD CONSTRAINT uk_assignment"));
        verify(jdbcTemplate, never()).execute(contains("MODIFY COLUMN subject_offering_id BIGINT NOT NULL"));
        verify(jdbcTemplate, never()).execute(contains("DELETE"));
    }

    @Test
    void legacyNotNullSubjectColumn_relaxedToNullable() {
        tablePresent();
        offeringColumnPresent(true);
        legacySubjectColumnPresent(true);
        legacySubjectNullability("NO");
        when(jdbcTemplate.update(anyString())).thenReturn(0);
        unmatched(0);
        duplicates(0);
        offeringNullability("NO");
        uniqueConstraint(true);

        migration.run(args);

        verify(jdbcTemplate).execute(contains("MODIFY COLUMN subject_id BIGINT NULL"));
        verify(jdbcTemplate, never()).execute(contains("MODIFY COLUMN subject_offering_id BIGINT NOT NULL"));
    }

    @Test
    void secondRun_isIdempotent() {
        tablePresent();
        offeringColumnPresent(true);
        legacySubjectColumnPresent(true);
        legacySubjectNullability("YES");
        when(jdbcTemplate.update(anyString())).thenReturn(0);
        unmatched(0);
        duplicates(0);
        offeringNullability("NO");
        uniqueConstraint(true);

        migration.run(args);

        verify(jdbcTemplate).update(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).execute(contains("ADD COLUMN"));
    }
}