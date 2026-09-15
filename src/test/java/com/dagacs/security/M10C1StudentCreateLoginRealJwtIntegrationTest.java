package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class M10C1StudentCreateLoginRealJwtIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DepartmentRepository deptRepo;
    @Autowired private ProgramRepository progRepo;
    @Autowired private AcademicSessionRepository sessionRepo;
    @Autowired private BatchRepository batchRepo;
    @Autowired private SectionRepository sectionRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private StudentManagementRepository studentRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Section setup() {
        Department d = deptRepo.save(Department.builder()
                .name("Dept").code("D").description("T").createdBy("t").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program p = progRepo.save(Program.builder()
                .name("CS").code("CS").duration("4yr").description("T").department(d).build());
        AcademicSession s = sessionRepo.save(AcademicSession.builder()
                .name("S").code("S").description("T").program(p).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch b = batchRepo.save(Batch.builder()
                .batchCode("B").name("B1").year(2026).program("CS").maxCapacity(60).academicSession(s).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepo.save(Section.builder()
                .sectionCode("Sec").name("A").maxCapacity(30).batch(b).status("ACTIVE").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String adminToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String teacherToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"teacher@dagacs.local\",\"password\":\"Teacher@123\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void m10c1CreateLogin201HappyPathFullFlow() throws Exception {

        // RUNTIME STEP 1: Admin logs in, JWT present, role=ADMIN
        String adminResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode adminLogin = objectMapper.readTree(adminResponse);
        String adminJwt = adminLogin.get("token").asText();
        assertNotNull(adminJwt);
        assertEquals("ADMIN", adminLogin.get("role").asText());

        // RUNTIME STEP 2: Academic setup created
        Section sec = setup();
        long programId = sec.getBatch().getAcademicSession().getProgram().getId();
        long batchId = sec.getBatch().getId();
        long sectionId = sec.getId();

        // RUNTIME STEP 3: Create student WITHOUT email → HTTP 201, no auto-provision
        String roll2 = "STU" + System.nanoTime();
        String bodyNoEmail = "{\"rollNumber\":\"" + roll2 + "\",\"name\":\"Rahul Kumar\","
                + "\"programId\":" + programId + ",\"batchId\":" + batchId + ",\"sectionId\":" + sectionId + "}";
        MvcResult createdNoEmail = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyNoEmail))
                .andExpect(status().isCreated()).andReturn();
        long id2 = objectMapper.readTree(createdNoEmail.getResponse().getContentAsString()).get("id").asLong();

        // RUNTIME STEP 4: Test fixture — set Student email directly via repository, NOT through production update logic
        String email = "student" + System.nanoTime() + "@dagacs.local";
        Student student = studentRepo.findById(id2).orElseThrow();
        student.setEmail(email);
        studentRepo.save(student);

        // RUNTIME STEP 5: Verify Student email is valid but NO User exists
        assertTrue(studentRepo.findById(id2).orElseThrow().getEmail().equals(email));
        assertTrue(userRepo.findByEmail(email).isEmpty(), "User must not exist before Create Login");

        // RUNTIME STEP 6: GET detail — loginLinked=false (no User yet)
        mockMvc.perform(get("/api/admin/students/" + id2)
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginLinked").value(false));

        // RUNTIME STEP 7: POST /api/admin/students/{id}/login — NO body — HTTP 201
        MvcResult provisionResult = mockMvc.perform(post("/api/admin/students/" + id2 + "/login")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isCreated()).andReturn();

        // RUNTIME STEP 8: Parse response — temporaryPassword exists, mustChange=true, loginLinked=true
        JsonNode provisionJson = objectMapper.readTree(provisionResult.getResponse().getContentAsString());
        String temporaryPassword = provisionJson.get("temporaryPassword").asText();
        assertNotNull(temporaryPassword);
        assertFalse(temporaryPassword.isEmpty());
        assertTrue(provisionJson.get("mustChangePassword").asBoolean());
        assertTrue(provisionJson.get("loginLinked").asBoolean());
        assertEquals("ACTIVE", provisionJson.get("loginStatus").asText());

        // RUNTIME STEP 9: Repository assertion — User now exists, role=STUDENT, status=ACTIVE, mustChange=true
        User user = userRepo.findByEmail(email).orElseThrow();
        assertEquals("STUDENT", user.getRole().getName());
        assertEquals("ACTIVE", user.getStatus());
        assertTrue(user.isMustChangePassword());

        // RUNTIME STEP 10: Password persistence — BCrypt hash matches returned temporaryPassword
        assertTrue(passwordEncoder.matches(temporaryPassword, user.getPassword()));
        assertFalse(user.getPassword().equals(temporaryPassword));
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$") || user.getPassword().startsWith("$2y$"));

        // RUNTIME STEP 11: GET detail — loginLinked=true, temporaryPassword absent
        mockMvc.perform(get("/api/admin/students/" + id2)
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        // RUNTIME STEP 12: GET list — temporaryPassword absent
        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].temporaryPassword").doesNotExist());

        // RUNTIME STEP 13: Roll Number login with temporaryPassword → HTTP 200, role=STUDENT, mustChange=true
        String loginBody = "{\"identifier\":\"" + roll2 + "\",\"password\":\"" + temporaryPassword + "\"}";
        MvcResult stdLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk()).andReturn();
        JsonNode stdLogin = objectMapper.readTree(stdLoginResult.getResponse().getContentAsString());
        String studentJwt = stdLogin.get("token").asText();
        assertNotNull(studentJwt);
        assertEquals("STUDENT", stdLogin.get("role").asText());
        assertTrue(stdLogin.get("mustChangePassword").asBoolean());

        // RUNTIME STEP 14: Forced gate — Student JWT on protected endpoint → HTTP 403
        mockMvc.perform(get("/api/student/test")
                        .header("Authorization", "Bearer " + studentJwt))
                .andExpect(status().isForbidden());

        // RUNTIME STEP 15: Change password → HTTP 200
        String changeBody = "{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"NewPass#1\",\"confirmPassword\":\"NewPass#1\"}";
        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + studentJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody))
                .andExpect(status().isOk());

        // RUNTIME STEP 16: BCrypt new password matches, old temp doesn't, mustChange=false
        User updatedUser = userRepo.findByEmail(email).orElseThrow();
        assertFalse(passwordEncoder.matches(temporaryPassword, updatedUser.getPassword()));
        assertTrue(passwordEncoder.matches("NewPass#1", updatedUser.getPassword()));
        assertFalse(updatedUser.isMustChangePassword());

        // RUNTIME STEP 17: Same JWT Student API now works → HTTP 200
        mockMvc.perform(get("/api/student/test")
                        .header("Authorization", "Bearer " + studentJwt))
                .andExpect(status().isOk());

        // RUNTIME STEP 18: New password login → HTTP 200, mustChange=false
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + roll2 + "\",\"password\":\"NewPass#1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // RUNTIME STEP 19: Old temp password rejected → HTTP 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());

        // RUNTIME STEP 20: Security negatives — duplicate 409, NULL-email 400, nonexistent 404, non-admin 403, anonymous 401
        mockMvc.perform(post("/api/admin/students/" + id2 + "/login")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isConflict());

        Student nullEmailStudent = studentRepo.save(Student.builder()
                .rollNumber("STU" + System.nanoTime())
                .name("NoEmail")
                .email(null)
                .gender("M")
                .fatherName("F")
                .motherName("M")
                .photoUrl("")
                .enrollmentNumber("E")
                .age(20)
                .admissionDate("2025-01-01")
                .status("ACTIVE")
                .batch(batchRepo.findById(batchId).orElseThrow())
                .section(sectionRepo.findById(sectionId).orElseThrow())
                .program(progRepo.findById(programId).orElseThrow())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        studentRepo.save(nullEmailStudent);
        mockMvc.perform(post("/api/admin/students/" + nullEmailStudent.getId() + "/login")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The student has no email to link a login to"));

        mockMvc.perform(post("/api/admin/students/999999/login")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isNotFound());

        String teacherJwt = teacherToken();
        mockMvc.perform(post("/api/admin/students/" + id2 + "/login")
                        .header("Authorization", "Bearer " + teacherJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/students/" + id2 + "/login"))
                .andExpect(status().isUnauthorized());
    }
}
