package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Student-wise attendance matrix (enrollment-ordered register). Additive phase-2
 * style report on top of {@link com.dagacs.repository.TeacherStudentWiseReportRepository}.
 * One row per student, one column per distinct (date, lecturePeriod) session;
 * every cell stays keyed to exactly one AttendanceSession so multiple sessions
 * on the same date are never collapsed. The authenticated teacher is resolved
 * from the JWT; subjectId/sectionId/batchId are optional selection filters only
 * and are never used as identity keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentWiseReportDTO {

    private Long subjectId;

    private String subjectName;

    /** Set for section-mode classes; null in batch mode. */
    private Long sectionId;

    private String sectionName;

    /** Set for zero-section batch-mode classes; null in section mode. */
    private Long batchId;

    private String batchCode;

    /** Distinct sessions (date + lecturePeriod) covered by the matrix. */
    private List<StudentWiseColumnDTO> columns;

    /** Enrollment-ordered student rows. */
    private List<StudentWiseRowDTO> rows;
}