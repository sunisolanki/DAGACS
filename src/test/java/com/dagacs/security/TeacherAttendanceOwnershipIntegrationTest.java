package com.dagacs.security;

import com.dagacs.entity.*;
import com.dagacs.repository.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.4 real-JWT, real-MySQL acceptance tests proving that attendance-session
 * creation and student lookup are scoped to the authenticated teacher's own
 * teaching assignments only (canonical ownership, never a client-supplied id):
 *
 * <ul>
 *   <li>A teacher creates a session over their own assignment (201) and the
 *       returned teacherId is resolved from the JWT, not the request body.</li>
 *   <li>A different teacher using the same subjectId/sectionId receives 403
 *       because they own no assignment for that subject+section.</li>
 *   <li>A teacher with no assignments at all receives 403.</li>
 *   <li>The duplicate (subject, section, date, period) create is a 409.</li>
 *   <li>Security matrix: anonymous 401, STUDENT 403.</li>
 *   <li>Student lookup for a session: owner 200, non-owner teacher 403.</li>
 * </ul>
 *
 * All fixtures are rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class TeacherAttendanceOwnershipIntegrationTest {

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
    }

    private Slice slice(String tag) {
        Slice s = new Slice();
        s.dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code(tag + "-" + System.nanoTime()).description("Test")
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
                .code("SUBJ-" + System.nanoTime()).name("Subject " + tag)
                .description("Test").creditHours("3").department(s.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        s.offering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(s.subject).semester(s.semester)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    private Teacher createTeacherAccount(String email, Department dept) {
        Role teacherRole = roleRepository.findByName("TEACHER").orElseThrow();
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("Pass@123")).fullName("Teacher " + email)
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

    private String sessionBody(Long subjectId, Long sectionId, String date, String period) {
        return "{\"subjectId\":" + subjectId
                + ",\"sectionId\":" + sectionId
                + ",\"lecturePeriod\":\"" + period
                + "\",\"date\":\"" + date + "\"}";
    }

    // ============================ Ownership ============================

    @Test
    void owner_createsSession_overOwnAssignment_returns201WithResolvedTeacherId() throws Exception {
        Slice a = slice("A");
        Teacher own = createTeacherAccount("wontm94a@dagacs.local", a.dept);
        assign(own, a.offering, a.section);
        String token = login("wontm94a@dagacs.local", "Pass@123");

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P1"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(a.subject.getId()))
                .andExpect(jsonPath("$.sectionId").value(a.section.getId()))
                .andExpect(jsonPath("$.teacherId").value(own.getId()))
                .andExpect(jsonPath("$.lecturePeriod").value("P1"))
                .andExpect(jsonPath("$.date").value("2026-06-01"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void otherTeacher_cannotCreateSession_overOwnersAssignment_returns403() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("wontm94b@dagacs.local", a.dept);
        Teacher intruder = createTeacherAccount("wontm94c@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);

        String ownerToken = login("wontm94b@dagacs.local", "Pass@123");
        String intruderToken = login("wontm94c@dagacs.local", "Pass@123");

        // Owner legitimately creates a session (proves the fixture is valid).
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P1"))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        // The non-assigned teacher using the exact same subjectId/sectionId is denied.
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P1"))
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_withNoAssignments_cannotCreateSession_returns403() throws Exception {
        Slice a = slice("A");
        createTeacherAccount("wontm94d@dagacs.local", a.dept);
        String token = login("wontm94d@dagacs.local", "Pass@123");

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P1"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_duplicateSession_returns409() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("wontm94e@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        String token = login("wontm94e@dagacs.local", "Pass@123");

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P2"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P2"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void owner_readsOwnSessions_otherTeacher_readsStudents_returns403() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("wontm94f@dagacs.local", a.dept);
        Teacher intruder = createTeacherAccount("wontm94g@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);

        String ownerToken = login("wontm94f@dagacs.local", "Pass@123");
        String intruderToken = login("wontm94g@dagacs.local", "Pass@123");

        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2026-06-01", "P3"))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();
        Long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // Owner may read the session roster.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Non-owner teacher is forbidden from peeking at someone else's roster.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }

    // ============================ Security matrix ============================

    @Test
    void anonymous_cannotCreateSession_returns401() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(1L, 1L, "2026-06-01", "P1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void student_cannotCreateSession_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(1L, 1L, "2026-06-01", "P1"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================ Shared fixture bootstrapping ============================

    @Test
    void admin_endpoint_stillRequiresAdminRole_returns403ForTeacher() throws Exception {
        Slice a = slice("A");
        Teacher teacher = createTeacherAccount("wontm94h@dagacs.local", a.dept);
        String token = login("wontm94h@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/admin/teacher-assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}