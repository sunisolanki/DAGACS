package com.dagacs.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.6 M — IP-keyed rate limiting against the real security stack.
 *
 * <p>Tuned tight for the test (100 failures/email, 3/IP) so the IP key is
 * exercised without tripping per-email budgets. Each 401 from one source adds to
 * that source's window; the fourth attempt is short-circuited with 429 even when
 * the presented credentials are actually correct. Different source IPs share
 * nothing, and X-Forwarded-For is ignored by default (spoofed header cannot
 * bypass the limiter).
 */
@SpringBootTest(properties = {
        "app.security.login-rate-limit.max-attempts-per-email=100",
        "app.security.login-rate-limit.max-attempts-per-ip=3"
})
@AutoConfigureMockMvc
class LoginRateLimitIpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String ATTACKER_IP_1 = "203.0.113.10";
    private static final String ATTACKER_IP_2 = "203.0.113.11";
    private static final String ATTACKER_IP_3 = "203.0.113.12";
    private static final String OTHER_IP = "198.51.100.20";
    private static final String SPOOF_IP = "10.9.9.9";

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void ipFailures_afterThreshold_return429_andShortCircuitEvenCorrectCredentials() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        for (String tag : new String[]{"a", "b", "c"}) {
            mockMvc.perform(post("/api/auth/login")
                            .remoteAddress(ATTACKER_IP_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("ip-" + tag + "-" + suffix + "@dagacs.local", "WrongPass1")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .remoteAddress(ATTACKER_IP_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@dagacs.local", "Admin@123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void differentSourceIp_hasItsOwnBudget() throws Exception {
        String email = "ip-diff-" + System.nanoTime() + "@dagacs.local";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .remoteAddress(ATTACKER_IP_2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "WrongPass1")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .remoteAddress(ATTACKER_IP_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "WrongPass1")))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/auth/login")
                        .remoteAddress(OTHER_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "WrongPass1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spoofedForwardedHeader_doesNotBypassTheLimiter() throws Exception {
        String email = "ip-spoof-" + System.nanoTime() + "@dagacs.local";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .remoteAddress(ATTACKER_IP_3)
                            .header("X-Forwarded-For", SPOOF_IP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, "WrongPass1")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .remoteAddress(ATTACKER_IP_3)
                        .header("X-Forwarded-For", SPOOF_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "WrongPass1")))
                .andExpect(status().isTooManyRequests());
    }
}