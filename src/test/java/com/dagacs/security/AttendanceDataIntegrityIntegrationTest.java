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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.13 real-JWT, real-MySQL acceptance tests proving attendance data-integrity
 * behavior end to end through the API:
 *
 * <ul>
 *   <li>Impossible calendar dates (e.g. 2024-02-31) are rejected with 400.</li>
 *   <li>Future session dates are rejected (400), today and backdated dates are
 *       accepted, and updating a session to a future date is rejected.</li>
 *   <li>The attendance roster returns only ACTIVE students.</li>
 *   <li>INACTIVE students cannot be newly marked (400, whole batch rolled back);
 *       pre-existing historical records of INACTIVE students remain corrigible
 *       and remain counted.</li>
 *   <li>Co-teacher semantics (OPTION B): a co-assigned teacher can mark into the
 *       session owner's session (markedBy = actual marker), the record stays
 *       under the owner's session, the owner's report includes it, and the
 *       co-teacher's own report excludes it.</li>
 * </ul>
 *
 * All fixtures are rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AttendanceDataIntegrityIntegrationTest {

    private static final String FUTURE_DATE = "2099-01-01";
    private static final String PAST_DATE = "2020-01-01";

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
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

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
        s.teacher = teacherRepository.save(Teacher.builder()
                .email("marker" + System.nanoTime() + "@dagacs.local").password("ignored")
                .fullName("Marker").phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false).department(s.dept)
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

    private Student student(Slice s, String name, String status) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E-" + System.nanoTime()).age(20)
                .admissionDate("2020-01-01").status(status)
                .email(null)
                .academicSession(s.session).batch(s.batch).section(s.section).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    private String sessionBody(Long subjectId, Long sectionId, String date, String period) {
        return "{\"subjectId\":" + subjectId
                + ",\"sectionId\":" + sectionId
                + ",\"lecturePeriod\":\"" + period
                + "\",\"date\":\"" + date + "\"}";
    }

    private String markBody(Long sessionId, Long studentId, String status) {
        return "{\"sessionId\":" + sessionId
                + ",\"items\":[{\"studentId\":" + studentId
                + ",\"status\":\"" + status + "\"}]}";
    }

    private Long createSession(String token, Long subjectId, Long sectionId, String date, String period)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(subjectId, sectionId, date, period))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    // ============================ Date validation ============================

    @Test
    void owner_createSession_impossibleCalendarDate_returns400() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913date@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        String token = login("m913date@dagacs.local", "Pass@123");

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), "2024-02-31", "P1"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void owner_createSession_futureDate_returns400() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913fut@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        String token = login("m913fut@dagacs.local", "Pass@123");

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(a.subject.getId(), a.section.getId(), FUTURE_DATE, "P1"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attendance date cannot be in the future"));
    }

    @Test
    void owner_createSession_todayAndBackdated_allowed() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913tday@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        String token = login("m913tday@dagacs.local", "Pass@123");

        createSession(token, a.subject.getId(), a.section.getId(), LocalDate.now().toString(), "P1");
        createSession(token, a.subject.getId(), a.section.getId(), PAST_DATE, "P2");
    }

    @Test
    void owner_updateSession_toFutureDate_returns400() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913upd@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        String token = login("m913upd@dagacs.local", "Pass@123");

        Long sessionId = createSession(token, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");

        mockMvc.perform(put("/api/teacher/attendance/sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturePeriod\":\"P1\",\"date\":\"" + FUTURE_DATE + "\",\"status\":\"SCHEDULED\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attendance date cannot be in the future"));
    }

    @Test
    void owner_updateSession_recordlessDateChange_toPast_allowed() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913mv@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        String token = login("m913mv@dagacs.local", "Pass@123");

        Long sessionId = createSession(token, a.subject.getId(), a.section.getId(),
                LocalDate.now().toString(), "P1");

        mockMvc.perform(put("/api/teacher/attendance/sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturePeriod\":\"P1\",\"date\":\"" + PAST_DATE + "\",\"status\":\"SCHEDULED\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(PAST_DATE));
    }

    // ============================ INACTIVE student policy ============================

    @Test
    void owner_roster_returnsOnlyActiveStudents() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913rod@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        Student inactive = student(a, "Inactive", "INACTIVE");
        student(a, "Active One", "ACTIVE");
        student(a, "Active Two", "ACTIVE");
        String token = login("m913rod@dagacs.local", "Pass@123");

        Long sessionId = createSession(token, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");

        MvcResult roster = mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andReturn();
        assertFalse(roster.getResponse().getContentAsString().contains(inactive.getRollNumber()));
    }

    @Test
    void owner_markInactiveStudent_returns400() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913ina@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        Student inactive = student(a, "Inactive", "INACTIVE");
        String token = login("m913ina@dagacs.local", "Pass@123");

        Long sessionId = createSession(token, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(markBody(sessionId, inactive.getId(), "PRESENT"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Student " + inactive.getRollNumber() + " is inactive and cannot be marked for attendance"));
    }

    @Test
    void owner_markBatchWithInactiveStudent_rollsBackAll() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913bar@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        Student active = student(a, "Active", "ACTIVE");
        Student inactive = student(a, "Inactive", "INACTIVE");
        String token = login("m913bar@dagacs.local", "Pass@123");

        Long sessionId = createSession(token, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");

        String batchBody = "{\"sessionId\":" + sessionId
                + ",\"items\":[{\"studentId\":" + active.getId() + ",\"status\":\"PRESENT\"},"
                + "{\"studentId\":" + inactive.getId() + ",\"status\":\"ABSENT\"}]}";

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0));
    }

    @Test
    void historicalRecordOfInactiveStudent_remainsCorrigibleAndCounted() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913hic@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        Student inactive = student(a, "Inactive", "INACTIVE");
        String token = login("m913hic@dagacs.local", "Pass@123");

        Long sessionId = createSession(token, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");
        AttendanceSession session = attendanceSessionRepository.findById(sessionId).orElseThrow();
        AttendanceRecord historical = attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(inactive)
                .subject(a.subject).section(a.section).markedBy(owner)
                .status("ABSENT").lecturePeriod("P1").date(PAST_DATE).isPresent(false)
                .createdAt(now()).build());

        mockMvc.perform(put("/api/teacher/attendance/" + historical.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PRESENT\",\"reason\":\"correction\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"));

        mockMvc.perform(get("/api/teacher/attendance/report?startDate=" + PAST_DATE + "&endDate=" + LocalDate.now())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subjectId == " + a.subject.getId() + ")].totalRecordedCount")
                        .value(1));
    }

    // ============================ Co-teacher semantics (OPTION B) ============================

    @Test
    void coTeacher_canMarkIntoOwnerSession_markedByIsActualMarker_reportsStayOwnerScoped() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913own@dagacs.local", a.dept);
        Teacher coTeacher = createTeacherAccount("m913cox@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        assign(coTeacher, a.offering, a.section);
        Student s1 = student(a, "Student One", "ACTIVE");
        Student s2 = student(a, "Student Two", "ACTIVE");

        String ownerToken = login("m913own@dagacs.local", "Pass@123");
        String coToken = login("m913cox@dagacs.local", "Pass@123");

        Long sessionId = createSession(ownerToken, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");

        // The co-teacher is authorized to mark into the owner's session.
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(markBody(sessionId, s1.getId(), "PRESENT"))
                        .header("Authorization", "Bearer " + coToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].markedById").value(coTeacher.getId()))
                .andExpect(jsonPath("$[0].isPresent").value(true));

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(markBody(sessionId, s2.getId(), "ABSENT"))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].markedById").value(owner.getId()));

        // The record stays under the owner's session (sessions are owner-created).
        mockMvc.perform(get("/api/teacher/attendance/sessions/" + sessionId + "/records")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2));

        // Owner's report includes both records (session-owner attribution).
        mockMvc.perform(get("/api/teacher/attendance/report?startDate=" + PAST_DATE + "&endDate=" + LocalDate.now())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subjectId == " + a.subject.getId() + ")].totalRecordedCount").value(2));

        // Co-teacher's own report excludes the owner's session entirely (OPTION B).
        mockMvc.perform(get("/api/teacher/attendance/report?startDate=" + PAST_DATE + "&endDate=" + LocalDate.now())
                        .header("Authorization", "Bearer " + coToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subjectId == " + a.subject.getId() + ")]").isEmpty());
    }

    @Test
    void unassignedTeacher_cannotMark_returns403() throws Exception {
        Slice a = slice("A");
        Teacher owner = createTeacherAccount("m913un1@dagacs.local", a.dept);
        Teacher intruder = createTeacherAccount("m913un2@dagacs.local", a.dept);
        assign(owner, a.offering, a.section);
        Student s1 = student(a, "Student One", "ACTIVE");

        String ownerToken = login("m913un1@dagacs.local", "Pass@123");
        String intruderToken = login("m913un2@dagacs.local", "Pass@123");

        Long sessionId = createSession(ownerToken, a.subject.getId(), a.section.getId(), PAST_DATE, "P1");

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(markBody(sessionId, s1.getId(), "PRESENT"))
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }
}