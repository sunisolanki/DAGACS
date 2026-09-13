package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M10A end-to-end forced-first-login coverage against the real security stack,
 * real MySQL and the real ADMIN-created student provisioning path.
 * <p>
 * The first case is the full lifecycle the correction requires: admin creates a
 * student (which auto-provisions a STUDENT login and returns a temporary
 * password), the student logs in with rollNumber + temporary password, normal
 * student APIs are gated with 403, the password is changed by verifying the
 * current password, the same JWT is immediately unblocked, and the old password
 * stops working while the new one succeeds.
 * </p>
 * <p>
 * The remaining cases are the current-password security contract: the endpoint
 * identity comes only from the JWT, and the current password must verify before
 * any mutation happens.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentPasswordChangeIntegrationTest {

    private static final String NEW_PASSWORD = "NewPass#2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private AcademicSessionRepository academicSessionRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;

    private long programId;
    private long batchId;
    private long sectionId;

    private String freshEmail(String prefix) {
        return prefix + Math.abs(System.nanoTime()) + "@dagacs.local";
    }

    private long createSectionEnv() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + Math.abs(System.nanoTime())).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Prog-" + Math.abs(System.nanoTime())).code("P").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + Math.abs(System.nanoTime())).code("S")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + Math.abs(System.nanoTime())).name("B1").year(2026)
                .program("Prog").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + Math.abs(System.nanoTime())).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        this.programId = program.getId();
        this.batchId = batch.getId();
        this.sectionId = section.getId();
        return section.getId();
    }

    private String adminToken() throws Exception {
        return login("admin@dagacs.local", "Admin@123").token;
    }

    private String studentCreateBody(String rollNumber, String email) {
        return "{"
                + "\"rollNumber\":\"" + rollNumber + "\","
                + "\"email\":\"" + email + "\","
                + "\"name\":\"New Student\","
                + "\"gender\":\"F\","
                + "\"fatherName\":\"Father\","
                + "\"motherName\":\"Mother\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"programId\":" + programId + ","
                + "\"batchId\":" + batchId + ","
                + "\"sectionId\":" + sectionId
                + "}";
    }

    private static final class LoginResult {
        final String token;
        final boolean mustChangePassword;

        LoginResult(String token, boolean mustChangePassword) {
            this.token = token;
            this.mustChangePassword = mustChangePassword;
        }
    }

    private LoginResult login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new LoginResult(node.get("token").asText(), node.get("mustChangePassword").asBoolean());
    }

    private static final class ProvisionedStudent {
        final long id;
        final String rollNumber;
        final String email;
        final String temporaryPassword;

        ProvisionedStudent(long id, String rollNumber, String email, String temporaryPassword) {
            this.id = id;
            this.rollNumber = rollNumber;
            this.email = email;
            this.temporaryPassword = temporaryPassword;
        }
    }

    private ProvisionedStudent adminCreateStudent() throws Exception {
        String admin = adminToken();
        createSectionEnv();
        String rollNumber = "STU" + Math.abs(System.nanoTime());
        String email = freshEmail("forced-");
        MvcResult created = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(rollNumber, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();
        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        return new ProvisionedStudent(node.get("id").asLong(), rollNumber, email,
                node.get("temporaryPassword").asText());
    }

    private boolean flagOf(String email) {
        User user = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        return user.isMustChangePassword();
    }

    @Test
    void forcedFirstLogin_fullLifecycle_adminCreateToUnblocked() throws Exception {
        ProvisionedStudent student = adminCreateStudent();

        // login by rollNumber + temporary password
        LoginResult firstLogin = login(student.rollNumber, student.temporaryPassword);
        org.junit.jupiter.api.Assertions.assertTrue(firstLogin.mustChangePassword);

        // gate: normal student API blocked
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + firstLogin.token))
                .andExpect(status().isForbidden());

        // change password with the current (temporary) password
        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + firstLogin.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + student.temporaryPassword + "\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // same JWT is immediately unblocked
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + firstLogin.token))
                .andExpect(status().isOk());

        // the one-time password is never echoed by later reads
        mockMvc.perform(get("/api/admin/students/" + student.id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // old password no longer works, new password does
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + student.rollNumber + "\","
                                + "\"password\":\"" + student.temporaryPassword + "\"}"))
                .andExpect(status().isUnauthorized());

        LoginResult secondLogin = login(student.email, NEW_PASSWORD);
        org.junit.jupiter.api.Assertions.assertFalse(secondLogin.mustChangePassword);
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + secondLogin.token))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400_andKeepsFlag() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongCurrent#1\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(flagOf(student.email));
        // old (temporary) password still authenticates
        login(student.email, student.temporaryPassword);
    }

    @Test
    void changePassword_blankCurrentPassword_returns400() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(flagOf(student.email));
    }

    @Test
    void changePassword_missingCurrentPassword_returns400() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(flagOf(student.email));
    }

    @Test
    void changePassword_confirmationMismatch_returns400() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + student.temporaryPassword + "\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"Different#99\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(flagOf(student.email));
    }

    @Test
    void changePassword_newTooShort_returns400() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + student.temporaryPassword + "\","
                                + "\"newPassword\":\"short1\","
                                + "\"confirmPassword\":\"short1\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(flagOf(student.email));
    }

    @Test
    void changePassword_newEqualsCurrent_returns400() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + student.temporaryPassword + "\","
                                + "\"newPassword\":\"" + student.temporaryPassword + "\","
                                + "\"confirmPassword\":\"" + student.temporaryPassword + "\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertTrue(flagOf(student.email));
    }

    @Test
    void changePassword_failedAttemptDoesNotMutatePassword() throws Exception {
        ProvisionedStudent student = adminCreateStudent();
        LoginResult login = login(student.email, student.temporaryPassword);

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongCurrent#1\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        // the temporary password is intact after the rejected attempt
        LoginResult relogin = login(student.rollNumber, student.temporaryPassword);
        org.junit.jupiter.api.Assertions.assertTrue(relogin.mustChangePassword);
    }
}