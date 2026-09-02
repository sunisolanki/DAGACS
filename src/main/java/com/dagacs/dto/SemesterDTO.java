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
public class SemesterDTO {

    private Long id;

    @NotBlank(message = "Semester name is required")
    @Size(max = 50, message = "Semester name must not exceed 50 characters")
    private String name;

    @NotBlank(message = "Semester code is required")
    @Size(max = 20, message = "Semester code must not exceed 20 characters")
    private String code;

    @NotNull(message = "Year is required")
    private Integer year;

    private AcademicSessionDTO academicSession;

    private java.util.List<SectionDTO> sections = new java.util.ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}