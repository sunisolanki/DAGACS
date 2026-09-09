package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class SubjectDTO {

    private Long id;

    @NotBlank(message = "Subject code is required")
    @Size(max = 20, message = "Subject code must not exceed 20 characters")
    private String code;

    @NotBlank(message = "Subject name is required")
    @Size(max = 100, message = "Subject name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotBlank(message = "Credit hours is required")
    @Pattern(regexp = "\\d+(\\.\\d+)?", message = "Credit hours must be a positive number")
    private String creditHours;

    @NotNull(message = "Department is required")
    private Long departmentId;

    private DepartmentDTO department;

    @NotNull(message = "Status is required")
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}