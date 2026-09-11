package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.5.1 + M9.5.4 acceptance against the real security stack and real MySQL:
 * admin provisions a teacher profile plus login from the UI API, the teacher can
 * then authenticate and reach TEACHER-only endpoints, collisions return 409,
 * login/profile lifecycles never cascade (D4), and identity-resolution failures
 * carry the stable machine-readable codes (D5) while invalid/bogus JWTs return a
 * code-less 401 (the client treats that as session-expiry and logs out).
 * <p>
 * Every case is transactional and rolled back.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class TeacherProvisioningAcceptanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

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

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String teacherCreateBody(String email) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"fullName\":\"Prof Provisioned\","
                + "\"phone\":\"1234567890\","
                + "\"designation\":\"Professor\","
                + "\"password\":\"TempPass#1\""
                + "}";
    }

    private String adminToken() throws Exception {
        return loginToken("admin@dagacs.local", "Admin@123");
    }

    private Department createDepartment() {
        return departmentRepository.save(Department.builder()
                .name("Dept-" + Math.abs(System.nanoTime())).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Teacher createTeacherRow(String email, String status, boolean isHod, Department department) {
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .email(email).password("").fullName("Prof " + email)
                .phone("").designation("Professor").status(status)
                .avatarUrl("").isHod(isHod)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        if (department != null) {
            teacher.setDepartment(department);
            teacherRepository.save(teacher);
        }
        return teacher;
    }

    private void createStudentRow(String email) {
        Department dept = createDepartment();
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
        studentRepository.save(Student.builder()
                .rollNumber("STU" + Math.abs(System.nanoTime())).name("Student").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("")
                .enrollmentNumber("E" + Math.abs(System.nanoTime())).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(email).batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void adminCreatesTeacher_thenTeacherCanLoginAndReachTeacherApi() throws Exception {
        String email = freshEmail("teacher-");
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andExpect(jsonPath("$.loginStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String teacherToken = loginToken(email, "TempPass#1");
        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/teacher-management/teachers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void createTeacher_duplicateTeacherEmail_returns409() throws Exception {
        String email = freshEmail("dup-");
        String token = adminToken();

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void createTeacher_emailAlreadyUsedByStudent_returns409() throws Exception {
        String email = freshEmail("stu-collision-");
        createStudentRow(email);
        String token = adminToken();

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void createTeacher_emailAlreadyUsedByLogin_returns409() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody("teacher@dagacs.local")))
                .andExpect(status().isConflict());
    }

    @Test
    void createTeacher_shortPassword_returns400() throws Exception {
        String token = adminToken();
        String body = teacherCreateBody(freshEmail("short-"))
                .replace("\"TempPass#1\"", "\"short\"");

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTeacher_invalidEmail_returns400() throws Exception {
        String token = adminToken();
        String body = "{"
                + "\"email\":\"not-an-email\","
                + "\"fullName\":\"Prof Provisioned\","
                + "\"password\":\"TempPass#1\""
                + "}";

        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanUpdateProfileAndToggleProfileStatus_withoutCascadingToLogin() throws Exception {
        String email = freshEmail("toggle-");
        String token = adminToken();
        Department department = createDepartment();

        MvcResult created = mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String updateBody = "{"
                + "\"fullName\":\"Prof Renamed\","
                + "\"designation\":\"Head Professor\","
                + "\"departmentId\":" + department.getId()
                + "}";
        mockMvc.perform(put("/api/admin/teacher-management/teachers/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Prof Renamed"))
                .andExpect(jsonPath("$.designation").value("Head Professor"))
                .andExpect(jsonPath("$.departmentName").value(department.getName()));

        mockMvc.perform(patch("/api/admin/teacher-management/teachers/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.loginStatus").value("ACTIVE"));

        mockMvc.perform(patch("/api/admin/teacher-management/teachers/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String teacherToken = loginToken(email, "TempPass#1");
        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
    }

    @Test
    void resettingLoginPassword_oldPasswordFails_newPasswordWorks() throws Exception {
        String email = freshEmail("reset-");
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        loginToken(email, "TempPass#1");

        mockMvc.perform(put("/api/admin/teacher-management/teachers/" + id + "/login/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass#456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"TempPass#1\"}"))
                .andExpect(status().isUnauthorized());

        String newToken = loginToken(email, "NewPass#456");
        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingLogin_blocksLogin_butProfileStaysActive() throws Exception {
        String email = freshEmail("login-off-");
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/admin/teacher-management/teachers/" + id + "/login/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"TempPass#1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/teacher-management/teachers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.loginStatus").value("INACTIVE"));
    }

    @Test
    void provisioningLogin_again_returns409() throws Exception {
        String email = freshEmail("twice-");
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/teacher-management/teachers/" + id + "/login")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Another#1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherIdentityFailures_returnStableCodes() throws Exception {
        String token = loginToken("teacher@dagacs.local", "Teacher@123");

        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TEACHER_PROFILE_NOT_LINKED"));

        createTeacherRow("teacher@dagacs.local", "INACTIVE", false, null);
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TEACHER_PROFILE_INACTIVE"));
    }

    @Test
    void invalidJwt_returnsCodeLess401_andAdminStillWorks() throws Exception {
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer bogus.invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());

        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void studentIdentityFailures_returnStableCodes() throws Exception {
        String token = loginToken("student@dagacs.local", "Student@123");

        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_LINKED"));
    }

    @Test
    void hodIdentityFailures_returnStableCodes_andActiveHodSucceeds() throws Exception {
        String token = loginToken("hod@dagacs.local", "Hod@123");

        mockMvc.perform(get("/api/hod/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HOD_PROFILE_NOT_LINKED"));

        Teacher teacher = createTeacherRow("hod@dagacs.local", "ACTIVE", false, null);
        mockMvc.perform(get("/api/hod/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HOD_NOT_DESIGNATED"));

        teacher.setIsHod(true);
        teacher.setDepartment(null);
        teacherRepository.save(teacher);
        mockMvc.perform(get("/api/hod/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HOD_NO_DEPARTMENT"));

        Department department = createDepartment();
        teacher.setDepartment(department);
        teacherRepository.save(teacher);
        mockMvc.perform(get("/api/hod/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void teacherManagement_studentForbidden_anonymousUnauthorized() throws Exception {
        String studentToken = loginToken("student@dagacs.local", "Student@123");
        mockMvc.perform(post("/api/admin/teacher-management/teachers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherCreateBody(freshEmail("forbid-"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/teacher-management/teachers"))
                .andExpect(status().isUnauthorized());
    }
}