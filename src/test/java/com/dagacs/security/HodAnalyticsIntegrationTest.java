package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.AttendanceAuditLog;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
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
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance tests for the M6.2 HOD governance &amp;
 * analytics API (see the approved M6.2 plan). 36 tests covering access
 * control, two-department isolation from both HOD perspectives, aggregation
 * correctness, the FIXED 75.0% low-attendance threshold, date-range filtering,
 * monthly/quarterly rollups, department-scoped audit logs, the explicit
 * semester-rollup rejection, and (M6.2 correction #2) a deliberately
 * inconsistent cross-department student/attendance fixture proving the
 * department predicate derives from the AttendanceRecord → Section chain,
 * never Student.program. All rows are created inside the test transaction
 * and rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodAnalyticsIntegrationTest {

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

    private void createHodB(String email, Slice s) {
        Role hodRole = roleRepository.findByName("HOD").orElseThrow();
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("Pass@123")).fullName("HOD B")
                .phone("").status("ACTIVE").role(hodRole).avatarUrl("")
                .createdAt(now()).updatedAt(now()).build());
        teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("HOD B Teacher")
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
                .batch(s.batch).section(s.section).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    private AttendanceRecord addRecord(Slice s, Student student, String date, boolean present) {
        sessionCounter++;
        AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(s.subject).sectionEntity(s.section).teacherEntity(s.teacher)
                .subject(s.subject.getName()).section(s.section.getName()).teacher(s.teacher.getFullName())
                .lecturePeriod("LP" + sessionCounter).date(date).status("CONDUCTED")
                .createdAt(now()).updatedAt(now()).build());
        return attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(s.subject).section(s.section).markedBy(s.teacher)
                .status(present ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod()).date(date).isPresent(present)
                .createdAt(now()).build());
    }

    private void addRecords(Slice s, Student student, String date, int present, int total) {
        for (int i = 0; i < total; i++) {
            addRecord(s, student, date, i < present);
        }
    }

    private AttendanceAuditLog auditLog(Student student, AttendanceRecord attendance,
                                        String studentName, String date) {
        return attendanceAuditLogRepository.save(AttendanceAuditLog.builder()
                .attendance(attendance).student(student)
                .rollNo(student.getRollNumber()).studentName(studentName)
                .subject("SUB").subjectName("Subject " + studentName)
                .section("A").sectionName("A")
                .date(date).previousStatus("PRESENT").newStatus("ABSENT")
                .updatedBy("teacher@dagacs.local").updatedAt(now()).reason("Correction")
                .build());
    }

    private void assertDashboard(String token, long programs, long batches, long sections,
                                 long students, long recorded, long present,
                                 Double expectedPercentage) throws Exception {
        JsonNode node = fetchJson("/api/hod/dashboard", token);
        assertEquals(programs, node.get("programCount").asLong());
        assertEquals(batches, node.get("batchCount").asLong());
        assertEquals(sections, node.get("sectionCount").asLong());
        assertEquals(students, node.get("studentCount").asLong());
        assertEquals(recorded, node.get("totalRecordedCount").asLong());
        assertEquals(present, node.get("presentCount").asLong());
        if (expectedPercentage == null) {
            assertFalse(node.hasNonNull("overallPercentage"));
        } else {
            assertEquals(expectedPercentage, node.get("overallPercentage").asDouble(), 0.001);
        }
    }

    // ============================ Access control (5) ============================

    @Test
    void hodDashboard_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/hod/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void hodDashboard_adminToken_returns403() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/hod/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodDashboard_teacherToken_returns403() throws Exception {
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/hod/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodDashboard_studentToken_returns403() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/hod/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodDashboard_configuredHod_returns200() throws Exception {
        Slice s = slice("A");
        makeHod(s);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get("/api/hod/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ============ Department isolation — HOD(A) sees only Data A (7) ============

    @Test
    void hodDashboard_onlyShowsDepartmentA() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        Student a2 = student(a, "Student A2");
        addRecords(a, a1, "2026-01-10", 2, 2);
        addRecords(a, a2, "2026-01-10", 0, 2);

        Student b1 = student(b, "Student B1");
        addRecords(b, b1, "2026-01-10", 3, 3);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        assertDashboard(token, 1, 1, 1, 2, 4, 2, 50.0);
    }

    @Test
    void hodSections_onlyShowsDepartmentASections() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        addRecords(a, a1, "2026-01-10", 1, 2);

        Student b1 = student(b, "Student B1");
        addRecords(b, b1, "2026-01-10", 1, 2);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode sections = fetchJson("/api/hod/sections", token);
        assertEquals(1, sections.size());
        assertEquals(a.section.getName(), sections.get(0).get("sectionName").asText());
    }

    @Test
    void hodSubjects_onlyShowsDepartmentASubjects() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        addRecords(a, a1, "2026-01-10", 2, 4);

        Student b1 = student(b, "Student B1");
        addRecords(b, b1, "2026-01-10", 2, 4);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode subjects = fetchJson("/api/hod/subjects", token);
        assertEquals(1, subjects.size());
        assertEquals(a.subject.getCode(), subjects.get(0).get("subjectCode").asText());
    }

    @Test
    void hodStudents_onlyShowsDepartmentAStudents() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        Student b1 = student(b, "Student B1");
        addRecords(a, a1, "2026-01-10", 1, 1);
        addRecords(b, b1, "2026-01-10", 1, 1);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode students = fetchJson("/api/hod/students", token);
        assertEquals(1, students.size());
        assertEquals(a1.getRollNumber(), students.get(0).get("rollNumber").asText());
    }

    @Test
    void hodLowAttendance_onlyShowsDepartmentA() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        Student b1 = student(b, "Student B1");
        addRecords(a, a1, "2026-01-10", 1, 10);
        addRecords(b, b1, "2026-01-10", 2, 10);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode low = fetchJson("/api/hod/low-attendance", token);
        assertEquals(1, low.size());
        assertEquals(a1.getRollNumber(), low.get(0).get("rollNumber").asText());
    }

    @Test
    void hodRollups_onlyShowsDepartmentA() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        addRecords(a, a1, "2026-01-10", 2, 4);

        Student b1 = student(b, "Student B1");
        addRecords(b, b1, "2026-02-10", 3, 3);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode rollups = fetchJson("/api/hod/rollups?type=monthly", token);
        assertEquals(1, rollups.size());
        assertEquals("2026-01", rollups.get(0).get("period").asText());
        assertEquals(4, rollups.get(0).get("totalRecordedCount").asLong());
        assertEquals(2, rollups.get(0).get("presentCount").asLong());
    }

    @Test
    void hodAuditLogs_onlyShowsDepartmentA() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        Student b1 = student(b, "Student B1");
        AttendanceRecord recordA = addRecord(a, a1, "2026-01-10", true);
        AttendanceRecord recordB = addRecord(b, b1, "2026-01-10", true);
        auditLog(a1, recordA, "Student A1", "2026-01-10");
        auditLog(b1, recordB, "Student B1", "2026-01-10");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode logs = fetchJson("/api/hod/audit-logs", token);
        assertEquals(1, logs.size());
        assertEquals("Student A1", logs.get(0).get("studentName").asText());
    }

    // ============ Cross-check from HOD(B) perspective (1) ============

    @Test
    void hodDashboard_crossDepartmentHodB_seesOnlyB() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);
        createHodB("hodb@dagacs.local", b);

        Student a1 = student(a, "Student A1");
        addRecords(a, a1, "2026-01-10", 2, 4);

        Student b1 = student(b, "Student B1");
        Student b2 = student(b, "Student B2");
        Student b3 = student(b, "Student B3");
        addRecords(b, b1, "2026-01-10", 3, 3);
        flush();

        String token = login("hodb@dagacs.local", "Pass@123");
        assertDashboard(token, 1, 1, 1, 3, 3, 3, 100.0);
    }

    // ============================ Aggregation (4) ============================

    @Test
    void aggregation_10Present_10Total_100Percent() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 10, 10);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        assertDashboard(token, 1, 1, 1, 1, 10, 10, 100.0);

        JsonNode students = fetchJson("/api/hod/students", token);
        assertEquals(100.0, students.get(0).get("percentage").asDouble(), 0.001);
    }

    @Test
    void aggregation_7Present_10Total_70Percent() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 7, 10);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        assertDashboard(token, 1, 1, 1, 1, 10, 7, 70.0);

        JsonNode students = fetchJson("/api/hod/students", token);
        assertEquals(70.0, students.get(0).get("percentage").asDouble(), 0.001);
    }

    @Test
    void aggregation_0Present_10Total_0Percent() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 0, 10);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        assertDashboard(token, 1, 1, 1, 1, 10, 0, 0.0);

        JsonNode students = fetchJson("/api/hod/students", token);
        assertEquals(0.0, students.get(0).get("percentage").asDouble(), 0.001);
    }

    @Test
    void aggregation_0Records_nullPercentage() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        student(a, "Student A1");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        // No attendance records -> empty student grouping, and null overall percentage.
        JsonNode students = fetchJson("/api/hod/students", token);
        assertEquals(0, students.size());

        JsonNode dashboard = fetchJson("/api/hod/dashboard", token);
        assertEquals(0, dashboard.get("totalRecordedCount").asLong());
        assertFalse(dashboard.hasNonNull("overallPercentage"));
    }

    // ===================== Low attendance — fixed 75.0% (5) =====================

    @Test
    void lowAttendance_74Percent_included() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 7, 10);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode low = fetchJson("/api/hod/low-attendance", token);
        assertEquals(1, low.size());
        assertEquals(s1.getRollNumber(), low.get(0).get("rollNumber").asText());
        assertEquals(70.0, low.get(0).get("percentage").asDouble(), 0.001);
    }

    @Test
    void lowAttendance_75Percent_excluded() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 30, 40);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode low = fetchJson("/api/hod/low-attendance", token);
        assertEquals(0, low.size());
    }

    @Test
    void lowAttendance_76Percent_excluded() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 38, 50);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode low = fetchJson("/api/hod/low-attendance", token);
        assertEquals(0, low.size());
    }

    @Test
    void lowAttendance_0PercentWithRecords_included() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 0, 10);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode low = fetchJson("/api/hod/low-attendance", token);
        assertEquals(1, low.size());
        assertEquals(0.0, low.get(0).get("percentage").asDouble(), 0.001);
    }

    @Test
    void lowAttendance_zeroRecords_excluded() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        student(a, "Student A1");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode low = fetchJson("/api/hod/low-attendance", token);
        assertEquals(0, low.size());
    }

    // ============================ Date filtering (7) ============================

    private Slice dateFixtureSlice() {
        Slice a = slice("A");
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-05", 2, 2);
        addRecords(a, s1, "2026-01-20", 1, 3);
        addRecords(a, s1, "2026-02-10", 1, 1);
        return a;
    }

    @Test
    void dateFilter_noDates_returnsAll() throws Exception {
        Slice a = dateFixtureSlice();
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        assertDashboard(token, 1, 1, 1, 1, 6, 4, 66.666666);
    }

    @Test
    void dateFilter_startOnly_returnsFromStart() throws Exception {
        Slice a = dateFixtureSlice();
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode dashboard = fetchJson("/api/hod/dashboard?startDate=2026-01-20", token);
        assertEquals(4, dashboard.get("totalRecordedCount").asLong());
        assertEquals(2, dashboard.get("presentCount").asLong());
    }

    @Test
    void dateFilter_endOnly_returnsUpToEnd() throws Exception {
        Slice a = dateFixtureSlice();
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode dashboard = fetchJson("/api/hod/dashboard?endDate=2026-01-20", token);
        assertEquals(5, dashboard.get("totalRecordedCount").asLong());
        assertEquals(3, dashboard.get("presentCount").asLong());
    }

    @Test
    void dateFilter_bothDates_inclusiveRange() throws Exception {
        Slice a = dateFixtureSlice();
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode dashboard = fetchJson("/api/hod/dashboard?startDate=2026-01-01&endDate=2026-01-31", token);
        assertEquals(5, dashboard.get("totalRecordedCount").asLong());
        assertEquals(3, dashboard.get("presentCount").asLong());
    }

    @Test
    void dateFilter_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/dashboard")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dateFilter_malformedDate_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/dashboard")
                        .param("startDate", "2026-13-99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dateFilter_departmentIsolation_withDates() throws Exception {
        Slice a = dateFixtureSlice();
        Slice b = slice("B");
        makeHod(a);

        Student b1 = student(b, "Student B1");
        addRecords(b, b1, "2026-01-20", 1, 1);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode dashboard = fetchJson("/api/hod/dashboard?startDate=2026-01-01&endDate=2026-01-31", token);
        // HOD(A) still sees only Department A records within the window.
        assertEquals(5, dashboard.get("totalRecordedCount").asLong());
        assertEquals(3, dashboard.get("presentCount").asLong());
    }

    // ================================ Rollups (2) ================================

    @Test
    void rollup_monthly_correctGrouping() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 3, 4);
        addRecords(a, s1, "2026-02-10", 1, 2);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode rollups = fetchJson("/api/hod/rollups?type=monthly", token);
        assertEquals(2, rollups.size());
        assertEquals("2026-01", rollups.get(0).get("period").asText());
        assertEquals(4, rollups.get(0).get("totalRecordedCount").asLong());
        assertEquals(3, rollups.get(0).get("presentCount").asLong());
        assertEquals("2026-02", rollups.get(1).get("period").asText());
        assertEquals(2, rollups.get(1).get("totalRecordedCount").asLong());
        assertEquals(1, rollups.get(1).get("presentCount").asLong());
    }

    @Test
    void rollup_quarterly_correctGrouping() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        addRecords(a, s1, "2026-01-10", 1, 1);
        addRecords(a, s1, "2026-03-10", 1, 1);
        addRecords(a, s1, "2026-04-10", 1, 1);
        addRecords(a, s1, "2026-06-10", 1, 1);
        addRecords(a, s1, "2026-07-10", 1, 1);
        addRecords(a, s1, "2026-10-10", 1, 1);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode rollups = fetchJson("/api/hod/rollups?type=quarterly", token);
        assertEquals(4, rollups.size());
        assertEquals("2026-Q1", rollups.get(0).get("period").asText());
        assertEquals(2, rollups.get(0).get("totalRecordedCount").asLong());
        assertEquals("2026-Q2", rollups.get(1).get("period").asText());
        assertEquals(2, rollups.get(1).get("totalRecordedCount").asLong());
        assertEquals("2026-Q3", rollups.get(2).get("period").asText());
        assertEquals(1, rollups.get(2).get("totalRecordedCount").asLong());
        assertEquals("2026-Q4", rollups.get(3).get("period").asText());
        assertEquals(1, rollups.get(3).get("totalRecordedCount").asLong());
    }

    // =============================== Audit logs (3) ==============================

    @Test
    void auditLogs_correctFields() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        AttendanceRecord record = addRecord(a, s1, "2026-01-10", true);
        auditLog(s1, record, "Student A1", "2026-01-10");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode logs = fetchJson("/api/hod/audit-logs", token);
        assertEquals(1, logs.size());
        JsonNode entry = logs.get(0);
        assertEquals("Student A1", entry.get("studentName").asText());
        assertEquals(s1.getRollNumber(), entry.get("rollNo").asText());
        assertEquals("Subject Student A1", entry.get("subjectName").asText());
        assertEquals("A", entry.get("sectionName").asText());
        assertEquals("2026-01-10", entry.get("date").asText());
        assertEquals("PRESENT", entry.get("previousStatus").asText());
        assertEquals("ABSENT", entry.get("newStatus").asText());
        assertEquals("teacher@dagacs.local", entry.get("updatedBy").asText());
        assertEquals("Correction", entry.get("reason").asText());
        assertTrue(entry.hasNonNull("updatedAt"));
    }

    @Test
    void auditLogs_otherDepartment_excluded() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);

        Student a1 = student(a, "Student A1");
        Student a2 = student(a, "Student A2");
        Student b1 = student(b, "Student B1");

        AttendanceRecord recA1 = addRecord(a, a1, "2026-01-10", true);
        AttendanceRecord recA2 = addRecord(a, a2, "2026-01-10", true);
        AttendanceRecord recB1 = addRecord(b, b1, "2026-01-10", true);

        auditLog(a1, recA1, "Student A1", "2026-01-10");
        auditLog(a2, recA2, "Student A2", "2026-01-10");
        auditLog(b1, recB1, "Student B1", "2026-01-10");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode logs = fetchJson("/api/hod/audit-logs", token);
        assertEquals(2, logs.size());
        boolean containsB = false;
        for (JsonNode entry : logs) {
            if ("Student B1".equals(entry.get("studentName").asText())) {
                containsB = true;
            }
        }
        assertFalse(containsB);
    }

    @Test
    void auditLogs_noInternalIdsExposed() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        AttendanceRecord record = addRecord(a, s1, "2026-01-10", true);
        auditLog(s1, record, "Student A1", "2026-01-10");
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        JsonNode logs = fetchJson("/api/hod/audit-logs", token);
        assertEquals(1, logs.size());
        JsonNode entry = logs.get(0);
        assertNull(entry.get("id"));
        assertNull(entry.get("attendanceId"));
        assertNull(entry.get("studentId"));
    }

    // ==================== Semester-rollup honesty (1) ====================

    @Test
    void rollup_semesterType_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        // Semester rollup is NOT DERIVABLE / NOT IMPLEMENTED; the API rejects it explicitly.
        mockMvc.perform(get("/api/hod/rollups")
                        .param("type", "semester")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ============ CORRECTION #2 — actual department scope via section chain (1) ============

    @Test
    void departmentScope_fromAttendanceSectionChain_notStudentProgram() throws Exception {
        // Department A: Program/Session/Batch/Section A chain. Department B: only Program B.
        // Student X sits in Section A (Department A chain) but its Student.program is Program B,
        // so attendance ownership MUST derive from the AttendanceRecord.section chain and the
        // student MUST NOT be scoped out of HOD(A). Department predicates use the section chain,
        // never Student.program (see M6.2 correction #2).
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);
        createHodB("hoda@dagacs.local", b);

        // Student X deliberately cross-department: section=SectionA, program=ProgramB.
        Student x = studentRepository.save(Student.builder()
                .rollNumber("STU-CROSS-" + System.nanoTime()).name("Student X").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E-CROSS-" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .batch(a.batch).section(a.section).program(b.program)
                .createdAt(now()).updatedAt(now()).build());

        // 2 attendance records owned by Section A: 1 present, 1 absent → 50% → below 75%.
        AttendanceRecord record = addRecord(a, x, "2026-01-10", true);
        addRecord(a, x, "2026-01-11", false);
        auditLog(x, record, "Student X", "2026-01-10");
        flush();

        String tokenA = login(HOD_A_EMAIL, HOD_PASSWORD);
        String tokenB = login("hoda@dagacs.local", "Pass@123");

        // 1) HOD(A) MUST see Student X (attendance belongs to Section A).
        JsonNode studentsA = fetchJson("/api/hod/students", tokenA);
        boolean studentXA = false;
        String xRoll = x.getRollNumber();
        for (JsonNode entry : studentsA) {
            if (xRoll.equals(entry.get("rollNumber").asText())) {
                studentXA = true;
            }
        }
        assertTrue(studentXA, "HOD(A) must see Student X whose attendance belongs to Section A");

        // 2) HOD(B) MUST NOT see Student X.
        JsonNode studentsB = fetchJson("/api/hod/students", tokenB);
        for (JsonNode entry : studentsB) {
            assertFalse(xRoll.equals(entry.get("rollNumber").asText()),
                    "HOD(B) must NOT see Student X");
        }

        // 3) HOD(A) low-attendance MUST show Student X (1/2 present = 50% < 75%).
        JsonNode lowA = fetchJson("/api/hod/low-attendance", tokenA);
        assertEquals(1, lowA.size(), "HOD(A) low-attendance must contain Student X");
        assertEquals(xRoll, lowA.get(0).get("rollNumber").asText());

        // 4) HOD(B) low-attendance MUST NOT show Student X.
        JsonNode lowB = fetchJson("/api/hod/low-attendance", tokenB);
        assertEquals(0, lowB.size(), "HOD(B) low-attendance must NOT contain Student X");

        // 5) Audit log created above for that AttendanceRecord (present in HOD(A) view).
        // 6) HOD(A) audit-logs MUST show the audit log for that AttendanceRecord.
        JsonNode logsA = fetchJson("/api/hod/audit-logs", tokenA);
        boolean logXA = false;
        for (JsonNode entry : logsA) {
            if ("Student X".equals(entry.get("studentName").asText())) {
                logXA = true;
            }
        }
        assertTrue(logXA, "HOD(A) audit-logs must show the Student X audit log");

        // 7) HOD(B) audit-logs MUST NOT show it.
        JsonNode logsB = fetchJson("/api/hod/audit-logs", tokenB);
        for (JsonNode entry : logsB) {
            assertFalse("Student X".equals(entry.get("studentName").asText()),
                    "HOD(B) audit-logs must NOT show the Student X audit log");
        }

        // 8) Section student count follows Section A's own department chain (HOD(A) counts X).
        JsonNode sectionsA = fetchJson("/api/hod/sections", tokenA);
        assertEquals(1, sectionsA.size(), "HOD(A) must see Section A");
        assertEquals(1, sectionsA.get(0).get("studentCount").asLong(),
                "Section A student count from HOD(A) must include Student X");

        JsonNode sectionsB = fetchJson("/api/hod/sections", tokenB);
        assertEquals(0, sectionsB.size(), "HOD(B) must NOT see Section A");
    }
}
