package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update-teacher-profile request (M9.5.1).
 * <p>
 * Deliberately excludes {@code email} (immutable once a teacher exists so the
 * email-linkage invariant of D1 cannot be broken) and {@code password} (handled
 * by the dedicated login-password operation). Only profile fields are mutable.
 * All fields are optional; absent fields keep their current value.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherUpdateRequestDTO {

    private String fullName;

    private String phone;

    private String designation;

    private Long departmentId;
}