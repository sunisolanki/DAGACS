package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: attendance detail for one subject inside the currently selected HOD
 * academic context.
 *
 * <p>{@code students} is the enrolled population of the context (not only the
 * students who happen to have records), so a student with zero marks in this
 * subject is still reported. {@code studentsBelowThreshold} counts exactly
 * those rows whose own percentage is below the application's fixed 75% rule -
 * the same threshold the low-attendance report uses.</p>
 *
 * <p>{@code facultyNames} is resolved from the real
 * {@code TeacherSubjectSectionAssignment} table and is empty when no teacher is
 * assigned yet. It is never invented, and a subject with no assignment still
 * renders with an explicit "no faculty assigned" state.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodSubjectAttendanceDetailDTO {

    private HodAttendanceContextDTO context;

    private Long subjectId;

    private String subjectCode;

    private String subjectName;

    private String programName;

    private String semesterName;

    private String sectionName;

    private List<String> facultyNames;

    /**
     * <b>Conducted classes</b> of this subject in this context, never
     * attendance records. Null when the subject is not scoped to a section.
     */
    private Long totalClasses;

    /** Enrolled students of the context, including those with no marks. */
    private Long students;

    private Long presentCount;

    /**
     * The denominator: {@code totalClasses * students}. Every enrolled student
     * is measured against every conducted class of the subject.
     */
    private Long totalClassesAcrossStudents;

    /** {@code presentCount / totalClassesAcrossStudents}, or null at zero. */
    private Double averageAttendance;

    private Long studentsBelowThreshold;

    private Double thresholdPercentage;

    private List<HodSubjectAttendanceDetailRowDTO> studentRows;
}
