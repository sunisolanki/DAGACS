package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodSectionAttendanceDTO {

    private String sectionName;

    private String sectionCode;

    private Long studentCount;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}