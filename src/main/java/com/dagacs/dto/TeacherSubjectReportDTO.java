package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the teacher's own subject-wise attendance report (M7.1). The
 * authenticated teacher is resolved from the JWT and the data is restricted to
 * the teacher's own teaching assignments (subject + section) and their own
 * recorded sessions; subjectId/sectionId are returned only as neutral labels for
 * the upcoming UI/export and are never accepted back as request parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherSubjectReportDTO {

    private Long subjectId;

    private String subjectCode;

    private String subjectName;

    private Long sectionId;

    private String sectionCode;

    private String sectionName;

    private Long presentCount;

    private Long totalRecordedCount;

    /**
     * presentCount / totalRecordedCount * 100; null when totalRecordedCount is 0
     * (recorded rows only, mirroring the frozen M4 percentage semantics).
     */
    private Double percentage;
}