package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One selectable academic master-data option for the Manage Students filter
 * bar. Options are sourced from the academic master-data entities (never from
 * student records), so they remain available even when zero students currently
 * belong to that category. Context fields let the UI render disambiguating
 * labels such as "2025-26 · B.Tech" or "3rd Semester · 2025-26".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentFilterOption {

    private Long id;
    private String name;

    private Long programId;
    private String programName;

    private Long academicSessionId;
    private String academicSessionName;

    private Long batchId;
    private String batchName;
}