package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAuditLogDTO {

    private Long id;

    @NotNull(message = "Attendance record ID is required")
    private Long attendanceId;

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotBlank(message = "Roll number snapshot is required")
    @Size(max = 50, message = "Roll number must not exceed 50 characters")
    private String rollNo;

    @NotBlank(message = "Student name snapshot is required")
    @Size(max = 100, message = "Student name must not exceed 100 characters")
    private String studentName;

    @NotBlank(message = "Subject name is required")
    @Size(max = 100, message = "Subject name must not exceed 100 characters")
    private String subject;

    @NotBlank(message = "Subject name snapshot is required")
    @Size(max = 100, message = "Subject name snapshot must not exceed 100 characters")
    private String subjectName;

    @NotBlank(message = "Section name is required")
    @Size(max = 100, message = "Section name must not exceed 100 characters")
    private String section;

    @NotBlank(message = "Section name snapshot is required")
    @Size(max = 100, message = "Section name snapshot must not exceed 100 characters")
    private String sectionName;

    private String batch;

    private String batchName;

    @NotBlank(message = "Date is required")
    @Size(max = 20, message = "Date must not exceed 20 characters")
    private String date;

    @NotBlank(message = "Previous status is required")
    @Size(max = 20, message = "Previous status must not exceed 20 characters")
    private String previousStatus;

    @NotBlank(message = "New status is required")
    @Size(max = 20, message = "New status must not exceed 20 characters")
    private String newStatus;

    @NotBlank(message = "Updated by is required")
    @Size(max = 100, message = "Updated by must not exceed 100 characters")
    private String updatedBy;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    private LocalDateTime updatedAt;
}
