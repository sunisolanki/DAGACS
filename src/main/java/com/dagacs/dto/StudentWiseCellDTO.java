package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One cell of the student-wise matrix. Maps to exactly one AttendanceRecord in
 * exactly one session (the {@code sessionId} in {@link StudentWiseColumnDTO}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentWiseCellDTO {

    private Long sessionId;

    /** PRESENT or ABSENT. */
    private String status;

    private Boolean isPresent;
}