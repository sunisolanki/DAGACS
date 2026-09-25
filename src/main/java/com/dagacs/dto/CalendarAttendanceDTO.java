package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarAttendanceDTO {

    private String date;

    private Long presentCount;

    private Long absentCount;

    private Long totalRecordedCount;

    private Double percentage;
}
