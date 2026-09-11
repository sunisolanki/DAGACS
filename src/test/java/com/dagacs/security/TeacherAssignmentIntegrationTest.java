package com.dagacs.security;

import com.dagacs.entity.*;
import com.dagacs.repository.*;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.3 real-JWT, real-MySQL acceptance tests for teaching-assignment management:
 *
 * <ul>
 *   <li>Admin CRUD on /api/admin/teacher-assignments (201/200/204) with the exact
 *       three-identity request contract and fully derived response context.</li>
 *   <li>Duplicate 409, academic-session incompatibility 400, missing-reference 404.</li>
 *   <li>Teacher self-service GET /api/teacher/assignments scoped to the
 *       authenticated teacher only (never a client-supplied id).</li>
 *   <li>Security matrix: anonymous 401, TEACHER/HOD/STUDENT 403 on admin
 *       endpoints, non-TEACHER 403 on the teacher endpoint.</li>
 *   <li>SubjectOffering delete is blocked (409) while teacher assignments exist,
 *       allowed (204) once none remain.</li>
 *   <li>GET /api/admin/teachers lists existing teacher rows.</li>
 * </ul>
 *
 * All fixtures are rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class TeacherAssignmentIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String HOD_A_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String TEACHER_EMAIL = "teacher@dagacs.local";
    private static final String TEACHER_PASSWORD = "Teacher@123";
    private static final String STUDENT_EMAIL = "student@dagacs.local";
    private static final String STUDENT_PASSWORD = "Student@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static class Slice {
        Department dept;
        Program program;
        AcademicSession session;
        Batch batch;
        Section section;
        Semester semester;
        Subject subject;
        SubjectOffering offering;
        Teacher teacher;
    }

    private Slice slice(String deptCode) {
        Slice s = new Slice();
        s.dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code(deptCode).description("Test")
                .createdBy("test").createdAt(now()).updatedAt(now()).build());
        s.program = programRepository.save(Program.builder()
                .name("Prog-" + System.nanoTime()).code("P").duration("4yr")
                .description("Test").department(s.dept).build());
        s.session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S")
                .description("Test").program(s.program)
                .createdAt(now()).updatedAt(now()).build());
        s.batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Prog").maxCapacity(60).academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
        s.section = sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(s.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        s.semester = semesterRepository.save(Semester.builder()
                .name("Sem-" + System.nanoTime()).code("SEM").year(2026)
                .academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
        s.subject = subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name("Subject " + deptCode)
                .description("Test").creditHours("3").department(s.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        s.offering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(s.subject).semester(s.semester)
                .createdAt(now()).updatedAt(now()).build());
        s.teacher = teacherRepository.save(Teacher.builder()
                .email("marker" + System.nanoTime() + "@dagacs.local").password("ignored")
                .fullName("Marker").phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false).department(s.dept)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    private Teacher createTeacherAccount(String email, String password, Department dept) {
        Role teacherRole = roleRepository.findByName("TEACHER").orElseThrow();
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode(password)).fullName("Teacher " + email)
                .phone("").status("ACTIVE").role(teacherRole).avatarUrl("")
                .createdAt(now()).updatedAt(now()).build());
        return teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("Teacher " + email)
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false).department(dept)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void assign(Teacher teacher, SubjectOffering offering, Section section) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).section(section)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void assignViaApi(String adminToken, Long teacherId, Long offeringId, Long sectionId) throws Exception {
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + teacherId
                                + ",\"subjectOfferingId\":" + offeringId
                                + ",\"sectionId\":" + sectionId + "}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());
    }

    private String requestBody(Long teacherId, Long offeringId, Long sectionId) {
        return "{\"teacherId\":" + teacherId
                + ",\"subjectOfferingId\":" + offeringId
                + ",\"sectionId\":" + sectionId + "}";
    }

    // ============================ Admin CRUD ============================

    @Test
    void admin_createAssignment_returns201WithDerivedContext() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93a@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(t1.getId()))
                .andExpect(jsonPath("$.teacherName").value("Teacher teachm93a@dagacs.local"))
                .andExpect(jsonPath("$.subjectOfferingId").value(a.offering.getId()))
                .andExpect(jsonPath("$.subjectCode").value(a.subject.getCode()))
                .andExpect(jsonPath("$.sectionId").value(a.section.getId()))
                .andExpect(jsonPath("$.sectionCode").value(a.section.getSectionCode()))
                .andExpect(jsonPath("$.sessionName").value(a.session.getName()))
                .andExpect(jsonPath("$.programName").value(a.program.getName()))
                .andExpect(jsonPath("$.departmentName").value(a.dept.getName()))
                .andExpect(jsonPath("$.batchCode").value(a.batch.getBatchCode()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.has("teacherId") && body.has("subjectOfferingId") && body.has("sectionId"));

        assertTrue(assignmentRepository.count() >= 1);
    }

    @Test
    void admin_createAssignment_duplicate_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93b@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assignViaApi(token, t1.getId(), a.offering.getId(), a.section.getId());

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_createAssignment_incompatibleSession_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93c@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // A second batch/section bound to a different academic session.
        AcademicSession otherSession = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-Other-" + System.nanoTime()).code("SO")
                .description("Test").program(a.program)
                .createdAt(now()).updatedAt(now()).build());
        Batch otherBatch = batchRepository.save(Batch.builder()
                .batchCode("Bat-O-" + System.nanoTime()).name("B2").year(2025)
                .program("Prog").maxCapacity(60).academicSession(otherSession)
                .createdAt(now()).updatedAt(now()).build());
        Section otherSection = sectionRepository.save(Section.builder()
                .sectionCode("Sec-O-" + System.nanoTime()).name("O").maxCapacity(30)
                .batch(otherBatch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), a.offering.getId(), otherSection.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_createAssignment_missingOffer_returns404() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93d@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), 99999L, a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_listAssignments_returns200() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93e@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assignViaApi(token, t1.getId(), a.offering.getId(), a.section.getId());

        mockMvc.perform(get("/api/admin/teacher-assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teacherId").value(t1.getId()))
                .andExpect(jsonPath("$[0].subjectOfferingId").value(a.offering.getId()));
    }

    @Test
    void admin_updateAssignment_movesTeacher_returns200() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93f@dagacs.local", "Pass@123", a.dept);
        Teacher t2 = createTeacherAccount("teachm93g@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assignViaApi(token, t1.getId(), a.offering.getId(), a.section.getId());

        Long assignmentId = assignmentRepository.findAll().get(0).getId();
        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t2.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(t2.getId()));

        assertTrue(assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndSectionId(
                t2.getId(), a.offering.getId(), a.section.getId()));
    }

    @Test
    void admin_deleteAssignment_returns204() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93h@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assignViaApi(token, t1.getId(), a.offering.getId(), a.section.getId());

        Long assignmentId = assignmentRepository.findAllByOrderByIdAsc().get(0).getId();
        mockMvc.perform(delete("/api/admin/teacher-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_deleteAssignment_missing_returns404() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(delete("/api/admin/teacher-assignments/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_listTeachers_returns200() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/teachers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ========================== Teacher self-service ==========================

    @Test
    void teacherSelfService_returnsOnlyOwnAssignments() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93i@dagacs.local", "Pass@123", a.dept);
        Teacher t2 = createTeacherAccount("teachm93j@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        assign(t2, a.offering, a.section);

        String token = login("teachm93i@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].teacherId").value(t1.getId()))
                .andExpect(jsonPath("$[0].subjectCode").value(a.subject.getCode()))
                .andExpect(jsonPath("$[0].sectionCode").value(a.section.getSectionCode()));
    }

    @Test
    void teacherSelfService_empty_whenNoAssignments() throws Exception {
        Slice a = slice("A");
        createTeacherAccount("teachm93k@dagacs.local", "Pass@123", a.dept);

        String token = login("teachm93k@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0));
    }

    // ============================ Security matrix ============================

    @Test
    void adminEndpoints_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/teacher-assignments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoints_teacher_returns403() throws Exception {
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/admin/teacher-assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_hod_returns403() throws Exception {
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":1,\"subjectOfferingId\":1,\"sectionId\":1}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_student_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/admin/teacher-assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherEndpoint_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/assignments")).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherEndpoint_student_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/teacher/assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ==================== SubjectOffering delete dependency ====================

    @Test
    void offeringDelete_withAssignments_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teachm93l@dagacs.local", "Pass@123", a.dept);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assignViaApi(token, t1.getId(), a.offering.getId(), a.section.getId());

        mockMvc.perform(delete("/api/admin/subject-offerings/" + a.offering.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void offeringDelete_withoutAssignments_returns204() throws Exception {
        Slice a = slice("A");
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(delete("/api/admin/subject-offerings/" + a.offering.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}