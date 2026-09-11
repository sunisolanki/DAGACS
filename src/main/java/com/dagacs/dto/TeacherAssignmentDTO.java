package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M9.3 response DTO for a teaching assignment.
 *
 * <p>Carries the three relational identities ({@code teacherId},
 * {@code subjectOfferingId}, {@code sectionId}) plus the fully derived
 * display context: Subject / Semester / AcademicSession / Program /
 * Department resolve through the SubjectOffering, while Batch resolves
 * through the Section. This is exactly what the admin Master Data screen
 * and the M9.4 teacher self-service screen render.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherAssignmentDTO {

    private Long id;

    private Long teacherId;

    private String teacherName;

    private String teacherEmail;

    private Long subjectOfferingId;

    private Long subjectId;

    private String subjectCode;

    private String subjectName;

    private Long semesterId;

    private String semesterName;

    private Long sessionId;

    private String sessionName;

    private Long programId;

    private String programName;

    private Long departmentId;

    private String departmentName;

    private Long sectionId;

    private String sectionCode;

    private String sectionName;

    private Long batchId;

    private String batchCode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}