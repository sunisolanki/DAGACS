package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: one student below the application's fixed 75% attendance threshold,
 * with the subjects responsible for it.
 *
 * <p>{@code belowThresholdSubjects} only lists subjects that actually have
 * recorded attendance and are themselves below the threshold. A subject with no
 * conducted class is deliberately absent from this list: it is not the reason a
 * student is low, and inventing a {@code 0%} entry for it would be fabricating
 * data.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodLowAttendanceStudentDTO {

    private Long studentId;

    private String enrollmentNumber;

    private String rollNumber;

    private String studentName;

    private String sectionName;

    private String programName;

    private String semesterName;

    private String academicSessionName;

    private Long presentCount;

    /**
     * The denominator: <b>conducted classes</b> applicable to this student in the
     * context. Never the attendance-record count.
     */
    private Long totalClasses;

    private Double percentage;

    private Long subjectsBelowThreshold;

    private List<HodLowAttendanceSubjectDTO> belowThresholdSubjects;
}
