package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodDashboardDTO {

    private String departmentName;

    private String departmentCode;

    private Long programCount;

    private Long batchCount;

    private Long sectionCount;

    private Long studentCount;

    private Long totalRecordedCount;

    private Long presentCount;

    private Double overallPercentage;
}