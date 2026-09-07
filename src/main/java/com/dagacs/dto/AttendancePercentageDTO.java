package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePercentageDTO {

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}
