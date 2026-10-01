package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodSubjectAttendanceDTO {

    /**
     * Phase 3: stable identifier of the subject, so a HOD can open this
     * subject's attendance detail directly from the subject list.
     *
     * <p>Populated only by the academic-context-scoped query. Null on the frozen
     * department-wide analytics query, whose grouping projection does not carry
     * an id - never fabricated there.</p>
     */
    private Long subjectId;

    private String subjectCode;

    private String subjectName;

    /** Academic context; null on the frozen department-wide query. */
    private String programName;

    private String semesterName;

    private String sectionName;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}