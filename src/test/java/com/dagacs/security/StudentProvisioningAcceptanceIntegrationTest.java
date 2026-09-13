package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.TeacherRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.5.2 acceptance against the real security stack and real MySQL: the admin
 * provisions a student profile plus a STUDENT login account from the UI API,
 * the student can then authenticate and reach STUDENT-only endpoints, email
 * collisions across teachers/students/logins return 409, login and profile
 * lifecycles never cascade (D4), and a linked login cannot be orphaned by an
 * email edit.
 * <p>
 * Every case is transactional and rolled back.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentProvisioningAcceptanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private SectionRepository sectionRepository;

    private String freshEmail(String prefix) {
        return prefix + Math.abs(System.nanoTime()) + "@dagacs.local";
    }

    private String adminToken() throws Exception {
        return loginToken("admin@dagacs.local", "Admin@123");
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Teacher createTeacherRow(String email) {
        return teacherRepository.save(Teacher.builder()
                .email(email).password("").fullName("Prof " + email)
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private long programId;
    private long batchId;
    private long sectionId;

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

    private String studentCreateBody(String email, long sectionId) {
        return "{"
                + "\"rollNumber\":\"STU" + Math.abs(System.nanoTime()) + "\","
                + "\"email\":\"" + email + "\","
                + "\"name\":\"New Student\","
                + "\"gender\":\"F\","
                + "\"fatherName\":\"Father\","
                + "\"motherName\":\"Mother\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"programId\":" + this.programId + ","
                + "\"batchId\":" + this.batchId + ","
                + "\"sectionId\":" + sectionId
                + "}";
    }

    @Test
    void adminCreatesStudent_autoProvisionsLogin_tempPasswordForcesChange() throws Exception {
        String email = freshEmail("student-");
        long sectionId = createSectionEnv();
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(email, sectionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andExpect(jsonPath("$.loginStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();
        String temporaryPassword = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("temporaryPassword").asText();

        // the temporary password is the real persisted password: it logs in
        String studentToken = loginToken(email, temporaryPassword);
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createStudent_noEmail_noLoginProvisioned() throws Exception {
        long sectionId = createSectionEnv();
        String token = adminToken();
        String rollNumber = "STU" + Math.abs(System.nanoTime());
        String body = "{"
                + "\"rollNumber\":\"" + rollNumber + "\","
                + "\"name\":\"No Email Student\","
                + "\"gender\":\"M\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"programId\":" + this.programId + ","
                + "\"batchId\":" + this.batchId + ","
                + "\"sectionId\":" + sectionId
                + "}";

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginLinked").value(false))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    @Test
    void createStudent_emailUsedByTeacher_returns409() throws Exception {
        String email = freshEmail("teach-collision-");
        long sectionId = createSectionEnv();
        createTeacherRow(email);
        String token = adminToken();

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(email, sectionId)))
                .andExpect(status().isConflict());
    }

    @Test
    void createStudent_emailUsedByLogin_returns409() throws Exception {
        long sectionId = createSectionEnv();
        String token = adminToken();

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody("teacher@dagacs.local", sectionId)))
                .andExpect(status().isConflict());
    }

    @Test
    void reProvisioningAnAutoProvisionedLogin_returns409() throws Exception {
        String email = freshEmail("twice-");
        long sectionId = createSectionEnv();
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(email, sectionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // the login was already auto-provisioned, so an explicit provision is a
        // duplicate and must keep returning 409
        mockMvc.perform(post("/api/admin/students/" + id + "/login")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Another#1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivatingStudentLogin_blocksLogin_profileStaysActive() throws Exception {
        String email = freshEmail("login-off-");
        long sectionId = createSectionEnv();
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(email, sectionId)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String temporaryPassword = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("temporaryPassword").asText();
        loginToken(email, temporaryPassword);

        mockMvc.perform(patch("/api/admin/students/" + id + "/login/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resettingStudentPassword_oldPasswordFails_newPasswordWorks_andForcesChange() throws Exception {
        String email = freshEmail("reset-");
        long sectionId = createSectionEnv();
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(email, sectionId)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String temporaryPassword = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("temporaryPassword").asText();

        mockMvc.perform(put("/api/admin/students/" + id + "/login/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewStuPass#456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isUnauthorized());

        // admin reset forces the change: the new password logs in but the
        // password-change gate still blocks normal student APIs
        String newToken = loginToken(email, "NewStuPass#456");
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/student/change-password")
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"NewStuPass#456\","
                                + "\"newPassword\":\"NewStuPass#789\","
                                + "\"confirmPassword\":\"NewStuPass#789\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void updateStudent_emailChangeWhileLoginLinked_returns409() throws Exception {
        String email = freshEmail("linked-edit-");
        long sectionId = createSectionEnv();
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(email, sectionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/admin/students/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(freshEmail("renamed-"), sectionId)))
                .andExpect(status().isConflict());
    }

    @Test
    void studentManagement_teacherForbidden_anonymousUnauthorized() throws Exception {
        long sectionId = createSectionEnv();
        String teacherToken = loginToken("teacher@dagacs.local", "Teacher@123");
        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentCreateBody(freshEmail("forbid-"), sectionId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isUnauthorized());
    }
}