package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login-provisioning request for a student (M9.5.2).
 * <p>
 * Carries the initial password (D3) and an optional login status that defaults
 * to ACTIVE. Provisioning always uses the student's email, so the login and the
 * student profile share one email by construction (D1).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLoginRequestDTO {

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String status;
}