package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSessionUpdateRequestDTO {

    @NotBlank(message = "Lecture period is required")
    @Size(max = 50, message = "Lecture period must not exceed 50 characters")
    private String lecturePeriod;

    @NotBlank(message = "Date is required")
    @Size(max = 20, message = "Date must not exceed 20 characters")
    private String date;

    @NotBlank(message = "Status is required")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;
}
