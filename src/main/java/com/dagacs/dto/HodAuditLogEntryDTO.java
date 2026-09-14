package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodAuditLogEntryDTO {

    private String studentName;

    private String rollNo;

    private String subjectName;

    private String sectionName;

    private String batchName;

    private String date;

    private String previousStatus;

    private String newStatus;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private String reason;
}