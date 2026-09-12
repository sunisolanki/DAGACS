package com.dagacs.security;

import com.dagacs.entity.Department;
import com.dagacs.entity.Role;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import com.dagacs.service.AccountProvisioningService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.8 real-JWT, real-MySQL acceptance test for HOD login-role parity.
 * <p>
 * Drives the actual production designation path
 * {@code PATCH /api/admin/teachers/{teacherId}/hod} and proves the linked
 * {@link User} role is synchronized with {@code Teacher.isHod}: designation
 * promotes an ACTIVE login to HOD, clearing restores TEACHER, an inactive
 * account is never escalated (D1), and clearing self-heals a drifted
 * {@code isHod=false / role=HOD} state (D2). A teacher with no login is
 * designated without a fabricated account or 500 (D3).
 * </p>
 * <p>
 * The primary test never assigns ROLE_HOD manually; the only manual HOD role is
 * the D2 drift fixture, which reproduces the recorded defect state on purpose.
 * All rows are created inside the test transaction and rolled back.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodRoleParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccountProvisioningService accountProvisioningService;

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

    private Teacher createTeacher(String email, String status) {
        return teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("Prof " + email)
                .phone("").designation("Professor").status(status)
                .avatarUrl("").isHod(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String adminToken() throws Exception {
        return loginToken("admin@dagacs.local", "Admin@123");
    }

    private void designate(Long teacherId, String token, Long departmentId) throws Exception {
        mockMvc.perform(patch("/api/admin/teachers/" + teacherId + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + departmentId + "}"))
                .andExpect(status().isOk());
    }

    private void clearHod(Long teacherId, String token) throws Exception {
        mockMvc.perform(patch("/api/admin/teachers/" + teacherId + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": false}"))
                .andExpect(status().isOk());
    }

    @Test
    void designateAndClear_endToEndRoleParity() throws Exception {
        Department dept = createDepartment();
        String email = "hodcand-" + System.nanoTime() + "@dagacs.local";
        Teacher teacher = createTeacher(email, "ACTIVE");
        accountProvisioningService.provisionLogin(email, "Prof " + email, "Teacher@123", "TEACHER", "ACTIVE");

        // Before designation: an ordinary provisioned TEACHER cannot reach HOD.
        String teacherToken = loginToken(email, "Teacher@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // ADMIN designates through the real production endpoint/service path.
        designate(teacher.getId(), adminToken(), dept.getId());

        // The linked User must now hold ROLE_HOD.
        assertEquals("HOD", userRepository.findByEmail(email).orElseThrow().getRole().getName());

        // The same teacher logs in again and reaches a real /api/hod/** endpoint.
        String hodToken = loginToken(email, "Teacher@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hod").value(true))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.departmentName").value(dept.getName()));

        // The same HOD JWT also authorizes a real HOD report endpoint.
        mockMvc.perform(get("/api/hod/dashboard").header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value(dept.getName()));

        // Clear through the real production endpoint/service path.
        clearHod(teacher.getId(), adminToken());

        // The linked User is demoted back to ROLE_TEACHER.
        assertEquals("TEACHER", userRepository.findByEmail(email).orElseThrow().getRole().getName());

        // The same teacher can no longer access /api/hod/**.
        String clearedToken = loginToken(email, "Teacher@123");
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + clearedToken))
                .andExpect(status().isForbidden());

        // The cleared TEACHER token cannot access the HOD report endpoint either.
        mockMvc.perform(get("/api/hod/dashboard").header("Authorization", "Bearer " + clearedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonDesignatedTeacher_keepsTeacherRole() throws Exception {
        String email = "teacher-c-" + System.nanoTime() + "@dagacs.local";
        createTeacher(email, "ACTIVE");
        accountProvisioningService.provisionLogin(email, "Prof", "Teacher@123", "TEACHER", "ACTIVE");

        assertEquals("TEACHER", userRepository.findByEmail(email).orElseThrow().getRole().getName());
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + loginToken(email, "Teacher@123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentMove_preservesHodRole() throws Exception {
        Department deptX = createDepartment();
        Department deptY = createDepartment();
        String email = "hod-move-" + System.nanoTime() + "@dagacs.local";
        Teacher teacher = createTeacher(email, "ACTIVE");
        accountProvisioningService.provisionLogin(email, "Prof", "Teacher@123", "TEACHER", "ACTIVE");

        designate(teacher.getId(), adminToken(), deptX.getId());
        assertEquals("HOD", userRepository.findByEmail(email).orElseThrow().getRole().getName());
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + loginToken(email, "Teacher@123")))
                .andExpect(status().isOk());

        // Move to the free target department via the same designation flow.
        designate(teacher.getId(), adminToken(), deptY.getId());
        assertEquals("HOD", userRepository.findByEmail(email).orElseThrow().getRole().getName());
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + loginToken(email, "Teacher@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value(deptY.getName()));
    }

    @Test
    void teacherWithoutLogin_designateNo500_noUserFabricated() throws Exception {
        Department dept = createDepartment();
        String email = "no-login-" + System.nanoTime() + "@dagacs.local";
        Teacher teacher = createTeacher(email, "ACTIVE");

        designate(teacher.getId(), adminToken(), dept.getId());

        Teacher persisted = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertTrue(persisted.getIsHod());
        assertFalse(userRepository.existsByEmail(email), "no login account may be fabricated");
    }

    @Test
    void inactiveLogin_isNotPromoted_no500() throws Exception {
        Department dept = createDepartment();
        String email = "inactive-login-" + System.nanoTime() + "@dagacs.local";
        Teacher teacher = createTeacher(email, "ACTIVE");
        accountProvisioningService.provisionLogin(email, "Prof", "Teacher@123", "TEACHER", "INACTIVE");

        // Designation follows existing business semantics: flag is set, no error.
        designate(teacher.getId(), adminToken(), dept.getId());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertEquals("TEACHER", user.getRole().getName(), "inactive login must not be promoted to HOD");
        assertEquals("INACTIVE", user.getStatus(), "login status must not be reactivated");

        // M9.5 behavior: an INACTIVE login cannot authenticate at all.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Teacher@123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveProfile_isNotPromoted_no500() throws Exception {
        Department dept = createDepartment();
        String email = "inactive-profile-" + System.nanoTime() + "@dagacs.local";
        Teacher teacher = createTeacher(email, "INACTIVE");
        accountProvisioningService.provisionLogin(email, "Prof", "Teacher@123", "TEACHER", "ACTIVE");

        // Designation follows existing business semantics: flag is set, no error.
        designate(teacher.getId(), adminToken(), dept.getId());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertEquals("TEACHER", user.getRole().getName(), "inactive profile must not be escalated to HOD");
        assertEquals("ACTIVE", user.getStatus(), "login status must be untouched");

        Teacher persisted = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertEquals("INACTIVE", persisted.getStatus(), "profile status must not be reactivated");
        assertTrue(persisted.getIsHod());

        // No HOD authority: the login resolves as a plain TEACHER end-to-end.
        mockMvc.perform(get("/api/hod/me").header("Authorization", "Bearer " + loginToken(email, "Teacher@123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void clear_selfHealsDriftedHodRole() throws Exception {
        Department dept = createDepartment();
        String email = "drift-" + System.nanoTime() + "@dagacs.local";
        Teacher teacher = createTeacher(email, "ACTIVE");
        teacher.setDepartment(dept);
        teacher.setIsHod(false);
        teacherRepository.save(teacher);
        User user = accountProvisioningService.provisionLogin(email, "Prof", "Teacher@123", "TEACHER", "ACTIVE");
        Role hodRole = roleRepository.findByName("HOD").orElseThrow();
        user.setRole(hodRole);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Reproduce the recorded defect state: isHod=false but role=HOD.
        assertEquals("HOD", userRepository.findByEmail(email).orElseThrow().getRole().getName());

        clearHod(teacher.getId(), adminToken());

        assertEquals("TEACHER", userRepository.findByEmail(email).orElseThrow().getRole().getName(),
                "clear must restore TEACHER independent of the previous isHod value");
        assertFalse(teacherRepository.findById(teacher.getId()).orElseThrow().getIsHod());
    }
}