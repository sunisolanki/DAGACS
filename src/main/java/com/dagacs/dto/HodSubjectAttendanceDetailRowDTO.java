package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3: one student row inside the subject attendance detail.
 *
 * <p>The percentage is computed with the identical helper the rest of the HOD
 * module uses, so a subject view and a student view of the same record can
 * never report different numbers.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodSubjectAttendanceDetailRowDTO {

    private Long studentId;

    private String enrollmentNumber;

    private String rollNumber;

    private String studentName;

    private Long present;

    /**
     * The denominator for this student in this subject: the conducted classes of
     * the subject, identical for every student of the class.
     */
    private Long total;

    /** Null only when the subject has no conducted classes at all. */
    private Double percentage;
}
