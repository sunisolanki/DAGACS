package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A subject offered in a specific academic context.
 *
 * <p>Derived exclusively from {@code SubjectOffering -> Semester ->
 * AcademicSession -> Program}. A subject is never listed because it merely
 * belongs to a department, and a subject code/name is never assumed to mean the
 * same offering across two different semesters or programs.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodHierarchySubjectDTO {

    private Long id;

    private String code;

    private String name;

    private Long semesterId;

    private String semesterName;

    private Long programId;

    private String programName;

    /** Set when the request was scoped to one section. */
    private Long sectionId;

    private String sectionName;

    /**
     * Teachers assigned to teach this offering to the scoped section, resolved
     * through {@code TeacherSubjectSectionAssignment}. Empty when the request
     * was not section-scoped, or when no teacher is assigned yet.
     */
    private List<String> facultyNames;
}
