package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3: one student x subject cell of the HOD attendance matrix.
 *
 * <p>Attendance semantics are the application's unchanged ones: {@code present}
 * counts attendance records with {@code is_present = true}, {@code total} counts
 * that student's attendance records for the subject, and {@code percentage} is
 * {@code present / total * 100} with a <b>null</b> result when {@code total} is
 * 0.</p>
 *
 * <p><b>A subject with no classes is never fabricated.</b> It is reported as
 * {@code present = 0, total = 0, percentage = null} - the established
 * representation of "no class conducted yet" - and the client renders
 * {@code 0 / 0} with an explicit {@code N/A} percentage.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAttendanceMatrixCellDTO {

    private Long subjectId;

    private Long present;

    private Long total;

    private Double percentage;
}
