package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: one dynamic subject column of the HOD attendance matrix.
 *
 * <p>Columns are generated from the actual subject offerings of the selected
 * academic context ({@code SubjectOffering}) plus any subject actually assigned
 * to teach the selected section ({@code TeacherSubjectSectionAssignment}).
 * A subject is never included because it merely belongs to the department, and
 * a subject can never leak in from another semester, program, academic session
 * or section.</p>
 *
 * <p>{@code subjectId} is the stable internal identifier clients must key on;
 * {@code subjectCode} / {@code subjectName} are display labels only. The
 * response never requires a client to use display text as a key.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceMatrixColumnDTO {

    private Long subjectId;

    private Long subjectOfferingId;

    private String subjectCode;

    private String subjectName;

    /** Real teaching assignments for the scoped section; empty when unassigned. */
    private List<String> facultyNames;
}
