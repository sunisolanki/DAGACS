package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Forced first-login password change payload (M10A).
 * <p>
 * The identity comes exclusively from the JWT in the Authorization header - the
 * body never carries an email/rollNumber/studentId. The current password must
 * verify against the persisted BCrypt hash before the new password is applied.
 * </p>
 */
@Data
public class StudentChangePasswordRequestDTO {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}