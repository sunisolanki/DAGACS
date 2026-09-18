package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.TeacherSubjectSectionAssignment;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.AttendanceAuditLogRepository;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.dagacs.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance tests for the M7.1 report data foundation
 * (see the approved M7.1 plan correction):
 *
 * <ul>
 *   <li>HOD daily-lecture feed (/api/hod/reports/daily-lecture) — record-backed,
 *       paginated, inclusive date range, department-scoped.</li>
 *   <li>HOD recording-coverage feed (GET /api/hod/coverage) — RECORD-BACKED: a
 *       subject+section only appears when AttendanceRecord rows actually exist;
 *       recordedDateCount = COUNT(DISTINCT AttendanceRecord.date),
 *       sessionCount = COUNT(DISTINCT AttendanceRecord.session.id); no timetable /
 *       expected-lecture / status-based semantics.</li>
 *   <li>Teacher own-subject report (/api/teacher/attendance/report) — self-scope
 *       via the authenticated teacher + own teaching assignments.</li>
 *   <li>Regression: rollups still monthly-capable and semester still rejected.</li>
 * </ul>
 *
 * All rows are created inside the test transaction and rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class M71ReportIntegrationTest {

    private static final String HOD_A_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String TEACHER_EMAIL = "teacher@dagacs.local";
    private static final String TEACHER_PASSWORD = "Teacher@123";
    private static final String STUDENT_EMAIL = "student@dagacs.local";
    private static final String STUDENT_PASSWORD = "Student@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceAuditLogRepository attendanceAuditLogRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private long sessionCounter = 0;

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

    private JsonNode fetchJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void flush() {
        entityManager.flush();
    }

    private static class Slice {
        Department dept;
        Program program;
        AcademicSession session;
        Batch batch;
        Section section;
        Subject subject;
        Semester semester;
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
        s.teacher = teacherRepository.save(Teacher.builder()
                .email("marker" + System.nanoTime() + "@dagacs.local").password("ignored")
                .fullName("Marker").phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false).department(s.dept)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    private void makeHod(Slice s) {
        teacherRepository.save(Teacher.builder()
                .email(HOD_A_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(s.dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Student student(Slice s, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E-" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .academicSession(s.session).batch(s.batch).section(s.section).program(s.program)
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

    private AttendanceRecord record(AttendanceSession session, Student student, boolean present) {
        return attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(session.getSubjectEntity()).section(session.getSectionEntity())
                .markedBy(session.getTeacherEntity())
                .status(present ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod()).date(session.getDate()).isPresent(present)
                .createdAt(now()).build());
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

    private SubjectOffering offeringFor(Slice s, Subject subject) {
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(s.semester)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Subject extraSubject(Slice s, String name) {
        return subjectRepository.save(Subject.builder()
                .code("EXT-" + System.nanoTime()).name(name)
                .description("Test").creditHours("3").department(s.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
    }

    // ============================ Access control (12) ============================

    @Test
    void dailyLecture_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/hod/reports/daily-lecture")).andExpect(status().isUnauthorized());
    }

    @Test
    void dailyLecture_adminToken_returns403() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dailyLecture_teacherToken_returns403() throws Exception {
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dailyLecture_studentToken_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void coverage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/hod/coverage")).andExpect(status().isUnauthorized());
    }

    @Test
    void coverage_adminToken_returns403() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/hod/coverage").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void coverage_teacherToken_returns403() throws Exception {
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/hod/coverage").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void coverage_studentToken_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/hod/coverage").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherReport_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/attendance/report")).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherReport_adminToken_returns403() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/report").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherReport_hodToken_returnsAllowed() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/report").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void teacherReport_studentToken_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/report").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ====================== Daily-lecture feed (7) ======================

    @Test
    void dailyLecture_configuredHod_returns200() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void dailyLecture_correctAggregationAndOrdering() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        Student s2 = student(a, "Student A2");

        AttendanceSession lp1 = saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED");
        record(lp1, s1, true);
        record(lp1, s2, false);

        AttendanceSession lp2 = saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP2", "CONDUCTED");
        record(lp2, s2, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/reports/daily-lecture", token);
        assertEquals(2, page.get("totalElements").asLong());
        assertEquals(2, page.get("content").size());

        JsonNode row0 = page.get("content").get(0);
        assertEquals("2026-01-10", row0.get("date").asText());
        assertEquals("LP1", row0.get("lecturePeriod").asText());
        assertEquals(2, row0.get("totalRecordedCount").asLong());
        assertEquals(1, row0.get("presentCount").asLong());
        assertEquals(50.0, row0.get("percentage").asDouble(), 0.001);

        JsonNode row1 = page.get("content").get(1);
        assertEquals("LP2", row1.get("lecturePeriod").asText());
        assertEquals(1, row1.get("totalRecordedCount").asLong());
        assertEquals(1, row1.get("presentCount").asLong());
        assertEquals(100.0, row1.get("percentage").asDouble(), 0.001);
    }

    @Test
    void dailyLecture_pagination() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        for (int d = 5; d <= 9; d++) {
            String date = "2026-01-0" + d;
            AttendanceSession session = saveSession(a.subject, a.section, a.teacher, date, "LP1", "CONDUCTED");
            record(session, s1, true);
        }
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page0 = fetchJson("/api/hod/reports/daily-lecture?page=0&size=2", token);
        assertEquals(5, page0.get("totalElements").asLong());
        assertEquals(3, page0.get("totalPages").asLong());
        assertEquals(2, page0.get("content").size());
        assertEquals("2026-01-05", page0.get("content").get(0).get("date").asText());
        assertEquals("2026-01-06", page0.get("content").get(1).get("date").asText());

        JsonNode page1 = fetchJson("/api/hod/reports/daily-lecture?page=1&size=2", token);
        assertEquals("2026-01-07", page1.get("content").get(0).get("date").asText());
        assertEquals("2026-01-08", page1.get("content").get(1).get("date").asText());

        JsonNode page2 = fetchJson("/api/hod/reports/daily-lecture?page=2&size=2", token);
        assertEquals(1, page2.get("content").size());
        assertEquals("2026-01-09", page2.get("content").get(0).get("date").asText());
    }

    @Test
    void dailyLecture_dateRange_inclusive() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        createDailySession(a, s1, "2026-01-05");
        createDailySession(a, s1, "2026-01-20");
        createDailySession(a, s1, "2026-02-10");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode all = fetchJson("/api/hod/reports/daily-lecture", token);
        assertEquals(3, all.get("totalElements").asLong());

        JsonNode range = fetchJson("/api/hod/reports/daily-lecture?startDate=2026-01-01&endDate=2026-01-31", token);
        assertEquals(2, range.get("totalElements").asLong());

        JsonNode startOnly = fetchJson("/api/hod/reports/daily-lecture?startDate=2026-02-10", token);
        assertEquals(1, startOnly.get("totalElements").asLong());

        JsonNode endOnly = fetchJson("/api/hod/reports/daily-lecture?endDate=2026-01-20", token);
        assertEquals(2, endOnly.get("totalElements").asLong());
    }

    @Test
    void dailyLecture_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyLecture_malformedDate_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture")
                        .param("startDate", "2026-13-99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyLecture_sessionWithoutRecords_excluded() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");

        // A session exists but has ZERO records -> must not contribute a row.
        saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "SCHEDULED");
        AttendanceSession recorded = saveSession(a.subject, a.section, a.teacher, "2026-01-07", "LP2", "CONDUCTED");
        record(recorded, s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/reports/daily-lecture", token);
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals("2026-01-07", page.get("content").get(0).get("date").asText());
    }

    @Test
    void dailyLecture_departmentIsolation() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);
        Student a1 = student(a, "Student A1");
        Student b1 = student(b, "Student B1");
        createDailySession(a, a1, "2026-01-05");
        createDailySession(b, b1, "2026-01-05");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/reports/daily-lecture", token);
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals(a.subject.getCode(), page.get("content").get(0).get("subjectCode").asText());
    }

    private void createDailySession(Slice s, Student student, String date) {
        sessionCounter++;
        AttendanceSession session = saveSession(s.subject, s.section, s.teacher, date,
                "LP" + sessionCounter, "CONDUCTED");
        record(session, student, true);
    }

    // ====================== Coverage feed (8) ======================

    @Test
    void coverage_recordBacked_semanticBoundary() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");

        // Session A: session exists, ZERO records -> must not contribute.
        saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "SCHEDULED");

        // Session B: session exists with records -> must contribute.
        AttendanceSession b = saveSession(a.subject, a.section, a.teacher, "2026-01-07", "LP2", "CONDUCTED");
        record(b, s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/coverage", token);
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals(1, page.get("content").size());

        JsonNode row = page.get("content").get(0);
        assertEquals(a.subject.getCode(), row.get("subjectCode").asText());
        assertEquals(a.section.getSectionCode(), row.get("sectionCode").asText());
        assertEquals(1, row.get("recordedDateCount").asLong());
        assertEquals(1, row.get("sessionCount").asLong());
    }

    @Test
    void coverage_statusIrrelevant_recordExistenceAuthoritative() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");

        // SCHEDULED session WITH records counts (status never gates coverage).
        AttendanceSession scheduled = saveSession(a.subject, a.section, a.teacher, "2026-01-09", "LP3", "SCHEDULED");
        record(scheduled, s1, true);

        // CANCELLED session without records does not count.
        saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP4", "CANCELLED");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/coverage", token);
        assertEquals(1, page.get("totalElements").asLong());
        JsonNode row = page.get("content").get(0);
        assertEquals(1, row.get("recordedDateCount").asLong());
        assertEquals(1, row.get("sessionCount").asLong());
    }

    @Test
    void coverage_joinMultiplication_guard() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        Student s2 = student(a, "Student A2");
        Student s3 = student(a, "Student A3");

        // One session, three student records -> sessionCount must stay 1.
        AttendanceSession session = saveSession(a.subject, a.section, a.teacher, "2026-01-15", "LPMULT", "CONDUCTED");
        record(session, s1, true);
        record(session, s2, true);
        record(session, s3, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/coverage", token);
        JsonNode row = page.get("content").get(0);
        assertEquals(1, row.get("recordedDateCount").asLong());
        assertEquals(1, row.get("sessionCount").asLong());

        JsonNode daily = fetchJson("/api/hod/reports/daily-lecture", token);
        JsonNode lecture = daily.get("content").get(0);
        assertEquals(3, lecture.get("totalRecordedCount").asLong());
        assertEquals(2, lecture.get("presentCount").asLong());
        assertEquals(66.666666, lecture.get("percentage").asDouble(), 0.001);
    }

    @Test
    void coverage_multiDate_grouping() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        AttendanceSession d1 = saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED");
        record(d1, s1, true);
        AttendanceSession d2 = saveSession(a.subject, a.section, a.teacher, "2026-02-10", "LP2", "CONDUCTED");
        record(d2, s1, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/coverage", token);
        assertEquals(1, page.get("content").size());
        JsonNode row = page.get("content").get(0);
        assertEquals(2, row.get("recordedDateCount").asLong());
        assertEquals(2, row.get("sessionCount").asLong());
    }

    @Test
    void coverage_dateFilter_inclusive() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        AttendanceSession d1 = saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED");
        record(d1, s1, true);
        AttendanceSession d2 = saveSession(a.subject, a.section, a.teacher, "2026-02-10", "LP2", "CONDUCTED");
        record(d2, s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode all = fetchJson("/api/hod/coverage", token);
        assertEquals(2, all.get("content").get(0).get("recordedDateCount").asLong());

        JsonNode filtered = fetchJson("/api/hod/coverage?startDate=2026-02-01&endDate=2026-02-28", token);
        assertEquals(1, filtered.get("content").get(0).get("recordedDateCount").asLong());
    }

    @Test
    void coverage_departmentIsolation() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);
        Student a1 = student(a, "Student A1");
        Student b1 = student(b, "Student B1");
        AttendanceSession sa = saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED");
        record(sa, a1, true);
        AttendanceSession sb = saveSession(b.subject, b.section, b.teacher, "2026-01-05", "LP1", "CONDUCTED");
        record(sb, b1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page = fetchJson("/api/hod/coverage", token);
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals(a.subject.getCode(), page.get("content").get(0).get("subjectCode").asText());
    }

    @Test
    void coverage_pagination() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        Subject extra = extraSubject(a, "Extra Subject");
        AttendanceSession sOne = saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED");
        record(sOne, s1, true);
        AttendanceSession sTwo = saveSession(extra, a.section, a.teacher, "2026-01-06", "LP1", "CONDUCTED");
        record(sTwo, s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode page0 = fetchJson("/api/hod/coverage?page=0&size=1", token);
        assertEquals(2, page0.get("totalElements").asLong());
        assertEquals(2, page0.get("totalPages").asLong());
        assertEquals(1, page0.get("content").size());

        JsonNode page1 = fetchJson("/api/hod/coverage?page=1&size=1", token);
        assertEquals(1, page1.get("content").size());
    }

    @Test
    void coverage_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/coverage")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coverage_malformedDate_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/coverage")
                        .param("startDate", "2026-13-99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ====================== Teacher subject-wise feed (5) ======================

    @Test
    void teacherReport_ownSubjectOnly() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherA@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);

        Student s1 = student(a, "Student A1");
        AttendanceSession session = saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED");
        record(session, s1, true);
        record(saveSession(a.subject, a.section, t1, "2026-01-11", "LP1", "CONDUCTED"), s1, false);
        flush();

        String token = login("teacherA@dagacs.local", "Pass@123");
        JsonNode rows = fetchJson("/api/teacher/attendance/report", token);
        assertEquals(1, rows.size());
        JsonNode row = rows.get(0);
        assertEquals(a.subject.getCode(), row.get("subjectCode").asText());
        assertEquals(a.section.getSectionCode(), row.get("sectionCode").asText());
        assertEquals(2, row.get("totalRecordedCount").asLong());
        assertEquals(1, row.get("presentCount").asLong());
        assertEquals(50.0, row.get("percentage").asDouble(), 0.001);
    }

    @Test
    void teacherReport_otherTeacherAndNonAssignedExcluded() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherA@dagacs.local", "Pass@123", a.dept);
        Teacher t2 = createTeacherAccount("teacherB@dagacs.local", "Pass@123", a.dept);
        Subject extra = extraSubject(a, "Extra Subject");
        assign(t1, a.offering, a.section);
        assign(t2, offeringFor(a, extra), a.section);

        Student s1 = student(a, "Student A1");
        Student s2 = student(a, "Student A2");

        // T1's own assigned class.
        record(saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED"), s1, true);

        // T1 records on a subject/section they are NOT assigned -> must be excluded.
        record(saveSession(extra, a.section, t1, "2026-01-11", "LP1", "CONDUCTED"), s2, true);

        // Another teacher's class -> must be excluded.
        record(saveSession(extra, a.section, t2, "2026-01-12", "LP1", "CONDUCTED"), s1, true);
        flush();

        String token = login("teacherA@dagacs.local", "Pass@123");
        JsonNode rows = fetchJson("/api/teacher/attendance/report", token);
        assertEquals(1, rows.size());
        assertEquals(a.subject.getCode(), rows.get(0).get("subjectCode").asText());
        assertEquals(1, rows.get(0).get("totalRecordedCount").asLong());
    }

    @Test
    void teacherReport_noAssignments_empty() throws Exception {
        Slice a = slice("A");
        createTeacherAccount("teacherC@dagacs.local", "Pass@123", a.dept);
        flush();

        String token = login("teacherC@dagacs.local", "Pass@123");
        JsonNode rows = fetchJson("/api/teacher/attendance/report", token);
        assertEquals(0, rows.size());
    }

    @Test
    void teacherReport_dateFilter_inclusive() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherA@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, t1, "2026-01-05", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, t1, "2026-02-10", "LP1", "CONDUCTED"), s1, false);
        flush();

        String token = login("teacherA@dagacs.local", "Pass@123");
        JsonNode all = fetchJson("/api/teacher/attendance/report", token);
        assertEquals(2, all.get(0).get("totalRecordedCount").asLong());

        JsonNode range = fetchJson("/api/teacher/attendance/report?startDate=2026-02-01&endDate=2026-02-28", token);
        assertEquals(1, range.get(0).get("totalRecordedCount").asLong());
        assertEquals(0, range.get(0).get("presentCount").asLong());
        assertEquals(0.0, range.get(0).get("percentage").asDouble(), 0.001);
    }

    @Test
    void teacherReport_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherA@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        flush();

        String token = login("teacherA@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ====================== M6.2 reuse regression (2) ======================

    @Test
    void rollup_semesterType_stillRejected() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/rollups")
                        .param("type", "semester")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rollup_monthly_stillWorks() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        AttendanceSession d1 = saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED");
        record(d1, s1, true);
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-20", "LP2", "CONDUCTED"), s1, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode rollups = fetchJson("/api/hod/rollups?type=monthly", token);
        assertEquals(1, rollups.size());
        assertEquals("2026-01", rollups.get(0).get("period").asText());
        assertEquals(2, rollups.get(0).get("totalRecordedCount").asLong());
        assertEquals(1, rollups.get(0).get("presentCount").asLong());
        assertFalse(rollups.get(0).has("id"));
    }
}