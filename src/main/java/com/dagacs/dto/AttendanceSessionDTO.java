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
public class AttendanceSessionDTO {

    private Long id;

    @NotNull(message = "Subject ID is required")
    private Long subjectId;

    private String subjectName;

    private Long sectionId;

    private String sectionName;

    private Long batchId;

    private String batchName;

    private Long teacherId;

    @NotBlank(message = "Lecture period is required")
    @Size(max = 50, message = "Lecture period must not exceed 50 characters")
    private String lecturePeriod;

    @NotBlank(message = "Date is required")
    @Size(max = 20, message = "Date must not exceed 20 characters")
    private String date;

    @NotBlank(message = "Status is required")
    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;

    private LocalDateTime createdAt;
}
