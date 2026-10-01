package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3: one subject line of the HOD student attendance detail.
 *
 * <p>{@code notAttended} is {@code total - present} and therefore covers both
 * explicitly ABSENT marks and the difference between the recorded denominator
 * and the present count - the application's established "everything that is not
 * present" grouping. It is never a second, differently-computed attendance
 * total.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodStudentSubjectAttendanceDTO {

    private Long subjectId;

    private String subjectCode;

    private String subjectName;

    /**
     * The denominator: <b>conducted classes of this subject</b>, whether or not
     * this student was marked in them. Unmarked classes stay inside it, which is
     * what makes "unmarked counts as absent" true rather than aspirational.
     */
    private Long classes;

    private Long present;

    /**
     * Conducted classes this student did not attend, i.e. {@code classes -
     * present}: explicitly ABSENT marks plus every unmarked conducted class.
     */
    private Long notAttended;

    /** Null only when the subject has no conducted classes at all. */
    private Double percentage;
}
