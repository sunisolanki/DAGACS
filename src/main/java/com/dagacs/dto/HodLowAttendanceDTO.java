package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodLowAttendanceDTO {

    private String rollNumber;

    /** Nullable: enrolment number is optional on the Student profile. */
    private String enrollmentNumber;

    private String studentName;

    private String sectionName;

    private String batchName;

    /** Academic context; null on the frozen department-wide query. */
    private String academicSessionName;

    private String programName;

    private String semesterName;

    private Long presentCount;

    private Long totalRecordedCount;

    private Double percentage;
}