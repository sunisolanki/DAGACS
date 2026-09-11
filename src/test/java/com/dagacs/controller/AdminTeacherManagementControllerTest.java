package com.dagacs.controller;

import com.dagacs.dto.TeacherManagementDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.TeacherManagementService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminTeacherManagementControllerTest {

    private static final String CREATE_BODY = "{"
            + "\"email\":\"prof.new@dagacs.local\","
            + "\"fullName\":\"Prof New\","
            + "\"phone\":\"1234567890\","
            + "\"designation\":\"Professor\","
            + "\"departmentId\":1,"
            + "\"password\":\"TempPass#1\""
            + "}";

    private static final String MINIMAL_BODY = "{"
            + "\"email\":\"prof.new@dagacs.local\","
            + "\"fullName\":\"Prof New\","
            + "\"password\":\"TempPass#1\""
            + "}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherManagementService teacherManagementService;

    private TeacherManagementDTO dto(long id, String email, String fullName, String status) {
        return TeacherManagementDTO.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .phone("1234567890")
                .designation("Professor")
                .status(status)
                .departmentId(1L)
                .departmentName("Computer Science")
                .isHod(false)
                .loginLinked(true)
                .loginStatus("ACTIVE")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacher_valid_returns201WithoutPassword() throws Exception {
        when(teacherManagementService.createTeacher(any()))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE"));

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("prof.new@dagacs.local"))
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listTeachers_returns200() throws Exception {
        when(teacherManagementService.listTeachers())
                .thenReturn(List.of(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE")));

        mockMvc.perform(get("/api/admin/teacher-management/teachers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].loginStatus").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTeacher_returns200() throws Exception {
        when(teacherManagementService.getTeacher(1L))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE"));

        mockMvc.perform(get("/api/admin/teacher-management/teachers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Prof New"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTeacher_unknown_returns404() throws Exception {
        when(teacherManagementService.getTeacher(99L))
                .thenThrow(new AuthException("Teacher not found with ID: 99", 404));

        mockMvc.perform(get("/api/admin/teacher-management/teachers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateTeacher_returns200() throws Exception {
        when(teacherManagementService.updateTeacher(eq(1L), any()))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New Updated", "ACTIVE"));

        mockMvc.perform(put("/api/admin/teacher-management/teachers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Prof New Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Prof New Updated"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setTeacherStatus_returns200() throws Exception {
        when(teacherManagementService.setTeacherStatus(eq(1L), eq("INACTIVE")))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "INACTIVE"));

        mockMvc.perform(patch("/api/admin/teacher-management/teachers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        verify(teacherManagementService).setTeacherStatus(1L, "INACTIVE");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void provisionLogin_returns201() throws Exception {
        when(teacherManagementService.provisionLogin(eq(1L), any()))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE"));

        mockMvc.perform(post("/api/admin/teacher-management/teachers/1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"TempPass#1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginLinked").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setLoginStatus_returns200() throws Exception {
        when(teacherManagementService.setLoginStatus(eq(1L), eq("INACTIVE")))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE"));

        mockMvc.perform(patch("/api/admin/teacher-management/teachers/1/login/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginStatus").value("ACTIVE"));
        verify(teacherManagementService).setLoginStatus(1L, "INACTIVE");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setLoginPassword_returns200() throws Exception {
        when(teacherManagementService.setLoginPassword(eq(1L), eq("NewPass#123")))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE"));

        mockMvc.perform(put("/api/admin/teacher-management/teachers/1/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass#123\"}"))
                .andExpect(status().isOk());
        verify(teacherManagementService).setLoginPassword(1L, "NewPass#123");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacher_minimalBody_passesValidation() throws Exception {
        when(teacherManagementService.createTeacher(any()))
                .thenReturn(dto(1L, "prof.new@dagacs.local", "Prof New", "ACTIVE"));

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_BODY))
                .andExpect(status().isCreated());
        verify(teacherManagementService).createTeacher(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacher_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"fullName\":\"Prof\",\"password\":\"TempPass#1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacher_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"fullName\":\"Prof\",\"password\":\"TempPass#1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTeacher_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prof.new@dagacs.local\",\"fullName\":\"Prof\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void provisionLogin_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-management/teachers/1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setTeacherStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/teacher-management/teachers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createTeacher_student_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void listTeachers_teacher_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/teacher-management/teachers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void createTeacher_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }
}