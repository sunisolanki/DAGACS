package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status-only payload for {@code PATCH /api/admin/students/{id}/status}.
 * Carries exactly one field: the new ACTIVE/INACTIVE status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatusDTO {

    @NotBlank(message = "Status is required")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;
}