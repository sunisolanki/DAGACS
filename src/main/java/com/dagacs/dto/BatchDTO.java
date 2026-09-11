package com.dagacs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for an admission cohort (Batch).
 * <p>
 * A Batch is the student cohort admitted in a given year for a Program.
 * Program context is relational through {@link #academicSessionId} (Batch →
 * AcademicSession → Program); {@link #program} is a derived denormalized
 * snapshot of that program name and is never accepted from client input.
 * </p>
 */
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

    /** Admission year of the cohort. */
    @NotNull(message = "Year is required")
    @Min(value = 2000, message = "Admission year must be at least 2000")
    @Max(value = 2100, message = "Admission year must not exceed 2100")
    private Integer year;

    @NotNull(message = "AcademicSession is required")
    private Long academicSessionId;

    private AcademicSessionDTO academicSession;

    /** Derived program name (snapshot of the academic session's program). */
    private String program; // program name string, derived from academic session's program for consistency

    @NotNull(message = "Max capacity is required")
    private Integer maxCapacity;

    private java.util.List<SectionDTO> sections = new java.util.ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}