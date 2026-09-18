package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for the M5.2 Admin student master-management API.
 * Resolved program/batch/section names are included for direct display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentManagementDTO {

    private Long id;
    private String rollNumber;
    private String email;
    private String name;
    private String gender;
    private String fatherName;
    private String motherName;
    private String photoUrl;
    private String enrollmentNumber;
    private Integer age;
    private String admissionDate;
    private String status;

    private Long programId;
    private String programName;
    private Long batchId;
    private String batchName;
    private Long sectionId;
    private String sectionName;
    private Long academicSessionId;
    private String academicSessionName;

    private boolean loginLinked;
    private String loginStatus;
    private boolean mustChangePassword;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String temporaryPassword;
    private String credentialDownloadId;
}