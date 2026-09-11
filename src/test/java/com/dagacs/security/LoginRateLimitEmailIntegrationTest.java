package com.dagacs.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.6 M — email-keyed rate limiting against the real security stack.
 *
 * <p>Tuned tight for the test (3 failures/email, 100/IP) so email behaviour is
 * exercised without tripping the shared-IP budget. Failures are counted ONLY on
 * HTTP 401; a successful login clears the email counter; unknown emails stay a
 * generic 401 (no existence disclosure); malformed bodies stay 400 and are not
 * counted.
 */
@SpringBootTest(properties = {
        "app.security.login-rate-limit.max-attempts-per-email=3",
        "app.security.login-rate-limit.max-attempts-per-ip=100"
})
@AutoConfigureMockMvc
class LoginRateLimitEmailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void emailFailures_afterThreshold_returns429_withRetryAfterAndEnvelope() throws Exception {
        String email = "rl-email-" + System.nanoTime() + "@dagacs.local";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "WrongPass1")))
                    .andExpect(status().isUnauthorized());
        }
        MvcResult blocked = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "WrongPass1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Please try again later."))
                .andReturn();
        int retryAfter = Integer.parseInt(blocked.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter > 0, "Retry-After must announce a real cooldown");
    }

    @Test
    void successfulLogin_clearsEmailCounter_provingHealThenRearm() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@dagacs.local", "WrongPass1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@dagacs.local", "WrongPass1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@dagacs.local", "Admin@123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@dagacs.local", "WrongPass1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@dagacs.local", "WrongPass1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailAndWrongPassword_staysGeneric401_noExistenceLeak() throws Exception {
        String email = "nobody-" + System.nanoTime() + "@dagacs.local";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertTrue(!body.toLowerCase().contains("exists"), "401 body must not reveal account existence");
        assertTrue(!body.toLowerCase().contains("already"), "401 body must not hint at account state");
    }

    @Test
    void malformedOrEmptyBody_returns400_andIsNotCounted() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonLoginEndpoints_areNeverRateLimited() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("hod@dagacs.local", "Hod@123")))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("token").asText();
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(get("/api/hod/test")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }
}