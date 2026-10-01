package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Root of the HOD academic hierarchy: the HOD's own department plus the two
 * cascade roots (Programs and Academic Sessions).
 *
 * <p>The department is echoed for display only. It is derived server-side from
 * the authenticated HOD identity and is never accepted from a request.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodHierarchyRootDTO {

    private Long departmentId;

    private String departmentName;

    private String departmentCode;

    /** Programs of the HOD's department. */
    private List<HodHierarchyOptionDTO> programs;

    /** Academic Sessions of the HOD's department, each carrying its program. */
    private List<HodHierarchyOptionDTO> academicSessions;
}
