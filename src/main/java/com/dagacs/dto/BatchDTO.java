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
public class BatchDTO {

    private Long id;

    @NotBlank(message = "Batch code is required")
    @Size(max = 20, message = "Batch code must not exceed 20 characters")
    private String batchCode;

    @NotBlank(message = "Batch name is required")
    @Size(max = 100, message = "Batch name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Year is required")
    private Integer year;

    private AcademicSessionDTO academicSession;

    private String program; // program name string for simplicity

    @NotNull(message = "Max capacity is required")
    private Integer maxCapacity;

    private java.util.List<SectionDTO> sections = new java.util.ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}