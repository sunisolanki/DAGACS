package com.dagacs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create-teacher profile request (M9.5.1).
 * <p>
 * The password is required at profile creation so the admin can immediately
 * hand the teacher an initial password (D3). It is validated by length here and
 * BCrypt-encoded in {@code AccountProvisioningService}; it is never serialized
 * in any response.
 * </p>
 * The optional {@code status} defaults to ACTIVE; the optional {@code phone} and
 * {@code designation} collapse to {@code ""} in the service (matching the
 * students surface).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherCreateRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phone;

    private String designation;

    private Long departmentId;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String status;
}