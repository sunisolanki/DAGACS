package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Activation/deactivation payload for a linked teacher login (M9.5.1).
 * Mirrors {@link StudentStatusDTO}; toggles only the login ({@code users.status}),
 * never the teacher profile status (D4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherLoginStatusDTO {

    @NotBlank(message = "Status is required")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;
}