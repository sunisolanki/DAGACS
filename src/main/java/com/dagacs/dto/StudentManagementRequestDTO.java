package com.dagacs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create/update payload for the M5.2 Admin student master-management API.
 * Mirrors the existing master-data DTO conventions (Lombok, Bean Validation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentManagementRequestDTO {

    @NotBlank(message = "Roll number is required")
    @Size(max = 50, message = "Roll number must not exceed 50 characters")
    private String rollNumber;

    @Size(max = 120, message = "Email must not exceed 120 characters")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 10, message = "Gender must not exceed 10 characters")
    private String gender;

    @Size(max = 100, message = "Father name must not exceed 100 characters")
    private String fatherName;

    @Size(max = 100, message = "Mother name must not exceed 100 characters")
    private String motherName;

    @Size(max = 500, message = "Photo URL must not exceed 500 characters")
    private String photoUrl;

    @Size(max = 50, message = "Enrollment number must not exceed 50 characters")
    private String enrollmentNumber;

    private Integer age;

    @Size(max = 20, message = "Admission date must not exceed 20 characters")
    private String admissionDate;

    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;

    @NotNull(message = "Program is required")
    private Long programId;

    @NotNull(message = "Batch is required")
    private Long batchId;

    private Long sectionId;
}