package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the HOD recording-coverage report (M7.1). Coverage is RECORD-BACKED:
 * per subject-section it shows only the dates/sessions on which actual
 * AttendanceRecord rows existed. A session row without records (e.g. SCHEDULED
 * with no marks) never contributes. No internal IDs are exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodCoverageReportDTO {

    private String subjectCode;

    private String subjectName;

    private String sectionCode;

    private String sectionName;

    /**
     * COUNT(DISTINCT AttendanceRecord.date) within scope/date-range — distinct dates
     * on which attendance records actually existed.
     */
    private Long recordedDateCount;

    /**
     * COUNT(DISTINCT AttendanceRecord.session.id) — distinct record-backed sessions.
     * Never a raw AttendanceSession count and never multiplied by the record join.
     */
    private Long sessionCount;
}