package com.dagacs.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.6 H1: the four role-echo diagnostics endpoints are now fenced by method
 * security (@PreAuthorize) on top of the URL matchers. A matching role gets the
 * echo payload, a wrong role gets 403, and an anonymous caller gets 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleEchoEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEcho_withAdminRole_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "HOD")
    void adminEcho_withHodRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEcho_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HOD")
    void hodEcho_withHodRole_returns200() throws Exception {
        mockMvc.perform(get("/api/hod/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HOD"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void hodEcho_withAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/hod/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherEcho_withTeacherRole_returns200() throws Exception {
        mockMvc.perform(get("/api/teacher/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void teacherEcho_withStudentRole_returns403() throws Exception {
        mockMvc.perform(get("/api/teacher/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentEcho_withStudentRole_returns200() throws Exception {
        mockMvc.perform(get("/api/student/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void studentEcho_withAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/student/test"))
                .andExpect(status().isForbidden());
    }
}