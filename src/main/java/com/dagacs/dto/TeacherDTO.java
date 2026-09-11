package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M9.3 response DTO for an existing {@code Teacher} row, used by the admin
 * Master Data teacher-assignment screens and the {@code GET /api/admin/teachers}
 * listing. Lists all persisted teacher rows (no provisioning).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDTO {

    private Long id;

    private String email;

    private String fullName;

    private String designation;

    private String status;

    private Long departmentId;

    private String departmentName;
}