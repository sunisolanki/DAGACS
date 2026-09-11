package com.dagacs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M9.2 request payload for a {@code SubjectOffering} (academic applicability).
 *
 * <p>The request intentionally carries only the two relational identities. The
 * academic context (Program / AcademicSession / Department) is always derived
 * server-side through {@code Semester -> AcademicSession -> Program ->
 * Department} and is never accepted from the client.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectOfferingRequestDTO {

    @NotNull(message = "Subject is required")
    private Long subjectId;

    @NotNull(message = "Semester is required")
    private Long semesterId;
}