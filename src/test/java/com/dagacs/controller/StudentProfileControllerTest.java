package com.dagacs.controller;

import com.dagacs.dto.StudentProfileDTO;
import com.dagacs.dto.StudentProfileRequestDTO;
import com.dagacs.service.StudentProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentProfileService studentProfileService;

    private StudentProfileDTO profileDto() {
        return StudentProfileDTO.builder()
                .rollNumber("2201CE001").enrollmentNumber("ENR-2022-001")
                .name("Student User").gender("M")
                .personalEmail("student@test.com")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .age(20).admissionDate("2026-01-01").status("ACTIVE")
                .batchName("B1").programName("Computer Science").sectionName("A")
                .build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyProfile_student_returns200() throws Exception {
        when(studentProfileService.getMyProfile()).thenReturn(profileDto());

        mockMvc.perform(get("/api/student/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value("2201CE001"))
                .andExpect(jsonPath("$.enrollmentNumber").value("ENR-2022-001"))
                .andExpect(jsonPath("$.name").value("Student User"))
                .andExpect(jsonPath("$.batchName").value("B1"))
                .andExpect(jsonPath("$.programName").value("Computer Science"))
                .andExpect(jsonPath("$.sectionName").value("A"));
    }

    @Test
    @WithAnonymousUser
    void getMyProfile_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/student/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void getMyProfile_teacherDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyProfile_adminDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOD")
    void getMyProfile_hodDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateMyProfile_student_returns200() throws Exception {
        when(studentProfileService.updateMyProfile(any(StudentProfileRequestDTO.class)))
                .thenReturn(profileDto());

        mockMvc.perform(put("/api/student/profile")
                .contentType("application/json")
                .content("{\"fatherName\":\"New Father\",\"motherName\":\"New Mother\",\"gender\":\"F\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value("2201CE001"))
                .andExpect(jsonPath("$.name").value("Student User"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void updateMyProfile_teacherDenied_returns403() throws Exception {
        mockMvc.perform(put("/api/student/profile")
                .contentType("application/json")
                .content("{\"fatherName\":\"New Father\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateMyProfile_adminDenied_returns403() throws Exception {
        mockMvc.perform(put("/api/student/profile")
                .contentType("application/json")
                .content("{\"fatherName\":\"New Father\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void updateMyProfile_anonymous_returns401() throws Exception {
        mockMvc.perform(put("/api/student/profile")
                .contentType("application/json")
                .content("{\"fatherName\":\"New Father\"}"))
                .andExpect(status().isUnauthorized());
    }
}