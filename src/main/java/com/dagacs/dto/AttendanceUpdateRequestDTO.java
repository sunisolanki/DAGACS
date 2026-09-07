package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
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
public class AttendanceUpdateRequestDTO {

    @NotBlank(message = "New status is required")
    @Pattern(regexp = "PRESENT|ABSENT", message = "Status must be PRESENT or ABSENT")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String newStatus;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
