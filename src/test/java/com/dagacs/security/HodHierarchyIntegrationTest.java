package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.TeacherSubjectSectionAssignment;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance coverage for the M12 HOD academic hierarchy.
 *
 * <p>Proves the two properties the module depends on: the hierarchy is real and
 * genuinely cascading, and it can never leak another department. Department
 * scope is derived only from the authenticated HOD, so a HOD that hand-edits a
 * program/semester/section/subject/student id belonging to a second department
 * receives 403 and no data.</p>
 *
 * <p>The fixture deliberately gives <b>two departments and two programs per
 * department</b> (B.Tech and M.Tech) that use the <b>same semester names</b>,
 * so any leakage between a B.Tech semester and an M.Tech semester is caught
 * rather than masked by distinct labels.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodHierarchyIntegrationTest {

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
    private StudentRepository studentRepository;
    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private long sessionCounter = 0;

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    // ── Fixture ───────────────────────────────────────────────────────────

    /** A full academic slice for one (department, program, semester, section). */
    private static final class Slice {
        Department dept;
        Program program;
        AcademicSession session;
        Semester semester;
        Batch batch;
        Section section;
        Subject subject;
        Teacher teacher;
        List<SubjectOffering> offerings = new ArrayList<>();
    }

    private Slice slice(Department dept, String programName, String semesterName,
                        String subjectName) {
        Slice s = new Slice();
        s.dept = dept;
        s.program = programRepository.save(Program.builder()
                .name(programName).code("P" + System.nanoTime()).duration("4yr")
                .description("Test").department(dept).build());
        s.session = academicSessionRepository.save(AcademicSession.builder()
                .name("2026-27-" + programName).code("S" + System.nanoTime())
                .description("Test").program(s.program)
                .createdAt(now()).updatedAt(now()).build());
        s.semester = semesterRepository.save(Semester.builder()
                .name(semesterName).code("C" + System.nanoTime()).year(2026)
                .academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
        s.batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("Batch 1").year(2026)
                .program(programName).maxCapacity(60).academicSession(s.session)
                .createdAt(now()).updatedAt(now()).build());
        s.section = sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(s.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        s.subject = subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name(subjectName)
                .description("Test").creditHours("3").department(dept)
                .status("ACTIVE").createdAt(now()).updatedAt(now()).build());
        s.offerings.add(subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(s.subject).semester(s.semester)
                .createdAt(now()).updatedAt(now()).build()));
        s.teacher = teacherRepository.save(Teacher.builder()
                .email("t" + System.nanoTime() + "@dagacs.local").password("ignored")
                .fullName("Teacher " + programName).phone("").designation("Professor")
                .status("ACTIVE").avatarUrl("").isHod(false).department(dept)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    private Department department(String code) {
        return departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code(code).description("Test")
                .createdBy("test").createdAt(now()).updatedAt(now()).build());
    }

    private void makeHodA(Department dept) {
        teacherRepository.save(Teacher.builder()
                .email(HOD_A_EMAIL).password("ignored").fullName("HOD A")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void createHodB(Department dept) {
        String email = "hodB" + System.nanoTime() + "@dagacs.local";
        Role hodRole = roleRepository.findByName("HOD").orElseThrow();
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("Pass@123")).fullName("HOD B")
                .phone("").status("ACTIVE").role(hodRole).avatarUrl("")
                .createdAt(now()).updatedAt(now()).build());
        teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("HOD B Teacher")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Student student(Slice s, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .enrollmentNumber("E-" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE").email(null)
                .academicSession(s.session).batch(s.batch).section(s.section)
                .semester(s.semester).program(s.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void addRecords(Slice s, Student student, String date, int present, int total) {
        for (int i = 0; i < total; i++) {
            sessionCounter++;
            AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                    .subjectEntity(s.subject).sectionEntity(s.section).teacherEntity(s.teacher)
                    .subject(s.subject.getName()).section(s.section.getName())
                    .teacher(s.teacher.getFullName())
                    .lecturePeriod("LP" + sessionCounter).date(date).status("CONDUCTED")
                    .createdAt(now()).updatedAt(now()).build());
            boolean isPresent = i < present;
            attendanceRecordRepository.save(AttendanceRecord.builder()
                    .session(session).student(student)
                    .subject(s.subject).section(s.section).markedBy(s.teacher)
                    .status(isPresent ? "PRESENT" : "ABSENT")
                    .lecturePeriod(session.getLecturePeriod()).date(date).isPresent(isPresent)
                    .createdAt(now()).build());
        }
    }

    private void assign(Slice s, Section section) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(s.teacher).subjectOffering(s.offerings.get(0)).section(section)
                .createdAt(now()).updatedAt(now()).build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int statusOf(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private List<String> names(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(field).asText()));
        return values;
    }

    private long idOf(JsonNode array, int index, String field) {
        return array.get(index).get(field).asLong();
    }

    /** The standard two-department, two-program academic world used per test. */
    private static final class World {
        Department deptA;
        Department deptB;
        Slice btech;
        Slice mtech;
        Slice other;
    }

    private World world() {
        World w = new World();
        w.deptA = department("A");
        w.deptB = department("B");
        // Same semester name in B.Tech and M.Tech: catches any cross-program mix.
        w.btech = slice(w.deptA, "B.Tech CSE", "Semester 3", "DSA");
        w.mtech = slice(w.deptA, "M.Tech CSE", "Semester 3", "Advanced DSA");
        w.other = slice(w.deptB, "B.Tech ECE", "Semester 3", "Signals");
        makeHodA(w.deptA);
        createHodB(w.deptB);
        entityManager.flush();
        return w;
    }

    // ── 1. own hierarchy ───────────────────────────────────────────────────

    @Test
    void hodCanRetrieveItsOwnDepartmentHierarchy() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        JsonNode root = getJson("/api/hod/hierarchy", token);

        assertEquals(w.deptA.getId(), root.get("departmentId").asLong());
        assertEquals(2, root.get("programs").size(), "both department-A programs");

        List<String> programs = names(root.get("programs"), "name");
        assertTrue(programs.contains("B.Tech CSE"));
        assertTrue(programs.contains("M.Tech CSE"));
        assertFalse(programs.contains("B.Tech ECE"), "department B program must not appear");

        List<String> sessions = names(root.get("academicSessions"), "name");
        assertEquals(2, sessions.size(), "one session per department-A program");
        // Each session carries its program so the client cannot mix programs.
        for (JsonNode session : root.get("academicSessions")) {
            assertTrue(session.has("programId"));
            assertTrue(session.has("programName"));
        }
    }

    @Test
    void semestersAreScopedToTheSelectedSession() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        JsonNode semesters = getJson(
                "/api/hod/hierarchy/semesters?academicSessionId=" + w.btech.session.getId(), token);

        assertEquals(1, semesters.size());
        assertEquals("Semester 3", semesters.get(0).get("name").asText());
        assertEquals(w.btech.semester.getId(), idOf(semesters, 0, "id"));
    }

    // ── 9/10/14. filtering ────────────────────────────────────────────────

    @Test
    void sectionFilteringDerivesMembershipFromRealRelationships() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        // Enrolment truth: a student placed in B.Tech Semester 3 / Section A.
        student(w.btech, "Rahul");
        // Teaching truth: Section B of the same batch has no students yet, but a
        // teacher IS assigned to teach the B.Tech Semester 3 offering there.
        Section empty = sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("B").maxCapacity(30)
                .batch(w.btech.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        assign(w.btech, empty);
        // A section of the SAME session's batch but belonging to no Semester 3
        // relationship at all must not appear.
        Section unrelated = sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("C").maxCapacity(30)
                .batch(w.btech.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        entityManager.flush();

        String path = "/api/hod/hierarchy/sections?academicSessionId="
                + w.btech.session.getId() + "&semesterId=" + w.btech.semester.getId();
        JsonNode sections = getJson(path, token);

        List<String> names = names(sections, "name");
        assertEquals(2, names.size(), "enrolled section A + assigned-only section B");
        assertTrue(names.contains("A"));
        assertTrue(names.contains("B"));
        assertFalse(names.contains("C"), "no enrolment and no assignment => excluded");

        // Student count is real: only Section A has a student in this semester.
        for (JsonNode section : sections) {
            if ("A".equals(section.get("name").asText())) {
                assertEquals(1, section.get("studentCount").asLong());
            } else {
                assertEquals(0, section.get("studentCount").asLong());
            }
        }
    }

    @Test
    void subjectFilteringUsesSubjectOfferingAndFaculty() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        student(w.btech, "Rahul");
        assign(w.btech, w.btech.section);
        entityManager.flush();

        JsonNode subjects = getJson("/api/hod/hierarchy/subjects?semesterId="
                + w.btech.semester.getId() + "&sectionId=" + w.btech.section.getId(), token);

        assertEquals(1, subjects.size(), "only the B.Tech Semester 3 offering");
        assertEquals("DSA", subjects.get(0).get("name").asText());
        assertEquals("B.Tech CSE", subjects.get(0).get("programName").asText());
        assertEquals("Semester 3", subjects.get(0).get("semesterName").asText());
        assertEquals(1, subjects.get(0).get("facultyNames").size());
        assertEquals("Teacher B.Tech CSE", subjects.get(0).get("facultyNames").get(0).asText());
    }

    // ── 13. B.Tech / M.Tech separation ────────────────────────────────────

    @Test
    void btechAndMtechSemestersRemainSeparated() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        student(w.btech, "Btech Student");
        student(w.mtech, "Mtech Student");
        entityManager.flush();

        JsonNode btechSubjects = getJson("/api/hod/hierarchy/subjects?semesterId="
                + w.btech.semester.getId(), token);
        assertEquals(1, btechSubjects.size());
        assertEquals("DSA", btechSubjects.get(0).get("name").asText());

        JsonNode mtechSubjects = getJson("/api/hod/hierarchy/subjects?semesterId="
                + w.mtech.semester.getId(), token);
        assertEquals(1, mtechSubjects.size());
        assertEquals("Advanced DSA", mtechSubjects.get(0).get("name").asText());
        assertEquals("M.Tech CSE", mtechSubjects.get(0).get("programName").asText());

        // The scoped student list of the B.Tech semester must not contain the
        // identically-named M.Tech semester's student.
        JsonNode students = getJson("/api/hod/students?academicSessionId="
                + w.btech.session.getId() + "&programId=" + w.btech.program.getId()
                + "&semesterId=" + w.btech.semester.getId(), token);
        assertEquals(1, students.size());
        assertEquals("Btech Student", students.get(0).get("studentName").asText());
    }

    // ── 2–8. cross-department isolation ───────────────────────────────────

    @Test
    void departmentBHodSeesOnlyItsOwnHierarchy() throws Exception {
        World w = world();
        // Create the HOD B login user first, then authenticate.
        String hodBEmail = teacherRepository.findAll().stream()
                .filter(t -> "HOD B Teacher".equals(t.getFullName()))
                .map(Teacher::getEmail).findFirst().orElseThrow();
        String tokenB = login(hodBEmail, "Pass@123");
        String tokenA = login(HOD_A_EMAIL, HOD_PASSWORD);

        JsonNode rootB = getJson("/api/hod/hierarchy", tokenB);
        assertEquals(w.deptB.getId(), rootB.get("departmentId").asLong());
        assertTrue(names(rootB.get("programs"), "name").contains("B.Tech ECE"));
        assertFalse(names(rootB.get("programs"), "name").contains("B.Tech CSE"));

        assertEquals(200, statusOf("/api/hod/hierarchy", tokenA));
    }

    @Test
    void hodCannotAccessAnotherDepartmentsProgramSemesterOrSection() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        // Cross-department semesters endpoint.
        assertEquals(403, statusOf(
                "/api/hod/hierarchy/semesters?academicSessionId=" + w.other.session.getId(),
                token));
        assertEquals(403, statusOf(
                "/api/hod/hierarchy/sections?academicSessionId=" + w.other.session.getId(),
                token));
        assertEquals(403, statusOf(
                "/api/hod/hierarchy/subjects?semesterId=" + w.other.semester.getId(), token));
    }

    @Test
    void hodCannotAccessAnotherDepartmentsStudentsOrAttendance() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        Student foreign = student(w.other, "Foreign Student");
        addRecords(w.other, foreign, "2026-09-01", 5, 10);
        entityManager.flush();

        assertEquals(403, statusOf("/api/hod/students?academicSessionId="
                + w.other.session.getId() + "&semesterId=" + w.other.semester.getId(), token));
        assertEquals(403, statusOf("/api/hod/low-attendance?academicSessionId="
                + w.other.session.getId() + "&semesterId=" + w.other.semester.getId(), token));
        assertEquals(403, statusOf("/api/hod/sections?semesterId=" + w.other.semester.getId()
                + "&academicSessionId=" + w.btech.session.getId(), token));

        // The HOD's own scoped list never contains the foreign student.
        JsonNode students = getJson("/api/hod/students?academicSessionId="
                + w.btech.session.getId() + "&semesterId=" + w.btech.semester.getId(), token);
        for (JsonNode row : students) {
            assertFalse("Foreign Student".equals(row.get("studentName").asText()));
        }
    }

    @Test
    void crossDepartmentSectionIdManipulationIsRejected() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        student(w.btech, "Own Student");
        entityManager.flush();

        // A perfectly well-formed request that pairs department A's session with
        // department B's section: the section id itself is out of scope.
        assertEquals(403, statusOf("/api/hod/students?academicSessionId="
                + w.btech.session.getId() + "&semesterId=" + w.btech.semester.getId()
                + "&sectionId=" + w.other.section.getId(), token));

        // And a fully department-B context is likewise refused.
        assertEquals(403, statusOf("/api/hod/students?academicSessionId="
                + w.other.session.getId() + "&semesterId=" + w.other.semester.getId()
                + "&sectionId=" + w.other.section.getId(), token));
    }

    // ── 11/12/13. context-aware data views ────────────────────────────────

    @Test
    void studentsViewIsContextAwareAndKeepsDepartmentWideFallback() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        Student good = student(w.btech, "Rahul");
        Student weak = student(w.btech, "Amit");
        student(w.mtech, "Mtech Only");
        addRecords(w.btech, good, "2026-09-01", 8, 10);   // 80%
        addRecords(w.btech, weak, "2026-09-01", 5, 10);   // 50%
        entityManager.flush();

        String btechContext = "/api/hod/students?academicSessionId=" + w.btech.session.getId()
                + "&programId=" + w.btech.program.getId()
                + "&semesterId=" + w.btech.semester.getId();

        // Scoped to the B.Tech semester.
        JsonNode scoped = getJson(btechContext, token);
        assertEquals(2, scoped.size());
        for (JsonNode row : scoped) {
            assertTrue(row.hasNonNull("enrollmentNumber"));
            assertEquals("B.Tech CSE", row.get("programName").asText());
            assertEquals("Semester 3", row.get("semesterName").asText());
            assertTrue(row.hasNonNull("academicSessionName"));
            assertEquals("A", row.get("sectionName").asText());
        }

        // Scoped further to a section is still the same single section here, but
        // it must never widen beyond the selected context.
        assertEquals(2, getJson(btechContext + "&sectionId=" + w.btech.section.getId(), token).size());

        // Department-wide fallback is unchanged. Note the frozen query is
        // attendance-backed, so it lists only students who have at least one
        // AttendanceRecord - the student with no records is absent. The
        // context-scoped query above is student-backed and does include them.
        JsonNode all = getJson("/api/hod/students", token);
        assertEquals(2, all.size(), "backward-compatible department-wide view");
        // The fallback rows carry no invented academic context.
        assertFalse(all.get(0).has("programName"));
    }

    @Test
    void lowAttendanceViewIsContextAwareAndKeepsFixedThreshold() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        Student good = student(w.btech, "Rahul");
        Student weakBtech = student(w.btech, "Amit");
        Student weakMtech = student(w.mtech, "Maya");
        addRecords(w.btech, good, "2026-09-01", 8, 10);
        addRecords(w.btech, weakBtech, "2026-09-01", 5, 10);
        addRecords(w.mtech, weakMtech, "2026-09-01", 4, 10);
        entityManager.flush();

        JsonNode btechLow = getJson("/api/hod/low-attendance?academicSessionId="
                + w.btech.session.getId() + "&semesterId=" + w.btech.semester.getId(), token);
        assertEquals(1, btechLow.size(), "only the 50% B.Tech student");
        assertEquals("Amit", btechLow.get(0).get("studentName").asText());
        assertEquals(50.0, btechLow.get(0).get("percentage").asDouble(), 0.001);
        assertEquals("B.Tech CSE", btechLow.get(0).get("programName").asText());

        JsonNode mtechLow = getJson("/api/hod/low-attendance?academicSessionId="
                + w.mtech.session.getId() + "&semesterId=" + w.mtech.semester.getId(), token);
        assertEquals(1, mtechLow.size());
        assertEquals("Maya", mtechLow.get(0).get("studentName").asText());

        // Department-wide fallback keeps both weak students.
        assertEquals(2, getJson("/api/hod/low-attendance", token).size());
    }

    @Test
    void sectionsAndSubjectsViewsAreContextAware() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        Student rahul = student(w.btech, "Rahul");
        addRecords(w.btech, rahul, "2026-09-01", 8, 10);
        student(w.mtech, "Mtech Only");
        entityManager.flush();

        JsonNode sections = getJson("/api/hod/sections?academicSessionId="
                + w.btech.session.getId() + "&semesterId=" + w.btech.semester.getId(), token);
        assertEquals(1, sections.size());
        assertEquals("A", sections.get(0).get("sectionName").asText());
        assertEquals(1, sections.get(0).get("studentCount").asLong());
        assertEquals(80.0, sections.get(0).get("percentage").asDouble(), 0.001);

        JsonNode subjects = getJson("/api/hod/subjects?semesterId=" + w.btech.semester.getId()
                + "&sectionId=" + w.btech.section.getId(), token);
        assertEquals(1, subjects.size());
        assertEquals("DSA", subjects.get(0).get("subjectName").asText());
        assertEquals("B.Tech CSE", subjects.get(0).get("programName").asText());
        assertEquals(8, subjects.get(0).get("presentCount").asLong());
        assertEquals(10, subjects.get(0).get("totalRecordedCount").asLong());
    }

    @Test
    void studentWithNoRecordedAttendanceIsListedWithNullPercentage() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        student(w.btech, "No Records");
        entityManager.flush();

        JsonNode students = getJson("/api/hod/students?semesterId=" + w.btech.semester.getId()
                + "&academicSessionId=" + w.btech.session.getId(), token);
        assertEquals(1, students.size());
        assertEquals("No Records", students.get(0).get("studentName").asText());
        assertEquals(0, students.get(0).get("totalRecordedCount").asLong());
        assertFalse(students.get(0).has("percentage"), "null percentage, never 0%");
    }

    // ── 15. invalid combinations ──────────────────────────────────────────

    @Test
    void inconsistentCombinationsAreRejected() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        // Program that does not belong to the selected session.
        assertEquals(400, statusOf("/api/hod/hierarchy/sections?academicSessionId="
                + w.btech.session.getId() + "&programId=" + w.mtech.program.getId(), token));

        // Semester that does not belong to the selected session.
        assertEquals(400, statusOf("/api/hod/hierarchy/sections?academicSessionId="
                + w.btech.session.getId() + "&semesterId=" + w.mtech.semester.getId(), token));

        // A lone, self-consistent level is accepted: every level is
        // single-valued in this schema, so it is already unambiguous.
        assertEquals(200, statusOf("/api/hod/students?sectionId="
                + w.btech.section.getId(), token));
        assertEquals(200, statusOf("/api/hod/students?semesterId="
                + w.btech.semester.getId(), token));
    }

    // ── 16/17. role + authentication ──────────────────────────────────────

    @Test
    void anonymousCannotAccessTheHierarchyApi() throws Exception {
        world();
        assertEquals(401, statusOf("/api/hod/hierarchy", null));
        assertEquals(401, statusOf("/api/hod/hierarchy/semesters?academicSessionId=1", null));
    }

    @Test
    void nonHodRolesCannotAccessTheHierarchyApi() throws Exception {
        world();
        String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        String student = login(STUDENT_EMAIL, STUDENT_PASSWORD);

        assertEquals(403, statusOf("/api/hod/hierarchy", teacher));
        assertEquals(403, statusOf("/api/hod/hierarchy", student));
    }

    @Test
    void departmentIdIsNeverAcceptedAsAnAuthority() throws Exception {
        World w = world();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);

        // A crafted departmentId is ignored: the response is still department A.
        JsonNode root = getJson("/api/hod/hierarchy?departmentId=" + w.deptB.getId(), token);
        assertEquals(w.deptA.getId(), root.get("departmentId").asLong());
        assertFalse(names(root.get("programs"), "name").contains("B.Tech ECE"));
    }
}
