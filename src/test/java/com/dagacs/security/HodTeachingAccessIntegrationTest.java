package com.dagacs.security;

import com.dagacs.entity.*;
import com.dagacs.repository.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M10B Candidate A — HOD teaching access, proven end-to-end with REAL JWTs
 * obtained through {@code POST /api/auth/login}. No role is ever injected into
 * the SecurityContext and no authorization is faked.
 *
 * <p>Authorized HOD access exists ONLY through an actual, active Teacher
 * profile linked to the HOD login AND a valid teaching assignment on that
 * profile. Every affected endpoint underneath {@code /api/teacher/**} is
 * re-checked at the service boundary with the same assignment authorization as
 * the Teacher flow:
 *
 * <ul>
 *   <li>Attendance sessions: AssignmentService/AttendanceSessionService
 *       {@code existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId} /
 *       {@code ...BatchId...} (403 when not assigned).</li>
 *   <li>Student-wise report/export: TeacherStudentWiseReportService performs the
 *       same assignment check (403 outside the assigned scope).</li>
 *   <li>M7.1 subject report + M7.2 teacher export: self-scoped to the resolved
 *       Teacher identity only (cannot address another teacher's class).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodTeachingAccessIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
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

    private Student studentInSection(Slice s, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("ENR-" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .academicSession(s.session).batch(s.batch).section(s.section).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** A zero-section batch (no sections) on the slice's academic session. */
    private Batch zeroSectionBatch(Slice s) {
        return batchRepository.save(Batch.builder()
                .batchCode("HodZ-" + System.nanoTime()).name("Z").year(2026)
                .program("Prog").maxCapacity(60).academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** A batch-mode student (section = null, batch set). */
    private Student studentInBatch(Slice s, Batch batch, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("ENR-" + System.nanoTime()).age(20)
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

    /** HOD-role login with NO linked Teacher profile. */
    private void createHodLoginWithoutTeacherProfile(String email, String password) {
        Role hodRole = roleRepository.findByName("HOD").orElseThrow();
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode(password)).fullName("HOD no profile")
                .phone("").status("ACTIVE").role(hodRole).avatarUrl("")
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

    private AttendanceRecord record(AttendanceSession session, Student student, boolean present) {
        return attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(session.getSubjectEntity()).section(session.getSectionEntity())
                .markedBy(session.getTeacherEntity())
                .status(present ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod()).date(session.getDate()).isPresent(present)
                .createdAt(now()).build());
    }

    private String createSessionBody(Slice s) {
        return "{\"subjectId\":" + s.subject.getId()
                + ",\"sectionId\":" + s.section.getId()
                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}";
    }

    // ============================================================
    // 1. Linked HOD + assigned Teacher profile -> full attendance flow
    // ============================================================

    @Test
    void hodWithLinkedAssignedTeacherProfile_runsFullAttendanceFlow() throws Exception {
        Slice a = slice("A");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        Student s1 = studentInSection(a, "S1");
        Student s2 = studentInSection(a, "S2");
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // Own assignments are visible (My Classes).
        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Create the attendance session for the assigned class.
        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(createSessionBody(a))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // Roster is the assigned class's roster.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Mark attendance.
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + s1.getId() + ",\"status\":\"PRESENT\"},"
                                + "{\"studentId\":" + s2.getId() + ",\"status\":\"PRESENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        // SCHEDULED session became CONDUCTED after the save (Candidate B) and
        // shows up in the HOD's own (resolved-teacher, self-scoped) list.
        mockMvc.perform(get("/api/teacher/attendance/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONDUCTED"));

        // Records are readable within the assigned scope.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ============================================================
    // 1b. Linked HOD + assigned Teacher profile -> batch-mode flow
    // ============================================================

    @Test
    void hodWithLinkedAssignedTeacherProfile_runsBatchModeAttendanceFlow() throws Exception {
        Slice a = slice("A");
        Teacher hod = makeHod(a);
        Batch z = zeroSectionBatch(a);
        assignBatch(hod, a.offering, z);
        Student s1 = studentInBatch(a, z, "B1");
        Student s2 = studentInBatch(a, z, "B2");
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // Own batch assignment is visible (My Classes).
        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Create a section-less session for the assigned zero-section batch.
        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // Roster is the batch roster (students with no Section).
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Mark attendance against the batch session.
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + s1.getId() + ",\"status\":\"PRESENT\"},"
                                + "{\"studentId\":" + s2.getId() + ",\"status\":\"ABSENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        // The section-less session became CONDUCTED.
        mockMvc.perform(get("/api/teacher/attendance/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONDUCTED"));

        // The batch-mode matrix carries the batch identity into the render.
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("batchId", String.valueOf(z.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchCode").value(z.getBatchCode()))
                .andExpect(jsonPath("$.rows.length()").value(2));
    }

    // ============================================================
    // 2. Linked HOD + NO assignment -> 403 for assignment-bound ops
    // ============================================================

    @Test
    void hodWithLinkedProfileButNoAssignment_returns403ForAssignmentBoundOperations()
            throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // createSession is assignment-bound -> 403.
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(createSessionBody(a))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Student-wise matrix is assignment-bound -> 403 (nothing is silently
        // served for an arbitrary context).
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // The self-scoped M7.1 report stays reachable (the HOD's own Teacher
        // profile has no recorded classes -> empty, no cross-scope leak).
        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============================================================
    // 3. HOD assigned to Teacher A class -> Teacher B class op -> 403
    // ============================================================

    @Test
    void hodAssignedToClassA_cannotOperateOnClassB() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        Teacher bTeacher = createTeacherAccount("hodcross-b@dagacs.local", "Pass@123", b.dept);
        assign(bTeacher, b.offering, b.section);
        AttendanceSession bSession = saveSession(b.subject, b.section, bTeacher, "2026-01-16", "LP1", "SCHEDULED");
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // Cannot create a session on Teacher B's class.
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + b.subject.getId()
                                + ",\"sectionId\":" + b.section.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-17\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Cannot read Teacher B's roster.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + bSession.getId() + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Cannot read Teacher B's attendance records.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + bSession.getId() + "/records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 4. HOD assigned to Teacher A -> Teacher B report/read -> 403
    // ============================================================

    @Test
    void hodAssignedToClassA_cannotReadTeacherBStudentWiseReport() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        Teacher bTeacher = createTeacherAccount("hodcross-b2@dagacs.local", "Pass@123", b.dept);
        assign(bTeacher, b.offering, b.section);
        record(saveSession(b.subject, b.section, bTeacher, "2026-01-16", "LP1", "CONDUCTED"),
                studentInSection(b, "B-Student"), true);
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // The student-wise matrix for Teacher B's class (a report that does
        // accept a context) must be rejected - never returned even when empty.
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(b.subject.getId()))
                        .param("sectionId", String.valueOf(b.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 5. HOD assigned to Teacher A -> Teacher B export -> 403
    // ============================================================

    @Test
    void hodAssignedToClassA_cannotExportTeacherBStudentWiseReport() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        Teacher hod = makeHod(a);
        assign(hod, a.offering, a.section);
        Teacher bTeacher = createTeacherAccount("hodcross-b3@dagacs.local", "Pass@123", b.dept);
        assign(bTeacher, b.offering, b.section);
        record(saveSession(b.subject, b.section, bTeacher, "2026-01-16", "LP1", "CONDUCTED"),
                studentInSection(b, "B-Student"), true);
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(b.subject.getId()))
                        .param("sectionId", String.valueOf(b.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 6. HOD without a linked Teacher profile -> 401
    // ============================================================

    @Test
    void hodWithoutLinkedTeacherProfile_isRejectedFromTeacherOperations() throws Exception {
        Slice a = slice("A");
        createHodLoginWithoutTeacherProfile("hod-nolink@dagacs.local", "Hod@123");
        flush();

        String token = login("hod-nolink@dagacs.local", "Hod@123");

        // Operational endpoint: identity resolution fails -> 401, never TEACHER access.
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(createSessionBody(a))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 7. Ordinary STUDENT -> Teacher operational endpoint -> 403
    // ============================================================

    @Test
    void studentRole_isBlockedFromTeacherOperationalEndpoints() throws Exception {
        Slice a = slice("A");
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(createSessionBody(a))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 8. TEACHER behavior unchanged
    // ============================================================

    @Test
    void teacherRole_behaviorRemainsUnchanged() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("hodctl-teacher@dagacs.local", "Pass@123", a.dept);
        assign(t, a.offering, a.section);
        Student s1 = studentInSection(a, "S1");
        flush();

        String token = login("hodctl-teacher@dagacs.local", "Pass@123");

        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(createSessionBody(a))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + s1.getId() + ",\"status\":\"ABSENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("sectionId", String.valueOf(a.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teacher/attendance/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Unassigned class remains out of reach for a plain TEACHER too.
        Slice b = slice("B");
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(b.subject.getId()))
                        .param("sectionId", String.valueOf(b.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}