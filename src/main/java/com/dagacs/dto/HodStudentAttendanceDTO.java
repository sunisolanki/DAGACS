package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodStudentAttendanceDTO {

    private String rollNumber;

    private String studentName;

    private String sectionName;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}