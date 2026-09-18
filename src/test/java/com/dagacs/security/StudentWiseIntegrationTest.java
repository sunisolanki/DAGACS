package com.dagacs.security;

import com.dagacs.entity.*;
import com.dagacs.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance tests for the additive student-wise
 * attendance register (M10B Candidate C), covering the LOCKED request contract:
 *
 * <pre>
 *   subjectId        : required                    -> 400 when missing
 *   sectionId|batchId: exactly one                 -> 400 when both or neither
 *   nonexistent IDs  : 404
 *   batch-with-sections: 400 (Phase-2 batch-mode rule)
 *   unassigned/incompatible context: 403
 *   dates            : optional, inclusive range, project-convention validation
 * </pre>
 *
 * Also covered: section mode, batch (zero-section) mode, enrollment ordering,
 * student rows, sessionId-based column identity (same-day LP1/LP2 stay separate),
 * P/A cells, Present/Total/Percentage (M4 private copy), date filtering, and
 * HOD access only within the HOD-as-Teacher's assigned teaching scope.
 *
 * Frozen milestones (M4/M7.1/M7.2/M9.15) are not modified.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentWiseIntegrationTest {

    private static final String TEACHER_EMAIL = "teacher@dagacs.local";
    private static final String TEACHER_PASSWORD = "Teacher@123";
    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String STUDENT_EMAIL = "student@dagacs.local";
    private static final String STUDENT_PASSWORD = "Student@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private AcademicSessionRepository academicSessionRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private TeacherSubjectSectionAssignmentRepository assignmentRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private SubjectOfferingRepository subjectOfferingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private static LocalDateTime now() { return LocalDateTime.now(); }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void flush() { entityManager.flush(); }

    // ── Fixture helpers ──────────────────────────────────────────

    private static class Slice {
        Department dept;
        Program program;
        AcademicSession session;
        Batch batch;
        Section section;
        Subject subject;
        Semester semester;
        SubjectOffering offering;
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
        s.subject = subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name("Subject " + deptCode)
                .description("Test").creditHours("3").department(s.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        s.semester = semesterRepository.save(Semester.builder()
                .name("Sem-" + System.nanoTime()).code("SEM").year(2026)
                .academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
        s.offering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(s.subject).semester(s.semester)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    /** Returns the persisted HOD-linked Teacher so tests can assign it. */
    private Teacher makeHod(Slice s) {
        return teacherRepository.save(Teacher.builder()
                .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(s.dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** Zero-section batch for Phase-2 batch mode. */
    private Batch zeroSectionBatch(Slice s) {
        return batchRepository.save(Batch.builder()
                .batchCode("BatchZ-" + System.nanoTime()).name("Z").year(2026)
                .program("Prog").maxCapacity(60).academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Student student(Slice s, String name, String enrollmentNumber) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber(enrollmentNumber).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .academicSession(s.session).batch(s.batch).section(s.section).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** A student belonging directly to a zero-section batch (section = null). */
    private Student studentInBatch(Slice s, Batch batch, String name, String enrollmentNumber) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber(enrollmentNumber).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .academicSession(s.session).batch(batch).section(null).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
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

    private void assignBatch(Teacher teacher, SubjectOffering offering, Batch batch) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).batch(batch)
                .createdAt(now()).updatedAt(now()).build());
    }

    private AttendanceSession saveSession(Subject sub, Section sec, Teacher teacher,
                                          String date, String period, String status) {
        return attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(sub).sectionEntity(sec).teacherEntity(teacher)
                .subject(sub.getName()).section(sec.getName()).teacher(teacher.getFullName())
                .lecturePeriod(period).date(date).status(status)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** A real persisted batch-mode (section-less) session: section NULL, batch set. */
    private AttendanceSession saveBatchSession(Subject sub, Batch batch, Teacher teacher,
                                               String date, String period, String status) {
        return attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(sub).batchEntity(batch).teacherEntity(teacher)
                .subject(sub.getName()).section(null).batch(batch.getBatchCode())
                .teacher(teacher.getFullName())
                .lecturePeriod(period).date(date).status(status)
                .createdAt(now()).updatedAt(now()).build());
    }

    private AttendanceRecord record(AttendanceSession session, Student student, boolean present) {
        return attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(session.getSubjectEntity())
                .section(session.getSectionEntity())
                .batch(session.getBatchEntity())
                .markedBy(session.getTeacherEntity())
                .status(present ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod()).date(session.getDate()).isPresent(present)
                .createdAt(now()).build());
    }

    // ── Matrix JSON endpoint: access control (5) ────────────────

    @Test
    void matrix_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/attendance/student-wise")).andExpect(status().isUnauthorized());
    }

    @Test
    void matrix_adminToken_returns403() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", "1").param("sectionId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrix_studentToken_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", "1").param("sectionId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrix_teacherToken_returns200() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_ok@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        flush();
        String token = login("sw_ok@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void matrix_hodTokenWithAssignment_returns200() throws Exception {
        Slice a = slice("A");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        flush();
        String token = login(HOD_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ── Matrix JSON endpoint: locked context contract (7) ───────

    @Test
    void matrix_missingSubject_returns400() throws Exception {
        Slice a = slice("A");
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_neitherSectionNorBatch_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_none@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_none@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_bothSectionAndBatch_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_both@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_both@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .param("batchId", String.valueOf(a.batch.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_nonexistentSubject_returns404() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_nsubj@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_nsubj@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", "999999")
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void matrix_nonexistentSection_returns404() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_nsec@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_nsec@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", "999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void matrix_batchWithSections_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_bhz@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_bhz@dagacs.local", "Pass@123");
        // a.batch structurally owns a.section -> batch mode must be rejected.
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("batchId", String.valueOf(a.batch.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_unassignedContext_returns403() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        Teacher t = createTeacherAccount("sw_noasgn@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        flush();
        String token = login("sw_noasgn@dagacs.local", "Pass@123");
        // The teacher is assigned to A's class, not B's -> must be rejected.
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(b.subject.getId()))
                        .param("sectionId", String.valueOf(b.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Matrix rendering (3) ────────────────────────────────────

    @Test
    void matrix_twoSessionsSameDate_twoDistinctColumns() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_2@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        Student s1 = student(a, "Student A1", "ENR-001");
        Student s2 = student(a, "Student A2", "ENR-002");

        // Same date, two different periods -> two columns keyed by different sessionIds.
        AttendanceSession lp1 = saveSession(a.subject, a.section, t, "2026-01-10", "LP1", "CONDUCTED");
        AttendanceSession lp2 = saveSession(a.subject, a.section, t, "2026-01-10", "LP2", "CONDUCTED");
        record(lp1, s1, true);
        record(lp1, s2, false);
        record(lp2, s1, false);
        record(lp2, s2, true);
        flush();

        String token = login("sw_2@dagacs.local", "Pass@123");
        JsonNode root = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId(), token);

        assertEquals(2, root.get("columns").size());
        assertEquals(2, root.get("rows").size());
        // Columns are ordered by (date ASC, lecturePeriod ASC).
        assertEquals(lp1.getId(), root.get("columns").get(0).get("sessionId").asLong());
        assertEquals("LP1", root.get("columns").get(0).get("lecturePeriod").asText());
        assertEquals(lp2.getId(), root.get("columns").get(1).get("sessionId").asLong());
        assertEquals("LP2", root.get("columns").get(1).get("lecturePeriod").asText());

        // s1: 1/2 present; s2: 1/2 present -> P/A cells carry the session identity.
        for (JsonNode row : root.get("rows")) {
            assertEquals(1, row.get("presentCount").asInt());
            assertEquals(2, row.get("totalRecordedCount").asInt());
            assertEquals(50.0, row.get("percentage").asDouble(), 0.001);
            assertEquals(2, row.get("cells").size());
        }
    }

    @Test
    void matrix_enrollmentOrdering() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_3@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        // Insert in reverse enrollment order; matrix must reorder to ENR-001 then ENR-002.
        Student s2 = student(a, "Z-Student", "ENR-002");
        Student s1 = student(a, "A-Student", "ENR-001");

        AttendanceSession session = saveSession(a.subject, a.section, t, "2026-01-10", "LP1", "CONDUCTED");
        record(session, s2, true);
        record(session, s1, true);
        flush();

        String token = login("sw_3@dagacs.local", "Pass@123");
        JsonNode root = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId(), token);

        assertEquals(2, root.get("rows").size());
        assertEquals("ENR-001", root.get("rows").get(0).get("enrollmentNumber").asText());
        assertEquals("ENR-002", root.get("rows").get(1).get("enrollmentNumber").asText());
    }

    @Test
    void matrix_emptyData_returnsEmptyArrays() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_4@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        flush();

        String token = login("sw_4@dagacs.local", "Pass@123");
        JsonNode root = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId(), token);

        assertEquals(0, root.get("columns").size());
        assertEquals(0, root.get("rows").size());
    }

    // ── Matrix batch (zero-section) mode: real persistence + rendering (1) ─
    // REVERSED from the previous pass: batch-mode sessions/records are now
    // really persisted (schema relaxed so section is NULL, batch is set) and
    // the matrix renders the actual recorded cells, not an empty contract.

    @Test
    void matrix_batchZeroSection_realPersistence_rendersCellsAndTotals() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_batch@dagacs.local", "Pass@123", a.dept);
        Batch z = zeroSectionBatch(a);
        assignBatch(t, a.offering, z);
        // Insert in reverse enrollment order; matrix must reorder to ENR-001 then ENR-002.
        Student s2 = studentInBatch(a, z, "Z-Student", "ENR-002");
        Student s1 = studentInBatch(a, z, "A-Student", "ENR-001");

        // Two genuinely section-less sessions, both CONDUCTED with real records.
        AttendanceSession lp1 = saveBatchSession(a.subject, z, t, "2026-01-10", "LP1", "CONDUCTED");
        AttendanceSession lp2 = saveBatchSession(a.subject, z, t, "2026-01-10", "LP2", "CONDUCTED");
        record(lp1, s1, true);
        record(lp1, s2, false);
        record(lp2, s1, false);
        record(lp2, s2, true);
        flush();

        String token = login("sw_batch@dagacs.local", "Pass@123");
        JsonNode root = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&batchId=" + z.getId(), token);

        // The batch identity is carried through the matrix.
        assertEquals(z.getBatchCode(), root.get("batchCode").asText());
        // Real sessions render as two distinct columns keyed by sessionId.
        assertEquals(2, root.get("columns").size());
        assertEquals(lp1.getId(), root.get("columns").get(0).get("sessionId").asLong());
        assertEquals("LP1", root.get("columns").get(0).get("lecturePeriod").asText());
        assertEquals(lp2.getId(), root.get("columns").get(1).get("sessionId").asLong());
        assertEquals("LP2", root.get("columns").get(1).get("lecturePeriod").asText());
        // Enrollment-ordered rows.
        assertEquals(2, root.get("rows").size());
        assertEquals("ENR-001", root.get("rows").get(0).get("enrollmentNumber").asText());
        assertEquals("ENR-002", root.get("rows").get(1).get("enrollmentNumber").asText());
        // s1: 1/2 present (P, A); s2: 1/2 present (A, P) -> 50% each, cells aligned.
        for (JsonNode row : root.get("rows")) {
            assertEquals(1, row.get("presentCount").asInt());
            assertEquals(2, row.get("totalRecordedCount").asInt());
            assertEquals(50.0, row.get("percentage").asDouble(), 0.001);
            assertEquals(2, row.get("cells").size());
        }
    }

    // ── Date range (3) ──────────────────────────────────────────

    @Test
    void matrix_dateRange_inclusive() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_5@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        Student s1 = student(a, "Student A1", "ENR-001");

        AttendanceSession inRange = saveSession(a.subject, a.section, t, "2026-01-15", "LP1", "CONDUCTED");
        AttendanceSession outOfRange = saveSession(a.subject, a.section, t, "2026-02-10", "LP2", "CONDUCTED");
        record(inRange, s1, true);
        record(outOfRange, s1, true);
        flush();

        String token = login("sw_5@dagacs.local", "Pass@123");
        JsonNode all = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId(), token);
        assertEquals(2, all.get("columns").size());

        JsonNode range = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId() + "&startDate=2026-01-01&endDate=2026-01-31", token);
        assertEquals(1, range.get("columns").size());
        assertEquals("2026-01-15", range.get("columns").get(0).get("date").asText());
    }

    @Test
    void matrix_dateRange_malformedDate_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_dbd@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        flush();

        String token = login("sw_dbd@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .param("startDate", "2026-13-99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_dateRange_inverted_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_dinv@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        flush();

        String token = login("sw_dinv@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ── Teacher isolation (1) ───────────────────────────────────

    @Test
    void matrix_otherTeacherRecordsExcluded() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("t1@dagacs.local", "Pass@123", a.dept);
        Teacher t2 = createTeacherAccount("t2@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);

        Student s1 = student(a, "Student A1", "ENR-001");
        // T1 records -> should appear for T1.
        record(saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        // T2 records on the same class -> distinct period, must NOT appear for T1.
        record(saveSession(a.subject, a.section, t2, "2026-01-10", "LP2", "CONDUCTED"), s1, true);
        flush();

        String token = login("t1@dagacs.local", "Pass@123");
        JsonNode root = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId(), token);
        assertEquals(1, root.get("columns").size());
        assertEquals("LP1", root.get("columns").get(0).get("lecturePeriod").asText());
        assertEquals(1, root.get("rows").size());
        assertEquals(1, root.get("rows").get(0).get("presentCount").asInt());
        assertEquals(1, root.get("rows").get(0).get("totalRecordedCount").asInt());
    }

    // ── Percentage semantics (M4 private copy) (1) ──────────────

    @Test
    void matrix_percentage_usesM4PrivateCopy() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_7@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        Student s1 = student(a, "Student A1", "ENR-001");

        record(saveSession(a.subject, a.section, t, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, t, "2026-01-12", "LP2", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, t, "2026-01-14", "LP3", "CONDUCTED"), s1, false);
        flush();

        String token = login("sw_7@dagacs.local", "Pass@123");
        JsonNode root = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&sectionId=" + a.section.getId(), token);
        JsonNode row = root.get("rows").get(0);
        assertEquals(2, row.get("presentCount").asInt());
        assertEquals(3, row.get("totalRecordedCount").asInt());
        assertEquals(66.6666666, row.get("percentage").asDouble(), 0.001);
    }

    // ── HOD cross-scope on reads (1) ────────────────────────────

    @Test
    void matrix_hodAssignedToAClass_cannotReadBClass_returns403() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        flush();
        String token = login(HOD_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(b.subject.getId()))
                        .param("sectionId", String.valueOf(b.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Export: access control + contract parity (5) ────────────

    @Test
    void exportXlsx_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportXlsx_adminToken_returns403() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", "1").param("sectionId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportXlsx_subjectIdMissing_returns400() throws Exception {
        Slice a = slice("A");
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportXlsx_bothSectionAndBatch_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_ex_both@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_ex_both@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .param("batchId", String.valueOf(a.batch.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportXlsx_neitherSectionNorBatch_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_ex_none@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("sw_ex_none@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ── Export: valid render + filename (3) ─────────────────────

    @Test
    void exportXlsx_validFile_returns200WithDisposition() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_8@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        Student s1 = student(a, "Student A1", "ENR-001");

        AttendanceSession session = saveSession(a.subject, a.section, t, "2026-01-10", "LP1", "CONDUCTED");
        record(session, s1, true);
        flush();

        String token = login("sw_8@dagacs.local", "Pass@123");
        MvcResult result = mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("dagacs_teacher_student_wise_all.xlsx"));
        assertTrue(result.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    void exportPdf_validFile_returns200WithDisposition() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_9@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        Student s1 = student(a, "Student A1", "ENR-001");

        AttendanceSession session = saveSession(a.subject, a.section, t, "2026-01-10", "LP1", "CONDUCTED");
        record(session, s1, true);
        flush();

        String token = login("sw_9@dagacs.local", "Pass@123");
        MvcResult result = mockMvc.perform(get("/api/teacher/attendance/student-wise/export.pdf")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("application/pdf", result.getResponse().getContentType());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("dagacs_teacher_student_wise_all.pdf"));
        assertTrue(result.getResponse().getContentAsByteArray().length > 0);
    }

    @Test
    void exportXlsx_dateRangeEncodedInFilename() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("sw_10@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        flush();

        String token = login("sw_10@dagacs.local", "Pass@123");
        MvcResult result = mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("dagacs_teacher_student_wise_2026-01-01_to_2026-01-31.xlsx"));
    }

    // ── Export: HOD access (2) ──────────────────────────────────

    @Test
    void exportXlsx_hodTokenWithAssignment_returns200() throws Exception {
        Slice a = slice("A");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void exportXlsx_hodAssignedToAClass_cannotExportBClass_returns403() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(b.subject.getId()))
                        .param("sectionId", String.valueOf(b.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Helper ──────────────────────────────────────────────────

    private JsonNode fetchJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}