package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3: one subject that is dragging a low-attendance student down.
 *
 * <p>The percentage is produced by the same arithmetic as every other Phase 3
 * and existing HOD attendance figure, so a subject breakdown can never disagree
 * with the matrix cell for the same student and subject.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodLowAttendanceSubjectDTO {

    private Long subjectId;

    private String subjectCode;

    private String subjectName;

    private Long present;

    /**
     * The denominator for this subject: its <b>conducted classes</b> in the
     * context, identical for every student.
     */
    private Long total;

    private Double percentage;
}
