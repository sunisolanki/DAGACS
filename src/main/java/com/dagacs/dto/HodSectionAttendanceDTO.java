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

    /** Academic context; null on the frozen department-wide query. */
    private String academicSessionName;

    private String programName;

    private String semesterName;

    private Long studentCount;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}