package com.dagacs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M9.3 / Phase-2 request payload for a teaching assignment.
 *
 * <p>Carries exactly the relational identities: {@code teacherId},
 * {@code subjectOfferingId} and — in Section mode — {@code sectionId}, or — in
 * batch (batch-level) mode for zero-section batches — {@code batchId}. Exactly
 * one of {@code sectionId}/{@code batchId} is required; the invariant is
 * enforced at the service boundary. All academic context (Subject, Semester,
 * AcademicSession, Program, Department, Batch) is derived server-side and is
 * never accepted from the client.</p>
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

    private Long sectionId;

    private Long batchId;
}