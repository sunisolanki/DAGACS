package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceMarkItemDTO {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "PRESENT|ABSENT", message = "Status must be PRESENT or ABSENT")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;
}
