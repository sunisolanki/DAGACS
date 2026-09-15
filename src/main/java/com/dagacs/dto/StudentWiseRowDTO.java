package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One student row in the student-wise attendance matrix. Cells are aligned to
 * {@link StudentWiseReportDTO#getColumns()}; each cell is keyed to exactly one
 * session. Totals count each recorded session separately (per-record M4
 * semantics); percentage is null when nothing was recorded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentWiseRowDTO {

    private Long studentId;

    private String rollNumber;

    private String enrollmentNumber;

    private String name;

    private Long presentCount;

    private Long totalRecordedCount;

    /** presentCount / totalRecordedCount * 100; null when totalRecordedCount is 0. */
    private Double percentage;

    private List<StudentWiseCellDTO> cells;
}