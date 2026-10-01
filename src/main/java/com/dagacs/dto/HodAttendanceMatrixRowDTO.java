package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: one student row of the HOD attendance matrix (a cross-tab row, not a
 * normalised attendance record).
 *
 * <p><b>A student with zero attendance records is still present.</b> The student
 * population comes from the enrolment of the selected context, never from the
 * attendance table, so a student who has never been marked appears with
 * {@code totalPresent = 0}, {@code totalClasses = 0} and
 * {@code overallPercentage = null} rather than being silently dropped by an
 * inner join.</p>
 *
 * <p>{@link #subjects} is an array whose order is <b>identical</b> to the
 * top-level {@code subjects} column list, so a client renders the cross-tab by
 * position and never has to look a column up by name. The array always has one
 * entry per column, including the zero-filled entries for subjects with no
 * classes.</p>
 *
 * <p>{@code totalPresent} / {@code totalClasses} are the sum of this row's own
 * cells, which keeps a single student row internally consistent across every
 * subject column. {@code overallPercentage} is
 * {@code totalPresent / totalClasses * 100} - never an average of the subject
 * percentages.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceMatrixRowDTO {

    private Long studentId;

    private String enrollmentNumber;

    private String rollNumber;

    private String studentName;

    private String sectionName;

    private List<HodAttendanceMatrixCellDTO> subjects;

    private Long totalPresent;

    private Long totalClasses;

    private Double overallPercentage;
}
