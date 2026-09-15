package com.dagacs.controller;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedTeacherResolver;
import com.dagacs.service.TeacherAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M9.3 backend preparation for the teacher self-service screen (the screen itself
 * ships with M9.4). The authenticated teacher always comes from the JWT security
 * context via {@link AuthenticatedTeacherResolver} — never from a client-supplied id.
 */
@RestController
@RequestMapping("/api/teacher/assignments")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;
    private final AuthenticatedTeacherResolver teacherResolver;

    public TeacherAssignmentController(TeacherAssignmentService teacherAssignmentService,
                                       AuthenticatedTeacherResolver teacherResolver) {
        this.teacherAssignmentService = teacherAssignmentService;
        this.teacherResolver = teacherResolver;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'HOD')")
    public ResponseEntity<List<TeacherAssignmentDTO>> getMyAssignments() {
        Teacher teacher = teacherResolver.resolve();
        return ResponseEntity.ok(teacherAssignmentService.getAssignmentsForTeacher(teacher.getId()));
    }
}