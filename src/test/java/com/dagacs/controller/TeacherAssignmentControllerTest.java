package com.dagacs.controller;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedTeacherResolver;
import com.dagacs.service.TeacherAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TeacherAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherAssignmentService teacherAssignmentService;

    @MockBean
    private AuthenticatedTeacherResolver teacherResolver;

    private TeacherAssignmentDTO assignmentDTO() {
        return TeacherAssignmentDTO.builder()
                .id(9L)
                .teacherId(1L)
                .teacherName("Teacher One")
                .subjectOfferingId(2L)
                .subjectCode("CS302")
                .subjectName("DBMS")
                .sectionId(3L)
                .sectionCode("CSE-A")
                .build();
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void getMyAssignments_returnsOnlyAuthenticatedTeachersRows() throws Exception {
        Teacher teacher = Teacher.builder().id(1L).email("t1@dagacs.local").fullName("Teacher One")
                .designation("Professor").status("ACTIVE").build();
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(teacherAssignmentService.getAssignmentsForTeacher(1L)).thenReturn(List.of(assignmentDTO()));

        mockMvc.perform(get("/api/teacher/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].teacherId").value(1))
                .andExpect(jsonPath("$[0].subjectCode").value("CS302"));
    }

    @Test
    @WithAnonymousUser
    void getMyAssignments_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/assignments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyAssignments_student_returns403() throws Exception {
        mockMvc.perform(get("/api/teacher/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyAssignments_admin_returns403() throws Exception {
        mockMvc.perform(get("/api/teacher/assignments"))
                .andExpect(status().isForbidden());
    }
}