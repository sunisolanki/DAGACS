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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.15 real-JWT, real-MySQL acceptance tests for assignment lifecycle
 * integrity hardening:
 *
 * <ul>
 *   <li>RULE 1: an INACTIVE teacher can never be assigned (create 409).</li>
 *   <li>RULE 2A: an update that changes the teacher onto an INACTIVE teacher
 *       is rejected (409); keeping the same teacher — even a historical
 *       INACTIVE one — is not an inactive-teacher assignment.</li>
 *   <li>RULE 2B / RULE 3: teacher or context changes and deletes are rejected
 *       (409) once teacher-scoped attendance history exists (sessions created
 *       or records marked by that teacher, subject + section).</li>
 *   <li>History is teacher-scoped: many teachers sharing one SubjectOffering +
 *       Section stay independent (co-teacher semantics preserved).</li>
 *   <li>No-op self-updates never fire history guards (200 even with history).</li>
 *   <li>History in one context never blocks creating assignments in another.</li>
 * </ul>
 *
 * All fixtures are rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AssignmentLifecycleIntegrityIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";

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
    private StudentRepository studentRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String login() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
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
        Student student;
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
        s.student = studentRepository.save(Student.builder()
                .rollNumber("LIFE" + System.nanoTime()).name("Lifecycle Student").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email("life" + System.nanoTime() + "@dagacs.local")
                .academicSession(s.session).batch(s.batch).section(s.section).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    private Teacher createActiveTeacher(String email, Department dept) {
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

    private Teacher createInactiveTeacher(String email, Department dept) {
        return teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("Teacher " + email)
                .phone("").designation("Professor").status("INACTIVE")
                .avatarUrl("").isHod(false).department(dept)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void assign(Teacher teacher, SubjectOffering offering, Section section) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).section(section)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Long assignViaApi(String adminToken, Long teacherId, Long offeringId, Long sectionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(teacherId, offeringId, sectionId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long assignmentIdOf(Teacher teacher, SubjectOffering offering, Section section) {
        return assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(
                        teacher.getId(), offering.getId(), section.getId())
                .orElseThrow().getId();
    }

    private String requestBody(Long teacherId, Long offeringId, Long sectionId) {
        return "{\"teacherId\":" + teacherId
                + ",\"subjectOfferingId\":" + offeringId
                + ",\"sectionId\":" + sectionId + "}";
    }

    private void createSession(Teacher teacher, Subject subject, Section section, String period) {
        attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod(period).date("2026-09-04").status("CONDUCTED")
                .createdAt(now()).updatedAt(now()).build());
    }

    private void createRecord(Teacher teacher, Subject subject, Section section, Student student) {
        AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod("1st").date("2026-09-04").status("CONDUCTED")
                .createdAt(now()).updatedAt(now()).build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(now()).build());
        attendanceRecordRepository.flush();
    }

    // ======================= RULE 1: create (inactive) =======================

    @Test
    void createAssignment_inactiveTeacher_returns409() throws Exception {
        Slice a = slice("A");
        Teacher inactive = createInactiveTeacher("inact-create" + System.nanoTime() + "@dagacs.local", a.dept);
        String adminToken = login();

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(inactive.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot assign an inactive teacher."));

        assertFalse(assignmentRepository
                .existsByTeacherIdAndSubjectOfferingIdAndSectionId(
                        inactive.getId(), a.offering.getId(), a.section.getId()));
    }

    // ================== RULE 2: update (teacher dimension) ==================

    @Test
    void updateAssignment_keepSameInactiveTeacher_returns200() throws Exception {
        Slice a = slice("A");
        Teacher inactive = createInactiveTeacher("inact-keep" + System.nanoTime() + "@dagacs.local", a.dept);
        assign(inactive, a.offering, a.section);
        String adminToken = login();

        mockMvc.perform(put("/api/admin/teacher-assignments/"
                        + assignmentIdOf(inactive, a.offering, a.section))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(inactive.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(inactive.getId()));
    }

    @Test
    void updateAssignment_switchToInactiveTeacher_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-switch-a" + System.nanoTime() + "@dagacs.local", a.dept);
        Teacher inactive = createInactiveTeacher("inact-switch" + System.nanoTime() + "@dagacs.local", a.dept);
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());

        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(inactive.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot assign an inactive teacher."));

        assertEquals(t1.getId(),
                assignmentRepository.findById(assignmentId).orElseThrow().getTeacher().getId());
    }

    @Test
    void updateAssignment_teacherChange_withSessions_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-sess-a" + System.nanoTime() + "@dagacs.local", a.dept);
        Teacher t2 = createActiveTeacher("life-sess-b" + System.nanoTime() + "@dagacs.local", a.dept);
        createSession(t1, a.subject, a.section, "2nd");
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t2.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot change assignment teacher while attendance history exists."));

        assertEquals(t1.getId(),
                assignmentRepository.findById(assignmentId).orElseThrow().getTeacher().getId());
    }

    @Test
    void updateAssignment_teacherChange_withRecords_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-rec-a" + System.nanoTime() + "@dagacs.local", a.dept);
        Teacher t2 = createActiveTeacher("life-rec-b" + System.nanoTime() + "@dagacs.local", a.dept);
        createRecord(t1, a.subject, a.section, a.student);
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t2.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot change assignment teacher while attendance history exists."));
    }

    @Test
    void updateAssignment_teacherChange_noHistory_returns200() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-move-a" + System.nanoTime() + "@dagacs.local", a.dept);
        Teacher t2 = createActiveTeacher("life-move-b" + System.nanoTime() + "@dagacs.local", a.dept);
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t2.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(t2.getId()));

        assertEquals(t2.getId(),
                assignmentRepository.findById(assignmentId).orElseThrow().getTeacher().getId());
    }

    // ================== RULE 3: update (context dimension) ==================

    @Test
    void updateAssignment_contextChange_withHistory_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-ctx-a" + System.nanoTime() + "@dagacs.local", a.dept);
        createSession(t1, a.subject, a.section, "3rd");
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        Subject otherSubject = subjectRepository.save(Subject.builder()
                .code("SUBJ-O-" + System.nanoTime()).name("Other Subject")
                .description("Test").creditHours("3").department(a.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        SubjectOffering otherOffering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(otherSubject).semester(a.semester)
                .createdAt(now()).updatedAt(now()).build());

        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), otherOffering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot change assignment context while attendance history exists."));

        assertEquals(a.offering.getId(),
                assignmentRepository.findById(assignmentId).orElseThrow().getSubjectOffering().getId());
    }

    @Test
    void updateAssignment_noChange_withHistory_returns200() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-noop-a" + System.nanoTime() + "@dagacs.local", a.dept);
        createRecord(t1, a.subject, a.section, a.student);
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(t1.getId()));
    }

    // ======================= RULE 4: delete =======================

    @Test
    void deleteAssignment_withSessions_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-del-sess" + System.nanoTime() + "@dagacs.local", a.dept);
        createSession(t1, a.subject, a.section, "4th");
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(delete("/api/admin/teacher-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete assignment while attendance history exists."));

        assertTrue(assignmentRepository.findById(assignmentId).isPresent());
    }

    @Test
    void deleteAssignment_withRecords_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-del-rec" + System.nanoTime() + "@dagacs.local", a.dept);
        createRecord(t1, a.subject, a.section, a.student);
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(delete("/api/admin/teacher-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete assignment while attendance history exists."));
    }

    @Test
    void deleteAssignment_noHistory_returns204() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-del-ok" + System.nanoTime() + "@dagacs.local", a.dept);
        Long assignmentId = assignViaApi(login(), t1.getId(), a.offering.getId(), a.section.getId());
        String adminToken = login();

        mockMvc.perform(delete("/api/admin/teacher-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(assignmentRepository.findById(assignmentId).isPresent());
    }

    // ================= co-teacher independence (teacher-scoped) =================

    @Test
    void coTeachers_sharedContext_historyIsIsolatedPerTeacher() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-cot-a" + System.nanoTime() + "@dagacs.local", a.dept);
        Teacher t2 = createActiveTeacher("life-cot-b" + System.nanoTime() + "@dagacs.local", a.dept);
        Teacher t3 = createActiveTeacher("life-cot-c" + System.nanoTime() + "@dagacs.local", a.dept);
        String adminToken = login();
        Long assignment1 = assignViaApi(adminToken, t1.getId(), a.offering.getId(), a.section.getId());
        Long assignment2 = assignViaApi(adminToken, t2.getId(), a.offering.getId(), a.section.getId());
        createSession(t1, a.subject, a.section, "5th");

        // t2's assignment is independent of t1's history: teacher change 200, delete 204.
        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignment2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t3.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(t3.getId()));
        mockMvc.perform(delete("/api/admin/teacher-assignments/" + assignment2)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // t1's own assignment is blocked by t1's history.
        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignment1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t2.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/teacher-assignments/" + assignment1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void coTeachers_teacherB_stillPerformsOperationalAttendanceAction() throws Exception {
        Slice a = slice("A");
        Teacher aTeacher = createActiveTeacher("life-op-a" + System.nanoTime() + "@dagacs.local", a.dept);
        String bEmail = "life-op-b" + System.nanoTime() + "@dagacs.local";
        Teacher bTeacher = createActiveTeacher(bEmail, a.dept);
        String adminToken = login();
        Long assignmentA = assignViaApi(adminToken, aTeacher.getId(), a.offering.getId(), a.section.getId());
        Long assignmentB = assignViaApi(adminToken, bTeacher.getId(), a.offering.getId(), a.section.getId());
        // A has historical attendance in the shared context.
        createSession(aTeacher, a.subject, a.section, "5th");

        // A's lifecycle operation is blocked by A's history.
        mockMvc.perform(put("/api/admin/teacher-assignments/" + assignmentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(bTeacher.getId(), a.offering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // B can still perform a valid operational attendance action in the SAME
        // shared context using B's real JWT (never a manually injected role).
        String bToken = login(bEmail, "Pass@123");
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"sectionId\":" + a.section.getId()
                                + ",\"lecturePeriod\":\"6th\""
                                + ",\"date\":\"2026-09-04\"}")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecturePeriod").value("6th"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        // B's session is actually persisted (unique period in the shared context).
        assertTrue(attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                a.subject.getId(), a.section.getId(), "2026-09-04", "6th"));
        assertEquals(1, attendanceSessionRepository.findByTeacherEntityIdOrderByDateDesc(bTeacher.getId()).size());

        // A's historical session remains untouched.
        assertEquals(1, attendanceSessionRepository.findByTeacherEntityIdOrderByDateDesc(aTeacher.getId()).size());
        assertTrue(attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                a.subject.getId(), a.section.getId(), "2026-09-04", "5th"));

        // A's blocked assignment remains unchanged (same teacher, same context).
        TeacherSubjectSectionAssignment persistedA = assignmentRepository.findById(assignmentA).orElseThrow();
        assertEquals(aTeacher.getId(), persistedA.getTeacher().getId());
        assertEquals(a.offering.getId(), persistedA.getSubjectOffering().getId());
        assertEquals(a.section.getId(), persistedA.getSection().getId());

        // B's assignment remains intact.
        TeacherSubjectSectionAssignment persistedB = assignmentRepository.findById(assignmentB).orElseThrow();
        assertEquals(bTeacher.getId(), persistedB.getTeacher().getId());
        assertEquals(a.offering.getId(), persistedB.getSubjectOffering().getId());
        assertEquals(a.section.getId(), persistedB.getSection().getId());
    }

    // ===== history in one context never blocks create in another context =====

    @Test
    void createAssignment_historyInDifferentContext_doesNotBlock() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createActiveTeacher("life-ctx2-a" + System.nanoTime() + "@dagacs.local", a.dept);
        createSession(t1, a.subject, a.section, "6th");
        String adminToken = login();
        assignViaApi(adminToken, t1.getId(), a.offering.getId(), a.section.getId());

        Subject otherSubject = subjectRepository.save(Subject.builder()
                .code("SUBJ-Y-" + System.nanoTime()).name("Context Y")
                .description("Test").creditHours("3").department(a.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        SubjectOffering otherOffering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(otherSubject).semester(a.semester)
                .createdAt(now()).updatedAt(now()).build());

        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(t1.getId(), otherOffering.getId(), a.section.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());
    }
}