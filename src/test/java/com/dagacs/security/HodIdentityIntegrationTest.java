package com.dagacs.security;

import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.TeacherRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance test for the M6.1 HOD identity endpoint
 * {@code GET /api/hod/me}.
 * <p>
 * Drives the actual controller/service/resolver against the real database and
 * real Spring Security. Asserts the exact HTTP semantics agreed in the M6.1 plan:
 * a correctly-configured HOD gets 200; identity-resolution failures (no linked
 * teacher, teacher not HOD, HOD without department) return 401; other roles and
 * anonymous get 403/401 via the role matcher. All rows are created inside the
 * test transaction and rolled back.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodIdentityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Department createDepartment() {
        return departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private void createHodTeacher(Department dept, boolean isHod) {
        teacherRepository.save(Teacher.builder()
                .email("hod@dagacs.local").password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(dept).isHod(isHod)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void hodMe_validConfiguredHod_returns200WithIdentity() throws Exception {
        Department dept = createDepartment();
        createHodTeacher(dept, true);
        String token = loginToken("hod@dagacs.local", "Hod@123");

        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hod").value(true))
                .andExpect(jsonPath("$.email").value("hod@dagacs.local"))
                .andExpect(jsonPath("$.teacherName").value("HOD User"))
                .andExpect(jsonPath("$.departmentName").value(dept.getName()))
                .andExpect(jsonPath("$.departmentCode").value("D"));
    }

    @Test
    void hodMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/hod/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void hodMe_adminToken_returns403() throws Exception {
        String token = loginToken("admin@dagacs.local", "Admin@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodMe_teacherToken_returns403() throws Exception {
        String token = loginToken("teacher@dagacs.local", "Teacher@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodMe_studentToken_returns403() throws Exception {
        String token = loginToken("student@dagacs.local", "Student@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodMe_hodRoleNoTeacherRow_returns401() throws Exception {
        // No teacher row exists at all for hod@dagacs.local -> 401 (resolver).
        String token = loginToken("hod@dagacs.local", "Hod@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hodMe_teacherNotHod_returns401() throws Exception {
        Department dept = createDepartment();
        createHodTeacher(dept, false);
        String token = loginToken("hod@dagacs.local", "Hod@123");

        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hodMe_hodWithoutDepartment_returns401() throws Exception {
        createHodTeacher(null, true);
        String token = loginToken("hod@dagacs.local", "Hod@123");

        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hodMe_noInternalIdsExposed() throws Exception {
        Department dept = createDepartment();
        createHodTeacher(dept, true);
        String token = loginToken("hod@dagacs.local", "Hod@123");

        MvcResult result = mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        // Internal DB primary keys must not be exposed (Correction 3).
        assertEquals(null, node.get("teacherId"));
        assertEquals(null, node.get("departmentId"));
    }
}