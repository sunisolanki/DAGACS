package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One selectable node of the HOD academic hierarchy.
 *
 * <p>Reused across every level (Academic Session, Program, Semester, Section)
 * because {@code spring.jackson.default-property-inclusion=non_null} omits the
 * fields that do not apply to a given level, so a response never carries a
 * misleading empty value. Only {@code id} and {@code name} are always present;
 * they are display labels only and are never accepted as authorization input.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodHierarchyOptionDTO {

    private Long id;

    private String name;

    /** Business code when the level defines one (Session/Semester/Section). */
    private String code;

    /** Populated on Academic Session nodes so the client can never mix programs. */
    private Long programId;

    private String programName;

    /** Populated on Section nodes; a section's program is its batch's session's. */
    private Long batchId;

    private String batchName;

    /** Real count of students in scope for this node, when meaningful. */
    private Long studentCount;
}
