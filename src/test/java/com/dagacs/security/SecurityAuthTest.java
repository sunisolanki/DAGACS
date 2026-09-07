package com.dagacs.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void login_admin_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withValidAdminToken_returns200() throws Exception {
        String token = loginToken("admin@dagacs.local", "Admin@123");
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withForgedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBkYWdhY3MubG9jYWwiLCJyb2xlcyI6IkFETUlOIn0.forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withStudentToken_returns403() throws Exception {
        String token = loginToken("student@dagacs.local", "Student@123");
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentAttendanceEndpoint_withValidStudentToken_returns401_whenNoProfileLinked() throws Exception {
        // A valid STUDENT token passes authentication and the /api/student/** matcher, but the
        // students table is empty (no seeded student profile), so identity cannot be resolved
        // and the endpoint returns 401. This mirrors teacher identity resolution semantics.
        String token = loginToken("student@dagacs.local", "Student@123");
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentAttendanceEndpoint_withValidAdminToken_returns403() throws Exception {
        // An authenticated, non-STUDENT principal is rejected by the STUDENT role requirement
        // before identity resolution, so ADMIN sees 403 (not 401).
        String token = loginToken("admin@dagacs.local", "Admin@123");
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentAttendanceEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentAttendanceEndpoint_withForgedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdHVkZW50QGRhZ2Fjcy5sb2NhbCIsInJvbGVzIjoiU1RVREVOVCJ9.forged"))
                .andExpect(status().isUnauthorized());
    }
}
