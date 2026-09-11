package com.dagacs.controller;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.dto.TeacherDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.TeacherAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminTeacherAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherAssignmentService teacherAssignmentService;

    private String validJson() {
        return "{\"teacherId\":1,\"subjectOfferingId\":2,\"sectionId\":3}";
    }

    private TeacherAssignmentDTO assignmentDTO() {
        return TeacherAssignmentDTO.builder()
                .id(9L)
                .teacherId(1L)
                .teacherName("Teacher One")
                .subjectOfferingId(2L)
                .sectionId(3L)
                .subjectCode("CS302")
                .subjectName("DBMS")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacherAssignment_valid_returns201() throws Exception {
        when(teacherAssignmentService.createAssignment(any())).thenReturn(assignmentDTO());

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.teacherId").value(1))
                .andExpect(jsonPath("$.subjectOfferingId").value(2))
                .andExpect(jsonPath("$.sectionId").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacherAssignment_ignoresRedundantContext_derivesServerSide() throws Exception {
        when(teacherAssignmentService.createAssignment(any())).thenReturn(assignmentDTO());

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":1,\"subjectOfferingId\":2,\"sectionId\":3,"
                                + "\"subjectId\":999,\"semesterId\":999,\"academicSessionId\":999,"
                                + "\"programId\":999,\"departmentId\":999,\"batchId\":999,"
                                + "\"studentId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllTeacherAssignments_returns200() throws Exception {
        when(teacherAssignmentService.getAllAssignments()).thenReturn(List.of(assignmentDTO()));

        mockMvc.perform(get("/api/admin/teacher-assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateTeacherAssignment_returns200() throws Exception {
        when(teacherAssignmentService.updateAssignment(eq(9L), any())).thenReturn(assignmentDTO());

        mockMvc.perform(put("/api/admin/teacher-assignments/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateTeacherAssignment_duplicate_returns409() throws Exception {
        when(teacherAssignmentService.updateAssignment(eq(9L), any()))
                .thenThrow(new AuthException("Teacher is already assigned to this subject offering and section", 409));

        mockMvc.perform(put("/api/admin/teacher-assignments/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateTeacherAssignment_missing_returns404() throws Exception {
        when(teacherAssignmentService.updateAssignment(eq(99L), any()))
                .thenThrow(new AuthException("Teacher assignment not found with ID: 99", 404));

        mockMvc.perform(put("/api/admin/teacher-assignments/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateTeacherAssignment_incompatibleSessions_returns400() throws Exception {
        when(teacherAssignmentService.updateAssignment(eq(9L), any()))
                .thenThrow(new AuthException("Academic session mismatch: the SubjectOffering belongs to a different academic session than the Section's batch", 400));

        mockMvc.perform(put("/api/admin/teacher-assignments/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteTeacherAssignment_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/teacher-assignments/9"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteTeacherAssignment_missing_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new AuthException("Teacher assignment not found with ID: 99", 404))
                .when(teacherAssignmentService).deleteAssignment(99L);

        mockMvc.perform(delete("/api/admin/teacher-assignments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacherAssignment_missingTeacherId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectOfferingId\":2,\"sectionId\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacherAssignment_missingOfferingId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":1,\"sectionId\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacherAssignment_missingSectionId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":1,\"subjectOfferingId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllTeachers_returns200() throws Exception {
        when(teacherAssignmentService.listTeachers())
                .thenReturn(List.of(TeacherDTO.builder()
                        .id(1L).email("t@dagacs.local").fullName("Teacher One")
                        .designation("Professor").status("ACTIVE").build()));

        mockMvc.perform(get("/api/admin/teachers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Teacher One"));
    }

    @Test
    @WithAnonymousUser
    void adminEndpoints_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void adminEndpoints_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}