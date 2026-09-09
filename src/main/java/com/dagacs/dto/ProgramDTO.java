package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramDTO {

    private Long id;

    @NotBlank(message = "Program name is required")
    @Size(max = 100, message = "Program name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Program code is required")
    @Size(max = 20, message = "Program code must not exceed 20 characters")
    private String code;

    /** Informational program duration (e.g. "4 years"). Display-only; no business logic consumes it. */
    @NotBlank(message = "Duration is required")
    @Size(max = 50, message = "Duration must not exceed 50 characters")
    private String duration;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private DepartmentDTO department;

    private Long departmentId;

    private java.util.List<AcademicSessionDTO> academicSessions = new java.util.ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}