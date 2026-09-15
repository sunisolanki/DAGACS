package com.dagacs.security;

import com.dagacs.dto.AttendanceAuditLogDTO;
import com.dagacs.entity.*;
import com.dagacs.repository.*;
import com.dagacs.service.AttendanceAuditLogService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase-2 batch-level attendance PERSISTENCE proof, run end-to-end through the
 * LIVE teacher/admin APIs with REAL JWTs obtained via {@code POST
 * /api/auth/login} against the real MySQL schema.
 *
 * <p>Proves that a zero-section batch session is actually persisted with
 * {@code section = NULL, batch = <target batch>} at the session and record
 * level, that the batch roster, marking, SCHEDULED-to-CONDUCTED transition,
 * student-wise matrix, exports and the audit trail all operate on those
 * section-less rows, and the full negative contract (both/neither, batch-with-
 * sections, nonexistent batch, unassigned, HOD allowed/denied, STUDENT denied).
 * No {@code @WithMockUser} anywhere — every call is authorized by a real JWT.
 *
 * <p>This reverses the previous correction pass that downgraded batch-mode
 * coverage to an "empty query contract": the batch session here is created
 * through the real service and survives through the report layer inside the
 * transaction before rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class BatchAttendanceIntegrationTest {

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
    @Autowired private AttendanceAuditLogService auditLogService;

    private static LocalDateTime now() { return LocalDateTime.now(); }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void flush() {
        entityManager.flush();
    }

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

    /** A zero-section batch (no sections) on the slice's academic session. */
    private Batch zeroSectionBatch(Slice s) {
        return batchRepository.save(Batch.builder()
                .batchCode("BatchZ-" + System.nanoTime()).name("Z-" + System.nanoTime()).year(2026)
                .program("Prog").maxCapacity(60).academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** Returns the persisted HOD-linked Teacher so tests can assign it. */
    private Teacher makeHod(Slice s) {
        return teacherRepository.save(Teacher.builder()
                .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(s.dept).isHod(true)
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

    /** A student belonging directly to a batch (section = null). */
    private Student studentInBatch(Slice s, Batch batch, String name, String enrollmentNumber) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber(enrollmentNumber).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .batch(batch).section(null).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void assignBatch(Teacher teacher, SubjectOffering offering, Batch batch) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).batch(batch)
                .createdAt(now()).updatedAt(now()).build());
    }

    private JsonNode fetchJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ============================================================
    // 1. Real persistence through the whole zero-section batch flow
    // ============================================================

    @Test
    void batchAttendance_zeroSectionBatch_persistsSectionlessSessionAndRecords_throughFullFlow()
            throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("bat_flow@dagacs.local", "Pass@123", a.dept);
        Batch z = zeroSectionBatch(a);
        Student s1 = studentInBatch(a, z, "Student A1", "ENR-001");
        Student s2 = studentInBatch(a, z, "Student A2", "ENR-002");
        Student s3 = studentInBatch(a, z, "Student A3", "ENR-003");

        // Admin assigns the teacher to the zero-section batch via the real API.
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + t.getId()
                                + ",\"subjectOfferingId\":" + a.offering.getId()
                                + ",\"batchId\":" + z.getId() + "}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").value(z.getId().intValue()))
                .andExpect(jsonPath("$.sectionId").doesNotExist());

        String token = login("bat_flow@dagacs.local", "Pass@123");

        // My Classes exposes the new batch assignment.
        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Create a section-less session targeting the batch.
        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.sectionId").doesNotExist())
                .andExpect(jsonPath("$.batchId").value(z.getId().intValue()))
                .andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // The batch roster is the batch's students, enrollment-ordered.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].enrollmentNumber").value("ENR-001"))
                .andExpect(jsonPath("$[1].enrollmentNumber").value("ENR-002"))
                .andExpect(jsonPath("$[2].enrollmentNumber").value("ENR-003"));

        // Mark attendance against the section-less session.
        MvcResult marked = mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + s1.getId() + ",\"status\":\"PRESENT\"},"
                                + "{\"studentId\":" + s2.getId() + ",\"status\":\"ABSENT\"},"
                                + "{\"studentId\":" + s3.getId() + ",\"status\":\"PRESENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].sectionId").doesNotExist())
                .andExpect(jsonPath("$[0].batchId").value(z.getId().intValue()))
                .andReturn();
        JsonNode records = objectMapper.readTree(marked.getResponse().getContentAsString());
        long s2RecordId = records.get(1).get("id").asLong();

        // SCHEDULED session becomes CONDUCTED after the save.
        mockMvc.perform(get("/api/teacher/attendance/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONDUCTED"));

        // Records are readable within the assigned batch scope.
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // PERSISTENCE PROOF at the session level: section is NULL, batch is set.
        AttendanceSession persistedSession = attendanceSessionRepository.findById(sessionId).orElseThrow();
        assertNull(persistedSession.getSectionEntity());
        assertNull(persistedSession.getSection());
        assertNotNull(persistedSession.getBatchEntity());
        assertEquals(z.getId(), persistedSession.getBatchEntity().getId());
        assertEquals(z.getBatchCode(), persistedSession.getBatch());

        // PERSISTENCE PROOF at the record level: section_id is NULL, batch_id is set.
        List<AttendanceRecord> persistedRecords = attendanceRecordRepository.findBySessionId(sessionId);
        assertEquals(3, persistedRecords.size());
        for (AttendanceRecord r : persistedRecords) {
            assertNull(r.getSection());
            assertNotNull(r.getBatch());
            assertEquals(z.getId(), r.getBatch().getId());
        }

        // A real status change on a batch record writes an audit row whose
        // section snapshots are NULL and whose batch snapshot is set.
        mockMvc.perform(put("/api/teacher/attendance/" + s2RecordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PRESENT\",\"reason\":\"batch-mode retake\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"));
        List<AttendanceAuditLogDTO> audits = auditLogService.getAuditLogsByAttendanceId(s2RecordId);
        assertEquals(1, audits.size());
        AttendanceAuditLogDTO audit = audits.get(0);
        assertNull(audit.getSection());
        assertNull(audit.getSectionName());
        assertEquals(z.getBatchCode(), audit.getBatch());
        assertEquals(z.getBatchCode(), audit.getBatchName());

        // A second session on the same date stays a distinct column.
        MvcResult second = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP2\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long secondSessionId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + secondSessionId + ",\"items\":["
                                + "{\"studentId\":" + s1.getId() + ",\"status\":\"ABSENT\"},"
                                + "{\"studentId\":" + s2.getId() + ",\"status\":\"PRESENT\"},"
                                + "{\"studentId\":" + s3.getId() + ",\"status\":\"ABSENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Student-wise matrix over the batch: columns, rows, P/A totals, %.
        JsonNode matrix = fetchJson("/api/teacher/attendance/student-wise?subjectId=" + a.subject.getId()
                + "&batchId=" + z.getId(), token);
        assertEquals(z.getBatchCode(), matrix.get("batchCode").asText());
        assertEquals(2, matrix.get("columns").size());
        assertEquals(3, matrix.get("rows").size());
        assertEquals("ENR-001", matrix.get("rows").get(0).get("enrollmentNumber").asText());
        assertEquals("ENR-002", matrix.get("rows").get(1).get("enrollmentNumber").asText());
        assertEquals("ENR-003", matrix.get("rows").get(2).get("enrollmentNumber").asText());
        // s2 (ENR-002) is PRESENT in both sessions after the update -> 2/2 = 100%.
        assertEquals(2, matrix.get("rows").get(1).get("presentCount").asInt());
        assertEquals(2, matrix.get("rows").get(1).get("totalRecordedCount").asInt());
        assertEquals(100.0, matrix.get("rows").get(1).get("percentage").asDouble(), 0.001);
        assertEquals(2, matrix.get("rows").get(1).get("cells").size());
        assertEquals(2, matrix.get("rows").get(0).get("cells").size());

        // Exports carry the batch context.
        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.xlsx")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("batchId", String.valueOf(z.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        result.getResponse().getContentType()))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));

        mockMvc.perform(get("/api/teacher/attendance/student-wise/export.pdf")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("batchId", String.valueOf(z.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("application/pdf",
                        result.getResponse().getContentType()))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));
    }

    // ============================================================
    // 2. Session negatives
    // ============================================================

    @Test
    void createSession_bothSectionAndBatchId_returns400() throws Exception {
        Slice a = slice("A");
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"sectionId\":" + a.section.getId()
                                + ",\"batchId\":" + a.batch.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSession_neitherSectionNorBatch_returns400() throws Exception {
        Slice a = slice("A");
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSession_nonexistentBatch_returns404() throws Exception {
        Slice a = slice("A");
        createTeacherAccount("bat_404@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("bat_404@dagacs.local", "Pass@123");
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":999999"
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSession_batchWithSections_returns400() throws Exception {
        Slice a = slice("A");
        createTeacherAccount("bat_400@dagacs.local", "Pass@123", a.dept);
        flush();
        String token = login("bat_400@dagacs.local", "Pass@123");
        // a.batch structurally owns a.section -> batch mode must be rejected.
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + a.batch.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSession_duplicateBatchPeriod_returns409() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("bat_dup@dagacs.local", "Pass@123", a.dept);
        Batch z = zeroSectionBatch(a);
        assignBatch(t, a.offering, z);
        flush();
        String token = login("bat_dup@dagacs.local", "Pass@123");
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void createSession_unassignedTeacher_returns403() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("bat_noasgn@dagacs.local", "Pass@123", a.dept);
        Batch z = zeroSectionBatch(a);
        flush();
        String token = login("bat_noasgn@dagacs.local", "Pass@123");
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignViaApi_batchWithSections_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("bat_assgn@dagacs.local", "Pass@123", a.dept);
        flush();
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/admin/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + t.getId()
                                + ",\"subjectOfferingId\":" + a.offering.getId()
                                + ",\"batchId\":" + a.batch.getId() + "}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAttendance_studentOutsideBatch_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t = createTeacherAccount("bat_out@dagacs.local", "Pass@123", a.dept);
        Batch z = zeroSectionBatch(a);
        assignBatch(t, a.offering, z);
        Student inBatch = studentInBatch(a, z, "OK", "ENR-001");
        // A second zero-section batch owns an unrelated student.
        Batch zB = zeroSectionBatch(a);
        Student outside = studentInBatch(a, zB, "Outside", "ENR-900");
        flush();

        String token = login("bat_out@dagacs.local", "Pass@123");
        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // A legit batch student would be accepted (positive control).
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + inBatch.getId() + ",\"status\":\"PRESENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // A student from another batch is rejected for the batch session.
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + outside.getId() + ",\"status\":\"PRESENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // 3. HOD-as-teacher operation in batch mode
    // ============================================================

    @Test
    void hodLinkedAndAssignedToBatch_runsBatchAttendanceFlow() throws Exception {
        Slice a = slice("A");
        Teacher hod = makeHod(a);
        Batch z = zeroSectionBatch(a);
        assignBatch(hod, a.offering, z);
        Student s1 = studentInBatch(a, z, "S1", "ENR-001");
        Student s2 = studentInBatch(a, z, "S2", "ENR-002");
        flush();

        String token = login(HOD_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get("/api/teacher/assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectionId").doesNotExist())
                .andExpect(jsonPath("$.batchId").value(z.getId().intValue()))
                .andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"items\":["
                                + "{\"studentId\":" + s1.getId() + ",\"status\":\"PRESENT\"},"
                                + "{\"studentId\":" + s2.getId() + ",\"status\":\"PRESENT\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionId").doesNotExist())
                .andExpect(jsonPath("$[0].batchId").value(z.getId().intValue()));

        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("batchId", String.valueOf(z.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchCode").value(z.getBatchCode()))
                .andExpect(jsonPath("$.rows.length()").value(2));
    }

    @Test
    void hodUnassignedToBatch_cannotCreateSession_returns403() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Batch z = zeroSectionBatch(a);
        flush();
        String token = login(HOD_EMAIL, HOD_PASSWORD);
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + z.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 4. STUDENT role is blocked from batch operations
    // ============================================================

    @Test
    void studentRole_blockedFromBatchOperations_returns403() throws Exception {
        Slice a = slice("A");
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + a.subject.getId()
                                + ",\"batchId\":" + a.batch.getId()
                                + ",\"lecturePeriod\":\"LP1\",\"date\":\"2026-01-15\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/teacher/attendance/student-wise")
                        .param("subjectId", String.valueOf(a.subject.getId()))
                        .param("batchId", String.valueOf(a.batch.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

}