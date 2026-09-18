package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M8 F-02: account-status enforcement matrix against the real security stack
 * and real MySQL. Every case is transactional and rolled back, so no permanent
 * data remains.
 *
 * <pre>
 *   users.status     INACTIVE   -> authentication fails globally (401), regardless of profile.
 *   students.status  INACTIVE   -> student identity resolution rejects (401).
 *   teachers.status  INACTIVE   -> teacher identity resolution rejects (401).
 *   teachers.status  INACTIVE   -> HOD identity resolution rejects (401) - HOD is a Teacher.
 *   ACTIVE users               -> unchanged; ADMIN never requires a profile row.
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AccountStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

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

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private void setUserStatus(String email, String status) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(status);
        entityManager.flush();
        entityManager.clear();
    }

    private Student createStudent(String email, String status) {
        Department dept = departmentRepository.save(Department.builder()
                .name("StDept-" + System.nanoTime()).code("SD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("StProg-" + System.nanoTime()).code("SP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("StSess-" + System.nanoTime()).code("SS")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("StBat-" + System.nanoTime()).name("StB1").year(2026)
                .program("StProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("StSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("STU" + System.nanoTime()).name("Student").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status(status)
                .email(email)
                .academicSession(session).batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Teacher createTeacher(String email, String status, boolean isHod, Department department) {
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .email(email).password("Temp#123").fullName("Prof " + email)
                .phone("").designation("Professor").status(status)
                .avatarUrl("").isHod(isHod)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        if (department != null) {
            teacher.setDepartment(department);
            teacherRepository.save(teacher);
        }
        return teacher;
    }

    private Department createDepartment() {
        return departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void adminUserInactive_loginReturns401() throws Exception {
        setUserStatus("admin@dagacs.local", "INACTIVE");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserInactive_existingTokenIsRejected() throws Exception {
        String token = loginToken("admin@dagacs.local", "Admin@123");
        setUserStatus("admin@dagacs.local", "INACTIVE");
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentUserInactive_evenWithActiveProfile_isRejected() throws Exception {
        createStudent("student@dagacs.local", "ACTIVE");
        String token = loginToken("student@dagacs.local", "Student@123");
        setUserStatus("student@dagacs.local", "INACTIVE");
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentProfileInactive_userActive_resolverRejects() throws Exception {
        createStudent("student@dagacs.local", "INACTIVE");
        String token = loginToken("student@dagacs.local", "Student@123");
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherUserInactive_existingTokenIsRejected() throws Exception {
        String token = loginToken("teacher@dagacs.local", "Teacher@123");
        setUserStatus("teacher@dagacs.local", "INACTIVE");
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherProfileInactive_userActive_resolverRejects() throws Exception {
        createTeacher("teacher@dagacs.local", "INACTIVE", false, null);
        String token = loginToken("teacher@dagacs.local", "Teacher@123");
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hodTeacherInactive_userActive_hodResolverRejects() throws Exception {
        createTeacher("hod@dagacs.local", "INACTIVE", true, createDepartment());
        String token = loginToken("hod@dagacs.local", "Hod@123");
        mockMvc.perform(get("/api/hod/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeUsers_remainUnchanged() throws Exception {
        String adminToken = loginToken("admin@dagacs.local", "Admin@123");
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        createStudent("student@dagacs.local", "ACTIVE");
        String studentToken = loginToken("student@dagacs.local", "Student@123");
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        createTeacher("teacher@dagacs.local", "ACTIVE", false, null);
        String teacherToken = loginToken("teacher@dagacs.local", "Teacher@123");
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        createTeacher("hod@dagacs.local", "ACTIVE", true, createDepartment());
        String hodToken = loginToken("hod@dagacs.local", "Hod@123");
        mockMvc.perform(get("/api/hod/me")
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminActive_noProfileRowRequired() throws Exception {
        String adminToken = loginToken("admin@dagacs.local", "Admin@123");
        mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}