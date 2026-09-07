package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodSubjectAttendanceDTO {

    private String subjectCode;

    private String subjectName;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}