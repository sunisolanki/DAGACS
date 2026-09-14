package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the HOD daily-lecture report (M7.1). Read-only, derived from
 * AttendanceRecord rows only (a session contributes a row only when it actually
 * has recorded attendance rows). No internal IDs are exposed (mirrors M6.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodDailyLectureReportDTO {

    private String date;

    private String lecturePeriod;

    private String subjectCode;

    private String subjectName;

    private String sectionCode;

    private String sectionName;

    /**
     * Batch-level context for zero-section batch-mode sessions (Phase 2).
     * Populated instead of the section fields.
     */
    private String batchCode;

    private Long presentCount;

    private Long totalRecordedCount;

    /**
     * presentCount / totalRecordedCount * 100; null when totalRecordedCount is 0
     * (recorded rows only, mirroring the frozen M4 percentage semantics).
     */
    private Double percentage;
}