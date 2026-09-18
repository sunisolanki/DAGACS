package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * True end-to-end M5.1 profile verification:
 * <p>
 * Real JWT login → JWT identity → {@code StudentRepository.findByEmail} on the actual
 * {@code students.email} DB profile → profile response for that student only. Also proves
 * a client-supplied {@code studentId} query parameter cannot alter identity (the API never
 * reads it), that a STUDENT account with no linked profile gets 401, and that non-STUDENT
 * roles get 403.
 * </p>
 * <p>
 * All users/students are created and removed within the test transaction (rolled back),
 * so no production/demo data is seeded.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentProfileIntegrationTest {

    private static final String PASSWORD = "Prof#123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private User createStudentUser(String email) {
        Role role = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT role not seeded"));
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName("Profile Student")
                .phone("1234567890")
                .status("ACTIVE")
                .avatarUrl("url")
                .role(role)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Section createTestSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Computer Science").code("CS").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Computer Science").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Student createTestStudent(String email, Section section) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU" + System.nanoTime()).name("Profile Student").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(email)
                .academicSession(section.getBatch().getAcademicSession())
                .batch(section.getBatch()).section(section).program(section.getBatch().getAcademicSession().getProgram())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String loginToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void linkedStudent_seesOwnProfile_and_clientStudentIdCannotChangeIdentity() throws Exception {
        String email = "linked" + System.nanoTime() + "@dagacs.local";
        Section section = createTestSection();
        Student studentA = createTestStudent(email, section);
        Student other = createTestStudent("other" + System.nanoTime() + "@dagacs.local", section);
        createStudentUser(email);

        String token = loginToken(email);

        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value(studentA.getRollNumber()))
                .andExpect(jsonPath("$.enrollmentNumber").value(studentA.getEnrollmentNumber()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Profile Student"))
                .andExpect(jsonPath("$.sectionName").value(section.getName()))
                .andExpect(jsonPath("$.batchName").value(section.getBatch().getName()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // A client-supplied studentId cannot switch identity or expose another profile.
        mockMvc.perform(get("/api/student/profile")
                        .param("studentId", String.valueOf(other.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value(studentA.getRollNumber()));
    }

    @Test
    void studentWithoutLinkedProfile_returns401() throws Exception {
        String email = "unlinked" + System.nanoTime() + "@dagacs.local";
        createStudentUser(email);
        createTestSection();

        String token = loginToken(email);

        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonStudentRoles_areForbidden() throws Exception {
        // DataInitializer-seeded accounts are used for role checks.
        String token = login("admin@dagacs.local", "Admin@123");
        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void seededTeacher_getsForbidden() throws Exception {
        String token = login("teacher@dagacs.local", "Teacher@123");
        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void seededHod_getsForbidden() throws Exception {
        String token = login("hod@dagacs.local", "Hod@123");
        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String login(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void profile_ofLinkedStudent_hasCorrectProgramName() throws Exception {
        String email = "prog" + System.nanoTime() + "@dagacs.local";
        Section section = createTestSection();
        Student student = createTestStudent(email, section);
        createStudentUser(email);

        String token = loginToken(email);

        String programName = student.getProgram().getName();
        assertEquals(programName, section.getBatch().getAcademicSession().getProgram().getName());
        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value(programName));
    }
}