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
public class AcademicSessionDTO {

    private Long id;

    @NotBlank(message = "Session name is required")
    @Size(max = 50, message = "Session name must not exceed 50 characters")
    private String name;

    @NotBlank(message = "Session code is required")
    @Size(max = 20, message = "Session code must not exceed 20 characters")
    private String code;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Program is required")
    private Long programId;

    private ProgramDTO program;

    private java.util.List<SemesterDTO> semesters = new java.util.ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}