package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3: one band of the student attendance distribution of an academic
 * context.
 *
 * <p>The band boundaries are the application's pre-existing display bands
 * (>= 90, 75-89, below 75) and carry no new business meaning. A student with
 * <em>no</em> recorded attendance is <b>not</b> counted into any band: with the
 * application's established semantics {@code percentage} is null when nothing
 * was recorded, and inventing a band for it would fabricate data. Such students
 * are reported by {@link #noDataStudents} instead.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceDistributionDTO {

    /** Stable machine key, e.g. {@code high} / {@code medium} / {@code low}. */
    private String band;

    /** Human-readable band label, e.g. {@code 90-100%}. */
    private String label;

    private Long studentCount;
}
