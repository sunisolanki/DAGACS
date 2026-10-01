package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase 3: one student's attendance detail inside the currently selected HOD
 * academic context.
 *
 * <p>The student is only ever reachable through a context that already belongs
 * to the authenticated HOD's department, and the student must itself belong to
 * that exact context. Changing the student id in the URL therefore yields 403
 * with no data, not another student's record.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HodStudentAttendanceDetailDTO {

    private HodAttendanceContextDTO context;

    private Long studentId;

    private String enrollmentNumber;

    private String rollNumber;

    private String studentName;

    private String programName;

    private String semesterName;

    private String sectionName;

    private List<HodStudentSubjectAttendanceDTO> subjects;

    private Long totalPresent;

    private Long totalClasses;

    private Double overallPercentage;
}
