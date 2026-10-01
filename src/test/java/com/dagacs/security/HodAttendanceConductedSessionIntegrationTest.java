package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.TeacherSubjectSectionAssignment;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The denominator regression suite for the Phase 3 correction.
 *
 * <p>Before the correction a HOD report measured {@code Present /
 * COUNT(attendance_records)}, so an unmarked class silently vanished from the
 * denominator and a student with no marks at all reported a meaningless
 * {@code 0 / 0}. After the correction {@code Total Classes} is the count of
 * conducted {@code AttendanceSession}s and {@code Unmarked = Absent}.</p>
 *
 * <p>Each of the ten cases required by the correction specification is proved
 * here against real sessions and real marks - in particular TEST 1, which is
 * the case that separates the two definitions:
 * {@code 7 present + 2 absent + 1 unmarked out of 10 conducted = 7/10 = 70%},
 * and not {@code 7/9}, {@code 7/7} or {@code 7/record-count}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodAttendanceConductedSessionIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";

    /** All fixtures conduct on distinct March 2026 dates inside the range. */
    private static final LocalDate MARCH_BASE = LocalDate.of(2026, 3, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private AcademicSessionRepository academicSessionRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SubjectOfferingRepository subjectOfferingRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    // ── Fixture ───────────────────────────────────────────────────────────

    private static class World {
        Department dept;
        Program program;
        AcademicSession session;
        Semester semester;
        Batch batch;
        Section section;
        Subject subjectA;
        Subject subjectB;
        SubjectOffering offeringA;
        SubjectOffering offeringB;
        Teacher faculty;
    }

    private World world() {
        World w = new World();
        w.dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("DAG")
                .description("denominator tests")
                .createdBy("test").createdAt(now()).updatedAt(now()).build());
        w.program = programRepository.save(Program.builder()
                .name("B.Tech CSE").code("BTC").duration("4yr")
                .description("test").department(w.dept).build());
        w.session = academicSessionRepository.save(AcademicSession.builder()
                .name("Academic Session 2026-27").code("AS2627").program(w.program)
                .description("test").createdAt(now()).updatedAt(now()).build());
        w.semester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("SEM3").year(2026).academicSession(w.session)
                .createdAt(now()).updatedAt(now()).build());
        w.batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("BTech").year(2026)
                .program("B.Tech CSE").maxCapacity(120).academicSession(w.session)
                .createdAt(now()).updatedAt(now()).build());
        w.section = sectionRepository.save(Section.builder()
                .sectionCode("A-" + System.nanoTime()).name("A")
                .maxCapacity(60).batch(w.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        w.subjectA = subject("DSA", "Data Structures", w.dept);
        w.subjectB = subject("CN", "Computer Networks", w.dept);
        w.offeringA = offering(w.subjectA, w.semester);
        w.offeringB = offering(w.subjectB, w.semester);
        w.faculty = teacherRepository.save(Teacher.builder()
                .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(w.dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
        assign(w.faculty, w.offeringA, w.section);
        assign(w.faculty, w.offeringB, w.section);
        return w;
    }

    private Subject subject(String code, String name, Department dept) {
        // A plain, readable code: the column order is derived from it, and these
        // tests look columns up by code rather than by position.
        return subjectRepository.save(Subject.builder()
                .code(code).name(name).description("test")
                .creditHours("3").department(dept).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
    }

    private SubjectOffering offering(Subject subject, Semester semester) {
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(now()).updatedAt(now()).build());
    }

    private void assign(Teacher teacher, SubjectOffering offering, Section section) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).section(section)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Student student(World w, String enrollmentNumber, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("ROLL-" + System.nanoTime())
                .enrollmentNumber(enrollmentNumber)
                .name(name).gender("M").status("ACTIVE")
                .academicSession(w.session).batch(w.batch)
                .section(w.section).semester(w.semester).program(w.program)
                .createdAt(now()).updatedAt(now()).build());
    }

    /** Conducts {@code count} real classes on distinct consecutive dates. */
    private List<AttendanceSession> conduct(Subject subject, Section section, Teacher teacher,
                                           int fromDay, int count) {
        List<AttendanceSession> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sessions.add(attendanceSessionRepository.save(AttendanceSession.builder()
                    .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                    .subject(subject.getName()).section(section.getName())
                    .teacher(teacher.getFullName())
                    .lecturePeriod("LP1")
                    .date(MARCH_BASE.plusDays(fromDay + i).toString())
                    .status("CONDUCTED")
                    .createdAt(now()).updatedAt(now()).build()));
        }
        return sessions;
    }

    private void mark(AttendanceSession session, Student student, boolean present) {
        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(session.getSubjectEntity()).section(session.getSectionEntity())
                .markedBy(session.getTeacherEntity())
                .status(present ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod())
                .date(session.getDate()).isPresent(present)
                .createdAt(now()).build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 1 — 7 present + 2 absent + 1 unmarked out of 10 conducted = 70%
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test1_sevenPresentTwoAbsentOneUnmarkedOutOfTenConductedIsSeventyPercent()
            throws Exception {
        World w = world();
        Student alice = student(w, "E-001", "Alice");
        List<AttendanceSession> ten = conduct(w.subjectA, w.section, w.faculty, 0, 10);

        for (int i = 0; i < 7; i++) {
            mark(ten.get(i), alice, true);      // 7 PRESENT
        }
        for (int i = 7; i < 9; i++) {
            mark(ten.get(i), alice, false);     // 2 ABSENT
        }
        // ten.get(9) is deliberately left with NO record: the unmarked class.
        entityManager.flush();

        JsonNode row = singleRow(w);

        // Exactly the specification's arithmetic, and explicitly not 7/9.
        assertEquals(7L, row.get("totalPresent").asLong());
        assertEquals(10L, row.get("totalClasses").asLong(),
                "Total Classes must be the 10 conducted sessions, not the 9 marked ones");
        assertEquals(70.0, row.get("overallPercentage").asDouble(), 0.0001);

        JsonNode cell = cellFor(lastMatrix, row, "DSA");
        assertEquals(7L, cell.get("present").asLong());
        assertEquals(10L, cell.get("total").asLong());
        assertEquals(70.0, cell.get("percentage").asDouble(), 0.0001);

        // The same numbers in the student detail, from the same canonical layer.
        JsonNode detail = fetchJson(studentPath(w, alice), token());
        assertEquals(7L, detail.get("totalPresent").asLong());
        assertEquals(10L, detail.get("totalClasses").asLong());
        assertEquals(70.0, detail.get("overallPercentage").asDouble(), 0.0001);
        assertEquals(3L, subjectLineFor(detail, "DSA").get("notAttended").asLong(),
                "notAttended = 2 explicit absences + 1 unmarked class");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 2 — zero attendance records against 10 conducted = 0/10 = 0%
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test2_zeroAttendanceRecordsAgainstTenConductedIsZeroOverTen() throws Exception {
        World w = world();
        Student neha = student(w, "E-002", "Neha");
        conduct(w.subjectA, w.section, w.faculty, 0, 10);
        // No marks at all for Neha, in any of the ten classes.
        entityManager.flush();

        JsonNode row = singleRow(w);

        assertEquals(0L, row.get("totalPresent").asLong());
        assertEquals(10L, row.get("totalClasses").asLong(),
                "A zero-record student must still carry the full denominator");
        assertEquals(0.0, row.get("overallPercentage").asDouble(), 0.0001);
        assertTrue(cellFor(lastMatrix, row, "DSA").get("total").asLong() > 0,
                "A conducted subject must not report 0 / 0 when classes were conducted");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 3 — 6 present + 2 absent + 2 unmarked out of 10 conducted = 60%
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test3_sixPresentTwoAbsentTwoUnmarkedOutOfTenConductedIsSixtyPercent()
            throws Exception {
        World w = world();
        Student carol = student(w, "E-003", "Carol");
        List<AttendanceSession> ten = conduct(w.subjectA, w.section, w.faculty, 0, 10);

        for (int i = 0; i < 6; i++) {
            mark(ten.get(i), carol, true);      // 6 PRESENT
        }
        for (int i = 6; i < 8; i++) {
            mark(ten.get(i), carol, false);     // 2 ABSENT
        }
        // ten.get(8) and ten.get(9) carry no record: 2 unmarked classes.
        entityManager.flush();

        JsonNode row = singleRow(w);

        assertEquals(6L, row.get("totalPresent").asLong());
        assertEquals(10L, row.get("totalClasses").asLong());
        assertEquals(60.0, row.get("overallPercentage").asDouble(), 0.0001);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 4 — 32/40 and 24/30 aggregate to 56/70 = 80%
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test4_overallIsTheRatioOfSumsNotTheAverageOfSubjectPercentages() throws Exception {
        World w = world();
        Student rahul = student(w, "E-001", "Rahul");

        // Subject A: 40 conducted classes, 32 present  -> 80%
        List<AttendanceSession> a = conduct(w.subjectA, w.section, w.faculty, 0, 40);
        for (int i = 0; i < 32; i++) {
            mark(a.get(i), rahul, true);
        }
        // Subject B: 30 conducted classes, 24 present  -> 80%
        List<AttendanceSession> b = conduct(w.subjectB, w.section, w.faculty, 40, 30);
        for (int i = 0; i < 24; i++) {
            mark(b.get(i), rahul, true);
        }
        entityManager.flush();

        JsonNode matrix = fetchJson(matrixPath(w), token());
        JsonNode row = matrix.get("students").get(0);
        assertEquals(32L, cellFor(matrix, row, "DSA").get("present").asLong());
        assertEquals(40L, cellFor(matrix, row, "DSA").get("total").asLong());
        assertEquals(24L, cellFor(matrix, row, "CN").get("present").asLong());
        assertEquals(30L, cellFor(matrix, row, "CN").get("total").asLong());

        assertEquals(56L, row.get("totalPresent").asLong());
        assertEquals(70L, row.get("totalClasses").asLong());
        assertEquals(80.0, row.get("overallPercentage").asDouble(), 0.0001);
        // Both subjects happen to be 80%, so an average would also give 80%.
        // The totals above are the real assertion: the denominators were summed,
        // not the percentages.
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 5 — each subject column uses its own conducted-session denominator
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test5_eachSubjectColumnUsesItsOwnConductedSessionDenominator() throws Exception {
        World w = world();
        Student amit = student(w, "E-001", "Amit");

        // 40 classes of A with 32 present (80%) and 30 of B with 24 (80%) are the
        // symmetric case. Make it asymmetric so a shared denominator cannot pass:
        // A: 40 conducted / 36 present = 90%.  B: 10 conducted / 2 present = 20%.
        List<AttendanceSession> a = conduct(w.subjectA, w.section, w.faculty, 0, 40);
        for (int i = 0; i < 36; i++) {
            mark(a.get(i), amit, true);
        }
        List<AttendanceSession> b = conduct(w.subjectB, w.section, w.faculty, 40, 10);
        for (int i = 0; i < 2; i++) {
            mark(b.get(i), amit, true);
        }
        entityManager.flush();

        JsonNode row = singleRow(w);

        assertEquals(40L, cellFor(lastMatrix, row, "DSA").get("total").asLong());
        assertEquals(10L, cellFor(lastMatrix, row, "CN").get("total").asLong(),
                "A subject with fewer classes must use its own denominator");
        assertEquals(90.0, cellFor(lastMatrix, row, "DSA").get("percentage").asDouble(), 0.0001);
        assertEquals(20.0, cellFor(lastMatrix, row, "CN").get("percentage").asDouble(), 0.0001);

        // The overall is 38/50, which is neither 90% nor 20% nor their mean
        // (55%) - a shared or averaged denominator is ruled out.
        assertEquals(38L, row.get("totalPresent").asLong());
        assertEquals(50L, row.get("totalClasses").asLong());
        assertEquals(76.0, row.get("overallPercentage").asDouble(), 0.0001);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 6 — the date range filters the conducted sessions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test6_dateRangeExcludesConductedSessionsOutsideTheSelectedRange() throws Exception {
        World w = world();
        Student bob = student(w, "E-001", "Bob");

        // 4 classes in March and 6 in May.
        List<AttendanceSession> march = conduct(w.subjectA, w.section, w.faculty, 0, 4);
        for (int i = 0; i < 3; i++) {
            mark(march.get(i), bob, true);
        }
        for (int i = 0; i < 6; i++) {
            AttendanceSession may = attendanceSessionRepository.save(AttendanceSession.builder()
                    .subjectEntity(w.subjectA).sectionEntity(w.section)
                    .teacherEntity(w.faculty)
                    .subject(w.subjectA.getName()).section(w.section.getName())
                    .teacher(w.faculty.getFullName()).lecturePeriod("LP1")
                    .date(LocalDate.of(2026, 5, 1).plusDays(i).toString())
                    .status("CONDUCTED").createdAt(now()).updatedAt(now()).build());
            if (i < 4) {
                mark(may, bob, true);
            }
            // The last two May classes are unmarked but still count.
        }
        entityManager.flush();

        String token = token();
        String path = matrixPath(w);

        // Unrestricted: all 10 conducted classes.
        JsonNode all = fetchJson(path, token);
        assertEquals(10L, singleRowOf(all).get("totalClasses").asLong());
        assertEquals(7L, singleRowOf(all).get("totalPresent").asLong());
        assertEquals(70.0, singleRowOf(all).get("overallPercentage").asDouble(), 0.0001);

        // March only: the 6 May classes are excluded from the denominator.
        JsonNode marchOnly = fetchJson(path + "&startDate=2026-03-01&endDate=2026-03-31", token);
        assertEquals(3L, singleRowOf(marchOnly).get("totalPresent").asLong());
        assertEquals(4L, singleRowOf(marchOnly).get("totalClasses").asLong(),
                "Conducted classes outside the range must not enter the denominator");
        assertEquals(75.0, singleRowOf(marchOnly).get("overallPercentage").asDouble(), 0.0001);

        // May only: 4 present of 6 conducted.
        JsonNode mayOnly = fetchJson(path + "&startDate=2026-05-01&endDate=2026-05-31", token);
        assertEquals(4L, singleRowOf(mayOnly).get("totalPresent").asLong());
        assertEquals(6L, singleRowOf(mayOnly).get("totalClasses").asLong());
        assertEquals(66.6666, singleRowOf(mayOnly).get("overallPercentage").asDouble(), 0.001);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 7 — a cross-department session never contributes
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test7_crossDepartmentSessionsNeverContributeToTheDenominator() throws Exception {
        World w = world();
        Student leela = student(w, "E-001", "Leela");
        List<AttendanceSession> mine = conduct(w.subjectA, w.section, w.faculty, 0, 4);
        for (int i = 0; i < 3; i++) {
            mark(mine.get(i), leela, true);
        }
        entityManager.flush();

        JsonNode before = singleRow(w);
        assertEquals(4L, before.get("totalClasses").asLong());

        // A second department conducts 20 classes for its own section.
        Department otherDept = departmentRepository.save(Department.builder()
                .name("Other-" + System.nanoTime()).code("OTH").description("foreign")
                .createdBy("test").createdAt(now()).updatedAt(now()).build());
        Program otherProgram = programRepository.save(Program.builder()
                .name("Other Program").code("OP").duration("4yr")
                .description("test").department(otherDept).build());
        AcademicSession otherSession = academicSessionRepository.save(AcademicSession.builder()
                .name("Foreign Session").code("FS").program(otherProgram).description("test")
                .createdAt(now()).updatedAt(now()).build());
        Semester otherSemester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("FSEM3").year(2026).academicSession(otherSession)
                .createdAt(now()).updatedAt(now()).build());
        Batch otherBatch = batchRepository.save(Batch.builder()
                .batchCode("FB-" + System.nanoTime()).name("FB").year(2026)
                .program("Other").maxCapacity(60).academicSession(otherSession)
                .createdAt(now()).updatedAt(now()).build());
        Section otherSection = sectionRepository.save(Section.builder()
                .sectionCode("F1-" + System.nanoTime()).name("F1")
                .maxCapacity(60).batch(otherBatch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        Subject otherSubject = subject("FS901", "Foreign Subject", otherDept);
        SubjectOffering otherOffering = offering(otherSubject, otherSemester);
        Teacher otherTeacher = teacherRepository.save(Teacher.builder()
                .email("foreign-" + System.nanoTime() + "@dagacs.local").password("ignored")
                .fullName("Foreign Teacher").phone("").designation("Professor")
                .status("ACTIVE").avatarUrl("").department(otherDept).isHod(false)
                .createdAt(now()).updatedAt(now()).build());
        assign(otherTeacher, otherOffering, otherSection);
        Student foreignStudent = studentRepository.save(Student.builder()
                .rollNumber("FROLL-" + System.nanoTime()).enrollmentNumber("F-001")
                .name("Foreign Student").gender("M").status("ACTIVE")
                .academicSession(otherSession).batch(otherBatch).section(otherSection)
                .semester(otherSemester).program(otherProgram)
                .createdAt(now()).updatedAt(now()).build());
        List<AttendanceSession> foreign = conduct(otherSubject, otherSection, otherTeacher, 0, 20);
        for (int i = 0; i < 20; i++) {
            mark(foreign.get(i), foreignStudent, true);
        }
        entityManager.flush();

        JsonNode after = singleRow(w);
        assertEquals(4L, after.get("totalClasses").asLong(),
                "A cross-department session must not enter the denominator");
        assertEquals(3L, after.get("totalPresent").asLong());
        assertEquals(75.0, after.get("overallPercentage").asDouble(), 0.0001);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 8 — cross program / session / semester / section never contribute
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test8_sameDepartmentOtherProgramSessionSemesterOrSectionNeverContributes()
            throws Exception {
        World w = world();
        Student raj = student(w, "E-001", "Raj");
        List<AttendanceSession> mine = conduct(w.subjectA, w.section, w.faculty, 0, 4);
        for (int i = 0; i < 3; i++) {
            mark(mine.get(i), raj, true);
        }

        // Same department, a DIFFERENT program + academic session + semester +
        // batch + section: another 20 classes that must be invisible here.
        Program otherProgram = programRepository.save(Program.builder()
                .name("M.Tech CSE").code("MTC").duration("2yr")
                .description("test").department(w.dept).build());
        AcademicSession otherSession = academicSessionRepository.save(AcademicSession.builder()
                .name("Academic Session 2025-26").code("AS2526").program(otherProgram)
                .description("test").createdAt(now()).updatedAt(now()).build());
        Semester otherSemester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("SEM3").year(2025).academicSession(otherSession)
                .createdAt(now()).updatedAt(now()).build());
        Batch otherBatch = batchRepository.save(Batch.builder()
                .batchCode("OB-" + System.nanoTime()).name("OB").year(2025)
                .program("M.Tech").maxCapacity(60).academicSession(otherSession)
                .createdAt(now()).updatedAt(now()).build());
        Section otherSection = sectionRepository.save(Section.builder()
                .sectionCode("B-" + System.nanoTime()).name("B")
                .maxCapacity(60).batch(otherBatch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        Subject otherSubject = subject("MT501", "Advanced Subject", w.dept);
        SubjectOffering otherOffering = offering(otherSubject, otherSemester);
        assign(w.faculty, otherOffering, otherSection);
        Student otherStudent = studentRepository.save(Student.builder()
                .rollNumber("OROLL-" + System.nanoTime()).enrollmentNumber("M-001")
                .name("Other Student").gender("M").status("ACTIVE")
                .academicSession(otherSession).batch(otherBatch).section(otherSection)
                .semester(otherSemester).program(otherProgram)
                .createdAt(now()).updatedAt(now()).build());
        List<AttendanceSession> other = conduct(otherSubject, otherSection, w.faculty, 0, 20);
        for (int i = 0; i < 20; i++) {
            mark(other.get(i), otherStudent, true);
        }
        entityManager.flush();

        JsonNode matrix = fetchJson(matrixPath(w), token());
        assertEquals(1, matrix.get("students").size(),
                "Another program's student must not appear");
        JsonNode row = matrix.get("students").get(0);
        assertEquals(4L, row.get("totalClasses").asLong(),
                "Another program/session/semester/section must not enter the denominator");
        assertEquals(3L, row.get("totalPresent").asLong());
        assertEquals(75.0, row.get("overallPercentage").asDouble(), 0.0001);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 9 — a zero-record student stays visible
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test9_zeroRecordStudentRemainsVisibleAndIsMeasured() throws Exception {
        World w = world();
        student(w, "E-001", "Present Student");
        Student ghost = student(w, "E-002", "Never Marked");
        conduct(w.subjectA, w.section, w.faculty, 0, 10);
        entityManager.flush();

        String token = token();
        JsonNode matrix = fetchJson(matrixPath(w), token);
        assertEquals(2, matrix.get("students").size(),
                "A student with no records must remain in the matrix");

        JsonNode row = matrix.get("students").get(1);
        assertEquals("E-002", row.get("enrollmentNumber").asText());
        assertEquals(0L, row.get("totalPresent").asLong());
        assertEquals(10L, row.get("totalClasses").asLong());
        assertEquals(0.0, row.get("overallPercentage").asDouble(), 0.0001);

        // Also present in the overview and in the subject detail.
        JsonNode overview = fetchJson(
                "/api/hod/attendance/overview?academicSessionId=" + w.session.getId()
                        + "&programId=" + w.program.getId()
                        + "&semesterId=" + w.semester.getId()
                        + "&sectionId=" + w.section.getId(), token);
        assertEquals(2L, overview.get("totalStudents").asLong());

        JsonNode subject = fetchJson(
                "/api/hod/attendance/subject/" + w.subjectA.getId()
                        + "?academicSessionId=" + w.session.getId()
                        + "&programId=" + w.program.getId()
                        + "&semesterId=" + w.semester.getId()
                        + "&sectionId=" + w.section.getId(), token);
        assertEquals(2, subject.get("studentRows").size());
        assertEquals(10L, subject.get("studentRows").get(1).get("total").asLong());
        assertEquals(0.0, subject.get("studentRows").get(1).get("percentage").asDouble(), 0.0001);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 10 — no conducted sessions means N/A, never 0%
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test10_noConductedSessionsReportsNotApplicableRatherThanZeroPercent()
            throws Exception {
        World w = world();
        Student sunil = student(w, "E-001", "Sunil");
        // Students and offerings exist, but not one class has been conducted.
        entityManager.flush();

        String token = token();
        JsonNode matrix = fetchJson(matrixPath(w), token);
        JsonNode row = matrix.get("students").get(0);
        assertEquals(0L, row.get("totalPresent").asLong());
        assertEquals(0L, row.get("totalClasses").asLong());
        assertFalse(row.hasNonNull("overallPercentage"),
                "No conducted class means no percentage, never 0%");
        assertFalse(row.get("subjects").get(0).hasNonNull("percentage"));

        // Same in the overview, and the student is counted as having no data
        // rather than as being 0%.
        JsonNode overview = fetchJson(
                "/api/hod/attendance/overview?academicSessionId=" + w.session.getId()
                        + "&programId=" + w.program.getId()
                        + "&semesterId=" + w.semester.getId()
                        + "&sectionId=" + w.section.getId(), token);
        assertEquals(0L, overview.get("overallTotalClasses").asLong());
        assertFalse(overview.hasNonNull("overallPercentage"));
        assertEquals(1L, overview.get("noConductedClasses").asLong());
        assertEquals(0L, overview.get("belowThresholdCount").asLong(),
                "A student with no class is not 'below' anything");

        // ... and the subject table reports zero classes with no percentage.
        assertEquals(0L, overview.get("subjects").get(0).get("classesConducted").asLong());
        assertFalse(overview.get("subjects").get(0).hasNonNull("percentage"));

        // A low-attendance report does not invent a 0% for the student either.
        JsonNode low = fetchJson(
                "/api/hod/attendance/low?academicSessionId=" + w.session.getId()
                        + "&programId=" + w.program.getId()
                        + "&semesterId=" + w.semester.getId()
                        + "&sectionId=" + w.section.getId(), token);
        assertEquals(0, low.get("students").size());
        assertNotNull(sunil);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Consistency: every Phase 3 surface must report the same denominator
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void allPhase3SurfacesReportTheSameConductedSessionDenominator() throws Exception {
        World w = world();
        Student deepak = student(w, "E-001", "Deepak");

        List<AttendanceSession> a = conduct(w.subjectA, w.section, w.faculty, 0, 10);
        for (int i = 0; i < 7; i++) {
            mark(a.get(i), deepak, true);
        }
        // 3 classes of A stay unmarked.
        List<AttendanceSession> b = conduct(w.subjectB, w.section, w.faculty, 10, 4);
        for (int i = 0; i < 2; i++) {
            mark(b.get(i), deepak, true);
        }
        // 2 of B stay unmarked. Total: 9 present of 14 conducted = 64.3%,
        // which is genuinely below the application's fixed 75% threshold.
        entityManager.flush();

        String token = token();
        String context = "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.program.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.section.getId();

        // Matrix: 9 present of 14 conducted classes.
        JsonNode matrixRow = singleRow(w);
        assertEquals(9L, matrixRow.get("totalPresent").asLong());
        assertEquals(14L, matrixRow.get("totalClasses").asLong());
        double expected = 9.0 / 14.0 * 100.0;
        assertEquals(expected, matrixRow.get("overallPercentage").asDouble(), 0.0001);

        // Student detail: identical.
        JsonNode detail = fetchJson(
                "/api/hod/attendance/student/" + deepak.getId() + context, token);
        assertEquals(9L, detail.get("totalPresent").asLong());
        assertEquals(14L, detail.get("totalClasses").asLong());
        assertEquals(expected, detail.get("overallPercentage").asDouble(), 0.0001);

        // Subject detail for A: 7 of 10 conducted classes.
        JsonNode subjectA = fetchJson(
                "/api/hod/attendance/subject/" + w.subjectA.getId() + context, token);
        assertEquals(10L, subjectA.get("totalClasses").asLong());
        assertEquals(7L, subjectA.get("presentCount").asLong());
        assertEquals(70.0, subjectA.get("averageAttendance").asDouble(), 0.0001);

        // Overview: the single student contributes 9 of 14.
        JsonNode overview = fetchJson("/api/hod/attendance/overview" + context, token);
        assertEquals(9L, overview.get("overallPresentCount").asLong());
        assertEquals(14L, overview.get("overallTotalClasses").asLong());
        assertEquals(expected, overview.get("overallPercentage").asDouble(), 0.0001);

        // Low attendance: 64.3% < 75%, listed with the same numbers.
        JsonNode low = fetchJson("/api/hod/attendance/low" + context, token);
        assertEquals(1, low.get("students").size());
        assertEquals(9L, low.get("students").get(0).get("presentCount").asLong());
        assertEquals(14L, low.get("students").get(0).get("totalClasses").asLong());
        assertEquals(expected, low.get("students").get(0).get("percentage").asDouble(), 0.0001);
        // Both subjects are below the threshold for this student: A 70%, B 50%.
        assertEquals(2L, low.get("students").get(0).get("subjectsBelowThreshold").asLong());

        // The matrix export carries the same corrected data in both formats.
        exportMatrix(w, token, "xlsx");
        exportMatrix(w, token, "pdf");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String token() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + HOD_EMAIL + "\",\"password\":\""
                                + HOD_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String matrixPath(World w) {
        return "/api/hod/attendance/matrix"
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.program.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.section.getId();
    }

    private String studentPath(World w, Student student) {
        return "/api/hod/attendance/student/" + student.getId()
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.program.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.section.getId();
    }

    /** The only student's row of the matrix, asserting there is exactly one. */
    private JsonNode singleRow(World w) throws Exception {
        JsonNode matrix = fetchJson(matrixPath(w), token());
        lastMatrix = matrix;
        return singleRowOf(matrix);
    }

    /** The matrix of the most recent [singleRow] call. */
    private JsonNode lastMatrix;

    private static JsonNode singleRowOf(JsonNode matrix) {
        assertEquals(1, matrix.get("students").size(),
                "The fixture must produce exactly one student row");
        return matrix.get("students").get(0);
    }

    /**
     * The cell of [row] for the subject with [subjectCode].
     *
     * <p>Looked up by the real subject code rather than by column position, so a
     * change in the column ordering cannot silently make these assertions test
     * the wrong subject.</p>
     */
    private static JsonNode cellFor(JsonNode matrix, JsonNode row, String subjectCode) {
        for (int i = 0; i < matrix.get("subjects").size(); i++) {
            if (subjectCode.equals(matrix.get("subjects").get(i).get("subjectCode").asText())) {
                JsonNode cell = row.get("subjects").get(i);
                assertEquals(matrix.get("subjects").get(i).get("subjectId").asLong(),
                        cell.get("subjectId").asLong(),
                        "Cells must be aligned with the column list by position");
                return cell;
            }
        }
        throw new AssertionError("No column for subject " + subjectCode);
    }

    /** The subject line of a student detail for the given subject code. */
    private static JsonNode subjectLineFor(JsonNode detail, String subjectCode) {
        for (JsonNode line : detail.get("subjects")) {
            if (subjectCode.equals(line.get("subjectCode").asText())) {
                return line;
            }
        }
        throw new AssertionError("No subject line for " + subjectCode);
    }

    private byte[] exportMatrix(World w, String token, String format) throws Exception {
        String path = matrixPath(w).replace("/matrix?", "/matrix/export." + format + "?");
        MvcResult result = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        if ("xlsx".equals(format)) {
            // A real XLSX container, not a JSON body with the wrong content type.
            assertEquals('P', (char) bytes[0]);
            assertEquals('K', (char) bytes[1]);
        } else {
            assertEquals('%', (char) bytes[0]);
            assertEquals('P', (char) bytes[1]);
            assertEquals('D', (char) bytes[2]);
            assertEquals('F', (char) bytes[3]);
        }
        return bytes;
    }

    private JsonNode fetchJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
