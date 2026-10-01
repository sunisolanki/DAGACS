package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: the context-scoped low-attendance report.
 *
 * <p>The student population and the threshold are the application's existing
 * ones: the frozen {@code HAVING (present * 100.0 / total) < 75.0} rule
 * evaluated over the selected academic context. Phase 3 does not introduce,
 * widen, narrow or re-derive that threshold - {@link #thresholdPercentage} is
 * echoed for display only, and a client must not offer a "critical" band unless
 * such a threshold is defined by the application.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodLowAttendanceReportDTO {

    private HodAttendanceContextDTO context;

    private Double thresholdPercentage;

    private Integer totalStudents;

    private Long belowThresholdCount;

    private List<HodLowAttendanceStudentDTO> students;
}
