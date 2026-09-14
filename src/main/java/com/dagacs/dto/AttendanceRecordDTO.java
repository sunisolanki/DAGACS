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
public class AttendanceRecordDTO {

    private Long id;

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Subject ID is required")
    private Long subjectId;

    private Long sectionId;

    private Long batchId;

    @NotBlank(message = "Status is required")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;

    @NotBlank(message = "Lecture period is required")
    @Size(max = 50, message = "Lecture period must not exceed 50 characters")
    private String lecturePeriod;

    @NotBlank(message = "Date is required")
    @Size(max = 20, message = "Date must not exceed 20 characters")
    private String date;

    @NotNull(message = "isPresent flag is required")
    private Boolean isPresent;

    private Long sessionId;

    private Long markedById;

    private String markedByName;

    private LocalDateTime createdAt;
}
