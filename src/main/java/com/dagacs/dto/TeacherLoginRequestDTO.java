package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login-provisioning request for a teacher (M9.5.1).
 * <p>
 * Carries the initial password (D3) and an optional login status that defaults
 * to ACTIVE. Provisioning always uses the teacher's email, so the login and the
 * teacher profile share one email by construction (D1).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherLoginRequestDTO {

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String status;
}