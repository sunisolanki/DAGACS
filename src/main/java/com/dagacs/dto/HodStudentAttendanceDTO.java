package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodStudentAttendanceDTO {

    private String rollNumber;

    /**
     * Phase 3: stable identifier of the student, so a HOD can open this
     * student's attendance detail directly from the student list.
     *
     * <p>Populated only by the academic-context-scoped query, which already
     * groups by the student primary key. Null on the frozen department-wide
     * analytics query, whose grouping projection does not carry an id - never
     * fabricated there.</p>
     */
    private Long studentId;

    /** Nullable: enrolment number is optional on the Student profile. */
    private String enrollmentNumber;

    private String studentName;

    private String sectionName;

    private String batchName;

    /**
     * Academic context of the row. Populated by the context-scoped queries so
     * a HOD can never see a student without knowing which session, program and
     * semester the figure belongs to. Null on the frozen department-wide query.
     */
    private String academicSessionName;

    private String programName;

    private String semesterName;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}