package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reset-password payload for a linked teacher login (M9.5.1).
 * The new password is BCrypt-encoded by {@code AccountProvisioningService};
 * it is never returned or logged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherPasswordUpdateRequestDTO {

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}