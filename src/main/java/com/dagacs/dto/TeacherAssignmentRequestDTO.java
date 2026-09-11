package com.dagacs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M9.3 request payload for a teaching assignment.
 *
 * <p>Carries exactly the three relational identities: {@code teacherId},
 * {@code subjectOfferingId} and {@code sectionId} — nothing more. All academic
 * context (Subject, Semester, AcademicSession, Program, Department, Batch) is
 * derived server-side and is never accepted from the client.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherAssignmentRequestDTO {

    @NotNull(message = "Teacher is required")
    private Long teacherId;

    @NotNull(message = "Subject offering is required")
    private Long subjectOfferingId;

    @NotNull(message = "Section is required")
    private Long sectionId;
}