package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One matrix column = exactly one AttendanceSession, identified by
 * (date, lecturePeriod). Sessions sharing a date remain distinct columns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentWiseColumnDTO {

    private Long sessionId;

    private String date;

    private String lecturePeriod;
}