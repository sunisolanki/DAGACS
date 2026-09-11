package com.dagacs.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.6 H1: with the diagnostics flag off (the production posture), even a valid
 * role principal gets 404 instead of an identity echo. The URL matcher and
 * @PreAuthorize checks still apply first, so an unauthorized caller still sees
 * 401/403, never a diagnostics leak.
 */
@SpringBootTest(properties = "app.diagnostics.enabled=false")
@AutoConfigureMockMvc
class RoleEchoEndpointDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEcho_whenDiagnosticsDisabled_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherEcho_whenDiagnosticsDisabled_returns404() throws Exception {
        mockMvc.perform(get("/api/teacher/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentEcho_whenDiagnosticsDisabled_returns404() throws Exception {
        mockMvc.perform(get("/api/student/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void studentEcho_whenDiagnosticsDisabled_wrongRoleStill403() throws Exception {
        mockMvc.perform(get("/api/student/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEcho_whenDiagnosticsDisabled_anonymousStill401() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }
}