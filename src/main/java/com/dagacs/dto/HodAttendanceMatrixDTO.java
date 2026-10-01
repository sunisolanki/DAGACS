package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: the HOD attendance matrix for one fully-resolved academic context.
 *
 * <p>The response carries explicit metadata and stable identifiers so a client
 * can render the cross-tab, export it, and re-issue the identical request
 * without inferring anything from display text.</p>
 *
 * <ul>
 *   <li>{@code subjects} - the dynamic subject columns, in the authoritative
 *       display order. No duplicate subject ever appears: the column set is
 *       produced by a single {@code SELECT DISTINCT} over the context's
 *       offerings and teaching assignments.</li>
 *   <li>{@code students} - one row per enrolled student of the context, in
 *       enrollment-number order by default. No duplicate student row exists:
 *       the population is paged from the student table grouped by primary key.</li>
 *   <li>Every row's {@code subjects} array has exactly {@code subjects.size()}
 *       entries, aligned by position with the column list.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceMatrixDTO {

    private HodAttendanceContextDTO context;

    private List<HodAttendanceMatrixColumnDTO> subjects;

    private List<HodAttendanceMatrixRowDTO> students;

    private Integer page;

    private Integer size;

    private Long totalElements;

    private Integer totalPages;

    /**
     * True when the report was narrowed to a single subject. The client renders
     * the focused single-subject matrix instead of the multi-subject cross-tab.
     */
    private Boolean singleSubject;

    private Double thresholdPercentage;
}
