package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cascaded academic filter options for the Manage Students filter bar.
 * <p>
 * Every list is computed FROM THE ACADEMIC MASTER DATA ONLY (AcademicSession /
 * Program / Semester / Batch / Section repositories) using an
 * anchor-academic-session intersection of the caller's current selection, so:
 * </p>
 * <ul>
 *   <li>options remain selectable even when zero students belong to the
 *       category, and</li>
 *   <li>each level only offers options valid for the selected academic context
 *       (the dependency chain Academic Session &rarr; Program &rarr;
 *       Semester &rarr; Batch &rarr; Section).</li>
 * </ul>
 * <p>
 * This is deliberately decoupled from the student query: the query filters
 * {@code students} by the explicitly selected ids only.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentFilterOptionsResponse {

    private List<StudentFilterOption> academicSessions;
    private List<StudentFilterOption> programs;
    private List<StudentFilterOption> semesters;
    private List<StudentFilterOption> batches;
    private List<StudentFilterOption> sections;
}