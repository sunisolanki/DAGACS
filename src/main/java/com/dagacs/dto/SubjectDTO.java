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

    @NotNull(message = "Credit hours is required")
    private String creditHours;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    private String department;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}