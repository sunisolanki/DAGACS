package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only DTO for the authenticated student's own academic profile (M5.1).
 * <p>
 * Returned only for the student resolved from the JWT security context; no
 * studentId is ever accepted from the client.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDTO {

    private String rollNumber;
    private String enrollmentNumber;
    private String email;
    private String name;
    private String gender;
    private String fatherName;
    private String motherName;
    private String photoUrl;
    private Integer age;
    private String admissionDate;
    private String status;
    private String batchName;
    private String programName;
    private String sectionName;
}