package com.dagacs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionSubjectSchemaMigrationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments args;

    @InjectMocks
    private SectionSubjectSchemaMigration migration;

    private void tableExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("sections")))
                .thenReturn(1);
    }

    @Test
    void alreadyNullable_doesNotAlter() {
        tableExists();
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq("sections"), eq("subject_id"))).thenReturn("YES");

        migration.run(args);

        verify(jdbcTemplate, never()).execute(contains("ALTER TABLE"));
    }

    @Test
    void notNullColumn_altersToNullable() {
        tableExists();
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq("sections"), eq("subject_id"))).thenReturn("NO");

        migration.run(args);

        verify(jdbcTemplate).execute(contains("ALTER TABLE sections"));
        verify(jdbcTemplate).execute(contains("MODIFY COLUMN subject_id BIGINT NULL"));
    }

    @Test
    void missingTable_doesNotAlter() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("sections")))
                .thenReturn(0);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).query(anyString(), any(ResultSetExtractor.class), any());
    }

    @Test
    void missingColumn_doesNotAlter() {
        tableExists();
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class),
                eq("sections"), eq("subject_id"))).thenReturn(null);

        migration.run(args);

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
