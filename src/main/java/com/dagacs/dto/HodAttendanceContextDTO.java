package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3: the resolved academic context a Phase 3 HOD attendance report was
 * computed for.
 *
 * <p>Every identifier here has already been proven to belong to the
 * authenticated HOD's department (and, where a combination was supplied, proven
 * to be internally consistent) before the report was assembled. The names are
 * resolved from the same rows the identifiers came from, so a report can never
 * display a label that disagrees with its own data.</p>
 *
 * <p>The <b>department is never part of this DTO</b>: it is derived from the JWT
 * and is not a report dimension.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceContextDTO {

    private Long academicSessionId;

    private String academicSessionName;

    private Long programId;

    private String programName;

    private Long semesterId;

    private String semesterName;

    private Long sectionId;

    private String sectionName;

    /**
     * True when the four mandatory levels (session, program, semester, section)
     * are all present. A matrix is never produced for a partial context, so the
     * client can render the "select the academic context" state directly from
     * this flag instead of inferring it.
     */
    private Boolean complete;
}
