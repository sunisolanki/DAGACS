package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3 (with the Phase 4A additive columns): one subject row of the
 * academic-context overview.
 *
 * <p><b>Corrected denominator.</b> {@code totalClasses} is the subject's
 * <b>conducted attendance sessions</b> measured against the enrolled population of
 * the context, and {@code percentage} is
 * {@code presentCount / totalClasses * 100} - null when {@code totalClasses} is 0,
 * never a fabricated 0%. A student who was never marked in a conducted class is
 * still inside {@code totalClasses}, which is what makes "unmarked counts as
 * absent" true rather than aspirational. This is the canonical Phase 3 rule and
 * must not be changed here.
 *
 * <p>{@code classesConducted} is the subject's own conducted-session count, the
 * per-column denominator the matrix uses for that subject.
 *
 * <p>{@code facultyNames} comes from the real teaching-assignment table and is
 * empty when no teacher is assigned yet. It is never invented - the export layer
 * renders that emptiness as an explicit statement.
 *
 * <p><b>Phase 4A additions</b> ({@code students},
 * {@code studentsBelowThreshold}) are reporting columns only. They are derived
 * from the same canonical per-student, per-subject PRESENT marks the low-attendance
 * report already uses, so they add no new attendance semantics and no new
 * denominator.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceSubjectSummaryDTO {

    private Long subjectId;

    private Long subjectOfferingId;

    private String subjectCode;

    private String subjectName;

    private List<String> facultyNames;

    private Long classesConducted;

    private Long presentCount;

    /**
     * The denominator for the subject total: conducted classes of this subject
     * measured against the enrolled population of the context.
     *
     * <p>Named for what it counts. A student who was never marked in a conducted
     * class is still inside it, which is exactly the corrected behaviour.</p>
     */
    private Long totalClasses;

    private Double percentage;

    /**
     * Enrolled students of the context, i.e. the population this subject's
     * {@code totalClasses} was measured against.
     *
     * <p>Counted, never filtered by attendance: a student with no marks in this
     * subject is still part of the population, which is the whole point of the
     * denominator.</p>
     */
    private Long students;

    /**
     * Students whose attendance in <b>this subject</b> is below the threshold.
     *
     * <p>A subject with no conducted class reports {@code 0}: with no denominator
     * there is no percentage, so the student is not "below threshold" for it and
     * inventing a 0% would misattribute the student's shortfall.</p>
     */
    private Long studentsBelowThreshold;
}
