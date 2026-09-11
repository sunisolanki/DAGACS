package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M9.2 response DTO for a {@code SubjectOffering}.
 *
 * <p>The nested {@code subject} and {@code semester} carry the display
 * identity needed by the admin UI; the academic context resolves through the
 * semester's nested {@code academicSession} plus program/department reference
 * data. No redundant context identifiers are present.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectOfferingDTO {

    private Long id;

    private Long subjectId;

    private SubjectDTO subject;

    private Long semesterId;

    private SemesterDTO semester;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}