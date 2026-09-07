package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only HOD identity and department scope (M6.1).
 * <p>
 * Only business identifiers are exposed - the internal database primary keys are
 * intentionally omitted. {@code email} is the unique teacher business identifier
 * and {@code departmentName} is the unique department identifier; department
 * code is informational.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodIdentityDTO {

    private String email;
    private String teacherName;
    private String designation;
    private Boolean hod;
    private String departmentName;
    private String departmentCode;
}