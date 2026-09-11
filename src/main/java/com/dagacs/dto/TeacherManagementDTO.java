package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Rich teacher-management response (M9.5.1) surfaced by
 * {@code /api/admin/teacher-management/teachers}.
 * <p>
 * Extends the frozen {@link com.dagacs.entity.Teacher} projection with the two
 * login-lifecycle facts required by the M9.5 admin UI: whether a login account
 * is linked and, if so, its ACTIVE/INACTIVE status. Never exposes the password.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherManagementDTO {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String designation;
    private String status;
    private Long departmentId;
    private String departmentName;
    private Boolean isHod;
    private Boolean loginLinked;
    private String loginStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}