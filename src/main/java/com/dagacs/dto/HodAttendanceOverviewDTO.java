package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: the academic-context attendance overview.
 *
 * <p>Every aggregate on this DTO is derived from authoritative backend data. A
 * metric with no data source is {@code null} and the client renders an explicit
 * unavailable state for it - the DTO never carries a fabricated zero.</p>
 *
 * <p>{@code overallPresentCount} / {@code overallRecordedCount} /
 * {@code overallPercentage} are the sum over the per-student aggregate that the
 * existing HOD analytics already produces, so this screen can never disagree
 * with the existing HOD student list.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceOverviewDTO {

    private HodAttendanceContextDTO context;

    private Long totalStudents;

    private Long overallPresentCount;

    /**
     * The overall denominator: <b>conducted attendance sessions</b> applicable
     * to the context, never attendance records.
     *
     * <p>A student with no attendance record still contributes their conducted
     * classes here, so they are measured rather than skipped.</p>
     */
    private Long overallTotalClasses;

    private Double overallPercentage;

    /** Students strictly below the application's fixed 75% threshold. */
    private Long belowThresholdCount;

    /**
     * Conducted classes in the context, or null when the context was not fully
     * resolved and the metric would be ambiguous.
     */
    private Long classesConducted;

    private Double thresholdPercentage;

    /** The three attendance bands, always in this order. */
    private List<HodAttendanceDistributionDTO> distribution;

    /**
     * Students with <b>no conducted classes at all</b>.
     *
     * <p>Reported separately because their percentage is genuinely unavailable
     * (no denominator), and must not be shown as 0% or 100%. A student with
     * conducted classes but no marks is <em>not</em> in this count: they report
     * a real 0%.</p>
     */
    private Long noConductedClasses;

    private List<HodAttendanceSubjectSummaryDTO> subjects;
}
