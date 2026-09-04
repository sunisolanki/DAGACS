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
public class SectionDTO {

    private Long id;

    @NotBlank(message = "Section code is required")
    @Size(max = 20, message = "Section code must not exceed 20 characters")
    private String sectionCode;

    @NotBlank(message = "Section name is required")
    @Size(max = 50, message = "Section name must not exceed 50 characters")
    private String name;

    @NotNull(message = "Max capacity is required")
    private Integer maxCapacity;

    @NotNull(message = "Batch is required")
    private Long batchId;

    private BatchDTO batch;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}