package com.dagacs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ADMIN request to designate or clear HOD status for a teacher (M6.1).
 * <p>
 * {@code designated=true} requires {@code departmentId}; {@code designated=false}
 * clears the HOD designation (the teacher keeps its department association).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodDesignationRequestDTO {

    @NotNull(message = "designated must be true or false")
    private Boolean designated;

    private Long departmentId;
}