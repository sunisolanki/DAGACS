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
import com.dagacs.repository.RoleRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance tests for the Phase 3 HOD attendance
 * intelligence API ({@code /api/hod/attendance/**}).
 *
 * <p>The fixture deliberately builds a full academic world so every isolation
 * rule can be attacked at once:</p>
 * <ul>
 *   <li>Department A (the HOD's) with <b>two programs</b> - B.Tech and M.Tech - so
 *       program separation is real;</li>
 *   <li><b>two academic sessions</b> of the B.Tech program, each offering the
 *       <b>same subject</b> CS301, so session separation is real;</li>
 *   <li><b>two sections</b> of the same session and semester, so section
 *       separation is real;</li>
 *   <li>a second semester whose only subject is never offered in the selected
 *       one, so "no subjects from another semester" is real;</li>
 *   <li>a completely separate Department B with its own student, subject and
 *       attendance, so cross-department access is real;</li>
 *   <li>one student with <b>zero</b> attendance records and one subject whose
 *       classes all fall outside the date range used by some tests, so the
 *       "visible with 0 / 0" and "no fabricated data" rules are real.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodAttendanceReportIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String TEACHER_EMAIL = "teacher@dagacs.local";
    private static final String TEACHER_PASSWORD = "Teacher@123";
    private static final String STUDENT_EMAIL = "student@dagacs.local";
    private static final String STUDENT_PASSWORD = "Student@123";
    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";

    /** CS301 classes: 05, 06, 07, 08 January 2026. */
    private static final String[] JAN_DATES = {
            "2026-01-05", "2026-01-06", "2026-01-07", "2026-01-08"};
    /** CS302 classes: 01 and 02 February 2026 - outside the January range. */
    private static final String[] FEB_DATES = {"2026-02-01", "2026-02-02"};

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

    // â”€â”€ Fixture â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Department A: the HOD's own world. */
    private static class World {
        Department dept;
        Program btech;
        Program mtech;
        AcademicSession session;          // 2026-27, B.Tech
        AcademicSession prevSession;      // 2025-26, B.Tech (same subject offered)
        AcademicSession mtechSession;     // 2026-27, M.Tech
        Semester semester;                // Semester 3 of session
        Semester otherSemester;           // Semester 1 of session
        Semester prevSemester;            // Semester 3 of prevSession
        Semester mtechSemester;
        Batch batch;
        Section sectionA;
        Section sectionB;
        Section prevSection;
        Section mtechSection;
        Subject cs301;
        Subject cs302;
        Subject cs101;                    // offered only in otherSemester
        Subject mt501;                    // M.Tech subject
        SubjectOffering cs301InSemester;
        SubjectOffering cs302InSemester;
        SubjectOffering cs301InPrevSemester;
        SubjectOffering mt501InMtechSemester;
        Teacher faculty;
    }

    /** Department B: a second, foreign department. */
    private static class Foreign {
        Department dept;
        Program program;
        AcademicSession session;
        Semester semester;
        Section section;
        Subject subject;
        Student student;
    }

    private World world() {
        World w = new World();
        w.dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("DAG")
                .description("Phase 3 HOD department")
                .createdBy("test").createdAt(now()).updatedAt(now()).build());
        w.btech = programRepository.save(Program.builder()
                .name("B.Tech CSE").code("BTC").duration("4yr")
                .description("test").department(w.dept).build());
        w.mtech = programRepository.save(Program.builder()
                .name("M.Tech CSE").code("MTC").duration("2yr")
                .description("test").department(w.dept).build());
        w.session = session(w.btech, "Academic Session 2026-27", "AS2627");
        w.prevSession = session(w.btech, "Academic Session 2025-26", "AS2526");
        w.mtechSession = session(w.mtech, "M.Tech Session 2026-27", "MS2627");

        w.semester = semester(w.session, "Semester 3", "SEM3", 2026);
        w.otherSemester = semester(w.session, "Semester 1", "SEM1", 2026);
        w.prevSemester = semester(w.prevSession, "Semester 3", "SEM3", 2025);
        w.mtechSemester = semester(w.mtechSession, "Semester 1", "MSEM1", 2026);

        w.batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("BTech 2026").year(2026)
                .program("B.Tech CSE").maxCapacity(120).academicSession(w.session)
                .createdAt(now()).updatedAt(now()).build());
        w.sectionA = section(w.batch, "A");
        w.sectionB = section(w.batch, "B");
        Batch prevBatch = batchRepository.save(Batch.builder()
                .batchCode("PBat-" + System.nanoTime()).name("BTech 2025").year(2025)
                .program("B.Tech CSE").maxCapacity(120).academicSession(w.prevSession)
                .createdAt(now()).updatedAt(now()).build());
        // Its own section in the previous academic session, so a class of the
        // same subject can never collide with this session's classes.
        w.prevSection = section(prevBatch, "A");
        Batch mtechBatch = batchRepository.save(Batch.builder()
                .batchCode("MBat-" + System.nanoTime()).name("MTech 2026").year(2026)
                .program("M.Tech CSE").maxCapacity(60).academicSession(w.mtechSession)
                .createdAt(now()).updatedAt(now()).build());
        w.mtechSection = section(mtechBatch, "M1");

        w.cs301 = subject("CS301", "Data Structures and Algorithms", w.dept);
        w.cs302 = subject("CS302", "Computer Networks", w.dept);
        w.cs101 = subject("CS101", "Programming Fundamentals", w.dept);
        w.mt501 = subject("MT501", "Advanced Machine Learning", w.dept);

        w.cs301InSemester = offering(w.cs301, w.semester);
        w.cs302InSemester = offering(w.cs302, w.semester);
        w.cs301InPrevSemester = offering(w.cs301, w.prevSemester);
        w.mt501InMtechSemester = offering(w.mt501, w.mtechSemester);
        offering(w.cs101, w.otherSemester);

        w.faculty = teacherRepository.save(Teacher.builder()
                .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(w.dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());

        assign(w.faculty, w.cs301InSemester, w.sectionA);
        assign(w.faculty, w.cs302InSemester, w.sectionA);
        assign(w.faculty, w.mt501InMtechSemester, w.mtechSection);
        return w;
    }

    private Foreign foreignDepartment() {
        Foreign f = new Foreign();
        f.dept = departmentRepository.save(Department.builder()
                .name("Other-" + System.nanoTime()).code("OTH")
                .description("Foreign department")
                .createdBy("test").createdAt(now()).updatedAt(now()).build());
        f.program = programRepository.save(Program.builder()
                .name("Other Program").code("OP").duration("4yr")
                .description("test").department(f.dept).build());
        f.session = session(f.program, "Foreign Session", "FS");
        f.semester = semester(f.session, "Semester 3", "FSEM3", 2026);
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("FB-" + System.nanoTime()).name("FB").year(2026)
                .program("Other").maxCapacity(60).academicSession(f.session)
                .createdAt(now()).updatedAt(now()).build());
        f.section = section(batch, "F1");
        f.subject = subject("FS901", "Foreign Subject", f.dept);
        SubjectOffering offering = offering(f.subject, f.semester);
        Teacher foreignTeacher = teacherRepository.save(Teacher.builder()
                .email("foreign-teacher-" + System.nanoTime() + "@dagacs.local")
                .password("ignored").fullName("Foreign Teacher")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(f.dept).isHod(false)
                .createdAt(now()).updatedAt(now()).build());
        assign(foreignTeacher, offering, f.section);
        f.student = student(f.session, f.semester, f.section, f.program, "FOR-9001", "Foreign Student");
        markAll(conductClasses(f.subject, f.section, foreignTeacher, 2, JAN_DATES), f.student, 2);
        return f;
    }

    // â”€â”€ Matrix fixture data â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * The attendance shape asserted throughout:
     * <pre>
     * section A, semester 3, session 2026-27:
     *   CS301  4 classes in January   E-001: 4 present | E-002: no records | E-003: 1 present
     *   CS302  2 classes in February  E-001: 1 present | E-002: no records | E-003: 2 present
     * section B, same semester:  E-004 recorded in CS301
     * previous session:            E-005 recorded in CS301
     * M.Tech:                      M-001 recorded in MT501
     * </pre>
     */
    private static class Fixture {
        Student alice;   // E-001: 5 present / 6 recorded  -> 83.33%
        Student nobody;  // E-002: zero attendance records -> must stay visible
        Student carol;   // E-003: 3 present / 6 recorded  -> 50.00% (below 75%)
        Student otherSection;   // E-004, section B
        Student otherSession;   // E-005, previous academic session
        Student mtechStudent;   // M-001, M.Tech program
    }

    private Fixture populate(World w) {
        Fixture f = new Fixture();
        f.alice = student(w.session, w.semester, w.sectionA, w.btech, "E-001", "Alice");
        f.nobody = student(w.session, w.semester, w.sectionA, w.btech, "E-002", "No Records");
        f.carol = student(w.session, w.semester, w.sectionA, w.btech, "E-003", "Carol");
        f.otherSection = student(w.session, w.semester, w.sectionB, w.btech, "E-004", "Section B Student");
        f.otherSession = student(w.prevSession, w.prevSemester, w.prevSection, w.btech,
                "E-005", "Previous Session Student");
        f.mtechStudent = student(w.mtechSession, w.mtechSemester, w.mtechSection, w.mtech,
                "M-001", "M Tech Student");

        // A class is a real session shared by every student of the class, so the
        // sessions are created once and each student's mark is then recorded
        // against them. Creating a fresh session per student would fabricate
        // extra conducted classes and break the unique class constraint.
        List<AttendanceSession> cs301SectionA = conductClasses(w.cs301, w.sectionA, w.faculty, 4, JAN_DATES);
        List<AttendanceSession> cs302SectionA = conductClasses(w.cs302, w.sectionA, w.faculty, 2, FEB_DATES);
        List<AttendanceSession> cs301SectionB = conductClasses(w.cs301, w.sectionB, w.faculty, 4, JAN_DATES);
        List<AttendanceSession> cs301Prev = conductClasses(w.cs301, w.prevSection, w.faculty, 4, JAN_DATES);
        List<AttendanceSession> mt501Mtech = conductClasses(w.mt501, w.mtechSection, w.faculty, 2, JAN_DATES);

        markAll(cs301SectionA, f.alice, 4);
        markAll(cs302SectionA, f.alice, 1);
        markAll(cs301SectionA, f.carol, 1);
        markAll(cs302SectionA, f.carol, 2);
        markAll(cs301SectionB, f.otherSection, 4);
        markAll(cs301Prev, f.otherSession, 4);
        markAll(mt501Mtech, f.mtechStudent, 2);

        entityManager.flush();
        return f;
    }

    // â”€â”€ 1/2/3. Matrix correctness, dynamic subjects, correct students â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_correctCrossTabForAValidAcademicContext() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, null), token);

        // Metadata
        assertEquals(w.session.getId(), matrix.get("context").get("academicSessionId").asLong());
        assertEquals(w.semester.getId(), matrix.get("context").get("semesterId").asLong());
        assertEquals(w.sectionA.getId(), matrix.get("context").get("sectionId").asLong());
        assertEquals(3L, matrix.get("totalElements").asLong());
        assertEquals(75.0, matrix.get("thresholdPercentage").asDouble(), 0.0001);
        assertFalse(matrix.get("singleSubject").asBoolean());

        // Dynamic subject columns: only the selected semester's offerings.
        // CS101 belongs to semester 1 and MT501 to M.Tech - neither may appear.
        List<Long> columnIds = idsOf(matrix.get("subjects"), "subjectId");
        assertEquals(2, columnIds.size());
        assertTrue(columnIds.contains(w.cs301.getId()));
        assertTrue(columnIds.contains(w.cs302.getId()));
        assertEquals(List.of("CS301", "CS302"), codesOf(matrix.get("subjects")));
        assertEquals("Data Structures and Algorithms",
                matrix.get("subjects").get(0).get("subjectName").asText());

        // Students: only the selected section + semester + session + program.
        assertEquals(List.of("Alice", "No Records", "Carol"),
                namesOf(matrix.get("students")));
        assertEquals(List.of("E-001", "E-002", "E-003"),
                enrollmentOf(matrix.get("students")));
        assertFalse(idSetOf(matrix.get("students"), "studentId").contains(fresh(f.mtechStudent)));
        assertFalse(idSetOf(matrix.get("students"), "studentId").contains(fresh(f.otherSection)));
        assertFalse(idSetOf(matrix.get("students"), "studentId").contains(fresh(f.otherSession)));

        // Alice: CS301 4/4, CS302 1/2 -> 5/6 = 83.33% (a ratio of sums).
        JsonNode alice = rowFor(matrix.get("students"), "E-001");
        assertEquals(4L, alice.get("subjects").get(0).get("present").asLong());
        assertEquals(4L, alice.get("subjects").get(0).get("total").asLong());
        assertEquals(100.0, alice.get("subjects").get(0).get("percentage").asDouble(), 0.0001);
        assertEquals(1L, alice.get("subjects").get(1).get("present").asLong());
        assertEquals(2L, alice.get("subjects").get(1).get("total").asLong());
        assertEquals(50.0, alice.get("subjects").get(1).get("percentage").asDouble(), 0.0001);
        assertEquals(5L, alice.get("totalPresent").asLong());
        assertEquals(6L, alice.get("totalClasses").asLong());
        assertEquals(83.3333, alice.get("overallPercentage").asDouble(), 0.001);

        // Carol: CS301 1/4, CS302 2/2 -> 3/6 = 50% (below 75%).
        JsonNode carol = rowFor(matrix.get("students"), "E-003");
        assertEquals(25.0, carol.get("subjects").get(0).get("percentage").asDouble(), 0.0001);
        assertEquals(3L, carol.get("totalPresent").asLong());
        assertEquals(6L, carol.get("totalClasses").asLong());
        assertEquals(50.0, carol.get("overallPercentage").asDouble(), 0.0001);
    }

    // â”€â”€ 4. A student with zero attendance records stays visible â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_studentWithZeroAttendanceRecordsIsStillVisible() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, null), token);
        JsonNode nobody = rowFor(matrix.get("students"), "E-002");
        assertNotNull(nobody, "A student with no attendance records must not be dropped");
        assertEquals(fresh(f.nobody), nobody.get("studentId").asLong());

        // THE CORRECTION: a zero-record student keeps the full conducted-session
        // denominator. 4 CS301 + 2 CS302 = 6 classes, all unmarked for them.
        assertEquals(0L, nobody.get("totalPresent").asLong());
        assertEquals(6L, nobody.get("totalClasses").asLong());
        assertEquals(0.0, nobody.get("overallPercentage").asDouble(), 0.0001);

        for (int i = 0; i < nobody.get("subjects").size(); i++) {
            JsonNode cell = nobody.get("subjects").get(i);
            assertEquals(matrix.get("subjects").get(i).get("subjectId").asLong(),
                    cell.get("subjectId").asLong());
            assertEquals(0L, cell.get("present").asLong());
            // Not 0 / 0: each subject keeps its own conducted-class denominator.
            assertTrue(cell.get("total").asLong() > 0,
                    "A conducted subject must not report a zero denominator");
            assertEquals(0.0, cell.get("percentage").asDouble(), 0.0001);
        }
    }

    // â”€â”€ 5. A subject with no classes fabricates nothing â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_subjectWithNoClassesReportsZeroOverZeroAndNoPercentage() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // Restrict to January: CS302 only has February classes, so it keeps its
        // column but has nothing to report.
        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, null) + "&startDate=2026-01-01&endDate=2026-01-31",
                token);

        assertEquals(List.of("CS301", "CS302"), codesOf(matrix.get("subjects")));
        JsonNode alice = rowFor(matrix.get("students"), "E-001");
        JsonNode february = alice.get("subjects").get(1);
        assertEquals("CS302", columnCodeAt(matrix, 1));
        assertEquals(0L, february.get("present").asLong());
        assertEquals(0L, february.get("total").asLong());
        assertFalse(february.hasNonNull("percentage"));
        assertEquals(4L, alice.get("totalPresent").asLong());
        assertEquals(4L, alice.get("totalClasses").asLong());
    }

    // â”€â”€ 6/7/8. Totals and overall percentage â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_totalsAreTheSumOfTheRowsOwnCellsAndNotAnAverageOfPercentages() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, null), token);
        boolean guardedAgainstAnAverage = false;
        for (JsonNode row : matrix.get("students")) {
            long expectedPresent = 0L;
            long expectedTotal = 0L;
            for (JsonNode cell : row.get("subjects")) {
                expectedPresent += cell.get("present").asLong();
                expectedTotal += cell.get("total").asLong();
            }
            assertEquals(expectedPresent, row.get("totalPresent").asLong());
            assertEquals(expectedTotal, row.get("totalClasses").asLong());

            double naiveAverage = 0;
            for (JsonNode cell : row.get("subjects")) {
                naiveAverage += cell.get("percentage").asDouble();
            }
            naiveAverage /= row.get("subjects").size();
            double ratio = (double) expectedPresent / expectedTotal * 100.0;
            assertEquals(ratio, row.get("overallPercentage").asDouble(), 0.0001);
            if (Math.abs(naiveAverage - ratio) > 0.0001) {
                guardedAgainstAnAverage = true;
            }
        }
        // At least one row must be able to tell the two calculations apart, so a
        // regression back to "average the subject percentages" is detectable.
        assertTrue(guardedAgainstAnAverage,
                "The fixture must distinguish a ratio of sums from an average of percentages");
    }

    // â”€â”€ 9/10/11. Program, session and section separation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_btechAndMtechNeverMix() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, null), token);
        for (JsonNode row : matrix.get("students")) {
            assertFalse(row.get("studentId").asLong() == fresh(f.mtechStudent),
                    "An M.Tech student leaked into the B.Tech matrix");
        }
        assertFalse(codesOf(matrix.get("subjects")).contains("MT501"));
        Set<Long> allowedColumnIds = Set.of(w.cs301.getId(), w.cs302.getId());
        for (JsonNode column : matrix.get("subjects")) {
            assertTrue(allowedColumnIds.contains(column.get("subjectId").asLong()),
                    "A subject from another program became a column");
        }
    }

    @Test
    void matrix_academicSessionsNeverMixEvenForTheSameSubject() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, null), token);
        assertFalse(idSetOf(matrix.get("students"), "studentId").contains(fresh(f.otherSession)));
        // The previous session's CS301 records must not count towards this one.
        JsonNode alice = rowFor(matrix.get("students"), "E-001");
        assertEquals(4L, alice.get("subjects").get(0).get("total").asLong());

        // The same subject offered in the previous session keeps its own report.
        JsonNode previous = fetchJson(
                matrixPath(w.prevSession, w.btech, w.prevSemester, w.prevSection, null), token);
        assertTrue(enrollmentOf(previous.get("students")).contains("E-005"));
        assertFalse(enrollmentOf(previous.get("students")).contains("E-001"));
    }

    @Test
    void matrix_sectionsNeverMix() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode sectionA = fetchJson(matrixPath(w, w.sectionA, null), token);
        assertEquals(List.of("Alice", "No Records", "Carol"), namesOf(sectionA.get("students")));

        JsonNode sectionB = fetchJson(matrixPath(w, w.sectionB, null), token);
        assertEquals(List.of("E-004"), enrollmentOf(sectionB.get("students")));
        assertEquals(fresh(f.otherSection), sectionB.get("students").get(0).get("studentId").asLong());
    }

    // â”€â”€ 12. Subject-specific report â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_subjectSelectedProducesAFocusedSingleColumnMatrix() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode matrix = fetchJson(matrixPath(w, w.sectionA, w.cs302), token);
        assertTrue(matrix.get("singleSubject").asBoolean());
        assertEquals(1, matrix.get("subjects").size());
        assertEquals("CS302", matrix.get("subjects").get(0).get("subjectCode").asText());
        for (JsonNode row : matrix.get("students")) {
            assertEquals(1, row.get("subjects").size());
        }
        JsonNode alice = rowFor(matrix.get("students"), "E-001");
        assertEquals(1L, alice.get("totalPresent").asLong());
        assertEquals(2L, alice.get("totalClasses").asLong());
        assertEquals(50.0, alice.get("overallPercentage").asDouble(), 0.0001);
    }

    // â”€â”€ 13. Low attendance report with subject breakdown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void lowAttendance_usesTheContextAndNamesTheSubjectsResponsible() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode report = fetchJson(lowPath(w, w.sectionA), token);
        assertEquals(75.0, report.get("thresholdPercentage").asDouble(), 0.0001);
        assertEquals(3, report.get("totalStudents").asInt());

        // Alice is at 83.33% and stays out. Carol (50%) and the zero-record
        // student (0% against 6 conducted classes) are both genuinely below.
        assertEquals(2L, report.get("belowThresholdCount").asLong());
        assertEquals(2, report.get("students").size());

        JsonNode carol = studentNamed(report.get("students"), "Carol");
        assertNotNull(carol);
        assertEquals("E-003", carol.get("enrollmentNumber").asText());
        assertEquals("A", carol.get("sectionName").asText());
        assertEquals(3L, carol.get("presentCount").asLong());
        // CORRECTED: the denominator is conducted classes (4 + 2), not records.
        assertEquals(6L, carol.get("totalClasses").asLong());
        assertEquals(50.0, carol.get("percentage").asDouble(), 0.0001);

        // Only CS301 (1/4 = 25%) drags her down; CS302 is at 2/2 = 100%.
        assertEquals(1L, carol.get("subjectsBelowThreshold").asLong());
        assertEquals(1, carol.get("belowThresholdSubjects").size());
        assertEquals("CS301", carol.get("belowThresholdSubjects").get(0).get("subjectCode").asText());
        assertEquals(1L, carol.get("belowThresholdSubjects").get(0).get("present").asLong());
        assertEquals(4L, carol.get("belowThresholdSubjects").get(0).get("total").asLong());
        assertEquals(25.0, carol.get("belowThresholdSubjects").get(0).get("percentage").asDouble(), 0.0001);
    }

    @Test
    void lowAttendance_excludesStudentsAtOrAboveTheExistingThreshold() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode report = fetchJson(lowPath(w, w.sectionA), token);
        // Alice is at 5/6 = 83.33% and is correctly excluded: the threshold is
        // still the application's fixed 75% and was not changed by the correction.
        assertNull(studentNamed(report.get("students"), "Alice"));

        // The zero-record student is at 0/6 = 0%, so they ARE below the threshold.
        // Before the correction they were invisible because they had no records.
        JsonNode nobody = studentNamed(report.get("students"), "No Records");
        assertNotNull(nobody,
                "A zero-record student has a real 0% against conducted classes");
        assertEquals(0L, nobody.get("presentCount").asLong());
        assertEquals(6L, nobody.get("totalClasses").asLong());
        assertEquals(0.0, nobody.get("percentage").asDouble(), 0.0001);
        // Both conducted subjects are below the threshold for this student.
        assertEquals(2L, nobody.get("subjectsBelowThreshold").asLong());
    }

    // â”€â”€ 14. Student detail â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void studentDetail_returnsSubjectWiseBreakdownInsideTheCurrentContext() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode detail = fetchJson(
                studentPath(w, w.sectionA, fresh(f.alice)) + "&startDate=2026-01-01&endDate=2026-12-31",
                token);

        assertEquals(fresh(f.alice), detail.get("studentId").asLong());
        assertEquals("E-001", detail.get("enrollmentNumber").asText());
        assertEquals("Alice", detail.get("studentName").asText());
        assertEquals("B.Tech CSE", detail.get("programName").asText());
        assertEquals("Semester 3", detail.get("semesterName").asText());
        assertEquals("A", detail.get("sectionName").asText());
        assertEquals(2, detail.get("subjects").size());

        assertEquals("CS301", detail.get("subjects").get(0).get("subjectCode").asText());
        assertEquals(4L, detail.get("subjects").get(0).get("classes").asLong());
        assertEquals(4L, detail.get("subjects").get(0).get("present").asLong());
        assertEquals(0L, detail.get("subjects").get(0).get("notAttended").asLong());
        assertEquals(100.0, detail.get("subjects").get(0).get("percentage").asDouble(), 0.0001);

        assertEquals("CS302", detail.get("subjects").get(1).get("subjectCode").asText());
        assertEquals(1L, detail.get("subjects").get(1).get("present").asLong());
        assertEquals(2L, detail.get("subjects").get(1).get("classes").asLong());
        assertEquals(1L, detail.get("subjects").get(1).get("notAttended").asLong());
        assertEquals(50.0, detail.get("subjects").get(1).get("percentage").asDouble(), 0.0001);

        assertEquals(5L, detail.get("totalPresent").asLong());
        assertEquals(6L, detail.get("totalClasses").asLong());
        assertEquals(83.3333, detail.get("overallPercentage").asDouble(), 0.001);
    }

    // â”€â”€ 15. Subject detail â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void subjectDetail_returnsFacultyClassesAverageAndBelowThreshold() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode detail = fetchJson(subjectPath(w, w.sectionA, w.cs301), token);

        assertEquals("CS301", detail.get("subjectCode").asText());
        assertEquals("Data Structures and Algorithms", detail.get("subjectName").asText());
        assertEquals("B.Tech CSE", detail.get("programName").asText());
        assertEquals("Semester 3", detail.get("semesterName").asText());
        assertEquals("A", detail.get("sectionName").asText());
        // Real teaching assignment, never invented.
        assertEquals(1, detail.get("facultyNames").size());
        assertEquals("HOD User", detail.get("facultyNames").get(0).asText());

        assertEquals(4L, detail.get("totalClasses").asLong());
        // The enrolled roster, not only the students who happen to have marks.
        assertEquals(3L, detail.get("students").asLong());
        assertEquals(5L, detail.get("presentCount").asLong());
        // CORRECTED: 4 conducted classes measured against 3 enrolled students.
        assertEquals(12L, detail.get("totalClassesAcrossStudents").asLong());
        assertEquals(41.6666, detail.get("averageAttendance").asDouble(), 0.001);
        // Carol at 1/4 and the zero-record student at 0/4 are both below.
        assertEquals(2L, detail.get("studentsBelowThreshold").asLong());
        assertEquals(3, detail.get("studentRows").size());

        // The zero-record student is listed with 0 against the subject's own
        // conducted classes, not omitted and not 0 / 0.
        JsonNode nobody = rowForStudentId(detail.get("studentRows"), fresh(f.nobody));
        assertNotNull(nobody, "A student with no records in this subject must still be listed");
        assertEquals(0L, nobody.get("present").asLong());
        assertEquals(4L, nobody.get("total").asLong());
        assertEquals(0.0, nobody.get("percentage").asDouble(), 0.0001);
    }

    // â”€â”€ 16. Date range filtering combined with the context (AND) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void dateRange_restrictsAttendanceWithoutWideningTheAcademicContext() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode january = fetchJson(
                matrixPath(w, w.sectionA, null) + "&startDate=2026-01-01&endDate=2026-01-31", token);
        // January: only the 4 CS301 classes. The context is unchanged.
        assertEquals(List.of("E-001", "E-002", "E-003"), enrollmentOf(january.get("students")));
        assertEquals(List.of("CS301", "CS302"), codesOf(january.get("subjects")));
        JsonNode alice = rowFor(january.get("students"), "E-001");
        assertEquals(4L, alice.get("totalPresent").asLong());
        assertEquals(4L, alice.get("totalClasses").asLong());

        JsonNode february = fetchJson(
                matrixPath(w, w.sectionA, null) + "&startDate=2026-02-01&endDate=2026-02-28", token);
        JsonNode carol = rowFor(february.get("students"), "E-003");
        assertEquals(2L, carol.get("totalPresent").asLong());
        assertEquals(2L, carol.get("totalClasses").asLong());
        assertEquals(100.0, carol.get("overallPercentage").asDouble(), 0.0001);

        // A range with no data still lists the population - never an empty report
        // masquerading as "no students".
        JsonNode none = fetchJson(
                matrixPath(w, w.sectionA, null) + "&startDate=2025-03-01&endDate=2025-03-31", token);
        assertEquals(List.of("E-001", "E-002", "E-003"), enrollmentOf(none.get("students")));
        assertFalse(rowFor(none.get("students"), "E-001").hasNonNull("overallPercentage"));

        // An inverted range is rejected, exactly like the other HOD endpoints.
        mockMvc.perform(get(matrixPath(w, w.sectionA, null))
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // â”€â”€ 17. Invalid academic combinations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_incompleteAcademicContextIsRejected() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        String base = "/api/hod/attendance/matrix";
        mockMvc.perform(get(base).param("academicSessionId", String.valueOf(w.session.getId()))
                        .param("programId", String.valueOf(w.btech.getId()))
                        .param("semesterId", String.valueOf(w.semester.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Select Academic Session, Program, Semester and Section "
                                + "to view the Attendance Matrix."));
    }

    @Test
    void matrix_sessionAndProgramFromDifferentProgramsIsRejected() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // The B.Tech academic session with the M.Tech program.
        mockMvc.perform(get("/api/hod/attendance/matrix")
                        .param("academicSessionId", String.valueOf(w.session.getId()))
                        .param("programId", String.valueOf(w.mtech.getId()))
                        .param("semesterId", String.valueOf(w.semester.getId()))
                        .param("sectionId", String.valueOf(w.sectionA.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_semesterFromAnotherAcademicSessionIsRejected() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get("/api/hod/attendance/matrix")
                        .param("academicSessionId", String.valueOf(w.session.getId()))
                        .param("programId", String.valueOf(w.btech.getId()))
                        .param("semesterId", String.valueOf(w.prevSemester.getId()))
                        .param("sectionId", String.valueOf(w.sectionA.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrix_invalidSortOrDirectionIsRejected() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get(matrixPath(w, w.sectionA, null)).param("sortBy", "attendanceRank")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(matrixPath(w, w.sectionA, null)).param("direction", "sideways")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // â”€â”€ 18/19/20. Cross-department rejection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void crossDepartment_academicContextIsRejected() throws Exception {
        World w = world();
        populate(w);
        Foreign foreign = foreignDepartment();
        entityManager.flush();
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get("/api/hod/attendance/matrix")
                        .param("academicSessionId", String.valueOf(foreign.session.getId()))
                        .param("programId", String.valueOf(foreign.program.getId()))
                        .param("semesterId", String.valueOf(foreign.semester.getId()))
                        .param("sectionId", String.valueOf(foreign.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/hod/attendance/overview")
                        .param("semesterId", String.valueOf(foreign.semester.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/hod/attendance/low")
                        .param("sectionId", String.valueOf(foreign.section.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossDepartment_studentIdIsRejectedEvenInsideAValidContext() throws Exception {
        World w = world();
        Fixture f = populate(w);
        Foreign foreign = foreignDepartment();
        entityManager.flush();
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // The academic context is entirely legitimate; only the student is foreign.
        mockMvc.perform(get(studentPath(w, w.sectionA, fresh(foreign.student)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // A student of the same department but another section is equally refused.
        mockMvc.perform(get(studentPath(w, w.sectionA, fresh(f.otherSection)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // ... and one of the same department but another academic session.
        mockMvc.perform(get(studentPath(w, w.sectionA, fresh(f.otherSession)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossDepartment_subjectIdIsRejectedEvenInsideAValidContext() throws Exception {
        World w = world();
        populate(w);
        Foreign foreign = foreignDepartment();
        entityManager.flush();
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        mockMvc.perform(get(subjectPath(w, w.sectionA, foreign.subject))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(matrixPath(w, w.sectionA, foreign.subject))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // A same-department subject from another semester is refused too.
        mockMvc.perform(get(subjectPath(w, w.sectionA, w.cs101))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // â”€â”€ 21/22. Unauthenticated and non-HOD access â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void unauthenticatedAccessToEveryPhase3EndpointIsRejected() throws Exception {
        World w = world();
        populate(w);
        String path = matrixPath(w, w.sectionA, null);

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hod/attendance/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hod/attendance/low")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(studentPath(w, w.sectionA, 1L))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(subjectPathById(w, w.sectionA, 1L))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path.replace("/matrix", "/matrix/export.xlsx")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonHodRolesAreRejectedFromEveryPhase3Endpoint() throws Exception {
        World w = world();
        populate(w);
        String path = matrixPath(w, w.sectionA, null);

        for (String credentials : List.of(
                TEACHER_EMAIL + "|" + TEACHER_PASSWORD,
                STUDENT_EMAIL + "|" + STUDENT_PASSWORD,
                ADMIN_EMAIL + "|" + ADMIN_PASSWORD)) {
            String[] parts = credentials.split("\\|");
            String token = login(parts[0], parts[1]);

            mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/hod/attendance/overview")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/hod/attendance/low")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(studentPath(w, w.sectionA, 1L))
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(subjectPathById(w, w.sectionA, 1L))
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path.replace("/matrix", "/matrix/export.xlsx"))
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void noClientSuppliedDepartmentIdIsEverAccepted() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        // A departmentId parameter is silently inert: the response still reflects
        // only the HOD's own department because the scope comes from the JWT.
        mockMvc.perform(get("/api/hod/attendance/overview")
                        .param("departmentId", String.valueOf(foreignDepartment().dept.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.sectionId").doesNotExist());
    }

    // â”€â”€ 23/24. No duplicate rows or columns â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_hasNoDuplicateStudentRowsOrSubjectColumns() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        for (String path : List.of(
                matrixPath(w, w.sectionA, null),
                matrixPath(w, w.sectionB, null),
                lowPath(w, w.sectionA),
                subjectPath(w, w.sectionA, w.cs301))) {
            JsonNode payload = fetchJson(path, token);
            JsonNode subjects = payload.get("subjects");
            if (subjects != null && subjects.isArray()) {
                Set<Long> columnIds = idSetOf(subjects, "subjectId");
                assertEquals(subjects.size(), columnIds.size(),
                        "Duplicate subject column in " + path);
            }
            JsonNode students = payload.get("students");
            if (students != null && students.isArray()) {
                Set<Long> studentIds = idSetOf(students, "studentId");
                assertEquals(students.size(), studentIds.size(),
                        "Duplicate student row in " + path);
            }
        }
    }

    // â”€â”€ 18. Overview metrics â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void overview_reportsTheContextMetricsFromRealData() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode overview = fetchJson(overviewPath(w, w.sectionA), token);
        assertEquals(3L, overview.get("totalStudents").asLong());
        // CORRECTED: Alice 4+1, Carol 1+2 -> 8 present. The denominator is
        // conducted classes: 6 per student x 3 students = 18.
        assertEquals(8L, overview.get("overallPresentCount").asLong());
        assertEquals(18L, overview.get("overallTotalClasses").asLong());
        assertEquals(44.4444, overview.get("overallPercentage").asDouble(), 0.001);
        // Carol at 50% and the zero-record student at 0% are both below 75%.
        assertEquals(2L, overview.get("belowThresholdCount").asLong());
        // 4 January CS301 + 2 February CS302 conducted sessions.
        assertEquals(6L, overview.get("classesConducted").asLong());
        // Every student has a denominator now, so nobody is "without data".
        assertEquals(0L, overview.get("noConductedClasses").asLong());
        assertEquals(75.0, overview.get("thresholdPercentage").asDouble(), 0.0001);

        List<Long> counts = new ArrayList<>();
        overview.get("distribution").forEach(band -> counts.add(band.get("studentCount").asLong()));
        assertEquals(List.of(0L, 1L, 2L), counts); // 90-100, 75-89, below 75

        assertEquals(2, overview.get("subjects").size());
        JsonNode cs301 = overview.get("subjects").get(0);
        assertEquals("CS301", cs301.get("subjectCode").asText());
        assertEquals(4L, cs301.get("classesConducted").asLong());
        assertEquals(5L, cs301.get("presentCount").asLong());
        // 4 conducted classes x 3 enrolled students.
        assertEquals(12L, cs301.get("totalClasses").asLong());
        assertEquals(41.6666, cs301.get("percentage").asDouble(), 0.001);
        assertEquals(1, cs301.get("facultyNames").size());

        JsonNode cs302 = overview.get("subjects").get(1);
        assertEquals(2L, cs302.get("classesConducted").asLong());
        assertEquals(3L, cs302.get("presentCount").asLong());
        assertEquals(6L, cs302.get("totalClasses").asLong());
        assertEquals(50.0, cs302.get("percentage").asDouble(), 0.0001);
    }

    @Test
    void overview_subjectWithNoClassesReportsNoPercentageRatherThanZero() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode overview = fetchJson(
                overviewPath(w, w.sectionA) + "&startDate=2026-01-01&endDate=2026-01-31", token);
        JsonNode cs302 = overview.get("subjects").get(1);
        assertEquals("CS302", cs302.get("subjectCode").asText());
        assertEquals(0L, cs302.get("classesConducted").asLong());
        assertEquals(0L, cs302.get("presentCount").asLong());
        assertEquals(0L, cs302.get("totalClasses").asLong());
        assertFalse(cs302.hasNonNull("percentage"));
    }

    @Test
    void overview_contextMetadataIsAlwaysEchoedBack() throws Exception {
        World w = world();
        populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        JsonNode context = fetchJson("/api/hod/attendance/context"
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.btech.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.sectionA.getId(), token);
        assertEquals("Academic Session 2026-27", context.get("academicSessionName").asText());
        assertEquals("B.Tech CSE", context.get("programName").asText());
        assertEquals("Semester 3", context.get("semesterName").asText());
        assertEquals("A", context.get("sectionName").asText());
        assertTrue(context.get("complete").asBoolean());
    }

    // â”€â”€ 18. Sorting and pagination â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void matrix_supportsAcademicSortingAndServerSidePagination() throws Exception {
        World w = world();
        Fixture f = populate(w);
        String token = login(HOD_EMAIL, HOD_PASSWORD);

        assertEquals(List.of("E-001", "E-002", "E-003"),
                enrollmentOf(fetchJson(matrixPath(w, w.sectionA, null), token)
                        .get("students")));

        // Default is enrollment number, ascending.
        JsonNode descending = fetchJson(
                matrixPath(w, w.sectionA, null) + "&sortBy=enrollmentNumber&direction=desc", token);
        assertEquals(List.of("E-003", "E-002", "E-001"), enrollmentOf(descending.get("students")));

        // By name: Alice < Carol < No Records.
        JsonNode nameAsc = fetchJson(
                matrixPath(w, w.sectionA, null) + "&sortBy=name&direction=asc", token);
        assertEquals(List.of("E-001", "E-003", "E-002"), enrollmentOf(nameAsc.get("students")));
        JsonNode nameDesc = fetchJson(
                matrixPath(w, w.sectionA, null) + "&sortBy=name&direction=desc", token);
        assertEquals(List.of("E-002", "E-003", "E-001"), enrollmentOf(nameDesc.get("students")));

        // By overall attendance: Alice 83.33% > Carol 50% > nobody (no data, always
        // last rather than treated as 0%).
        JsonNode byPercentage = fetchJson(
                matrixPath(w, w.sectionA, null) + "&sortBy=overallPercentage&direction=desc", token);
        assertEquals(List.of("E-001", "E-003", "E-002"),
                enrollmentOf(byPercentage.get("students")));

        JsonNode ascending = fetchJson(
                matrixPath(w, w.sectionA, null) + "&sortBy=overallPercentage&direction=asc", token);
        assertEquals(List.of("E-002", "E-003", "E-001"),
                enrollmentOf(ascending.get("students")));

        // Paging never splits a student row across subject columns.
        JsonNode firstPage = fetchJson(matrixPath(w, w.sectionA, null) + "&size=2&page=0", token);
        assertEquals(2, firstPage.get("students").size());
        assertEquals(3L, firstPage.get("totalElements").asLong());
        assertEquals(2, firstPage.get("totalPages").asInt());
        assertEquals(2, firstPage.get("subjects").size());
        for (JsonNode row : firstPage.get("students")) {
            assertEquals(firstPage.get("subjects").size(), row.get("subjects").size());
        }
        JsonNode secondPage = fetchJson(matrixPath(w, w.sectionA, null) + "&size=2&page=1", token);
        assertEquals(1, secondPage.get("students").size());
        assertEquals("E-003", secondPage.get("students").get(0).get("enrollmentNumber").asText());

        // A page beyond the end is empty, not an error and not a repeat.
        JsonNode beyond = fetchJson(matrixPath(w, w.sectionA, null) + "&size=2&page=9", token);
        assertEquals(0, beyond.get("students").size());
        assertEquals(3L, beyond.get("totalElements").asLong());

        assertFalse(idSetOf(firstPage.get("students"), "studentId").contains(fresh(f.mtechStudent)));
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private AcademicSession session(Program program, String name, String code) {
        return academicSessionRepository.save(AcademicSession.builder()
                .name(name).code(code).program(program).description("test")
                .createdAt(now()).updatedAt(now()).build());
    }

    private Semester semester(AcademicSession session, String name, String code, int year) {
        return semesterRepository.save(Semester.builder()
                .name(name).code(code).year(year).academicSession(session)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Section section(Batch batch, String name) {
        return sectionRepository.save(Section.builder()
                .sectionCode(name + "-" + System.nanoTime()).name(name)
                .maxCapacity(60).batch(batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
    }

    private Subject subject(String code, String name, Department dept) {
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

    private Student student(AcademicSession session, Semester semester, Section section,
                            Program program, String enrollmentNumber, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("ROLL-" + System.nanoTime())
                .enrollmentNumber(enrollmentNumber)
                .name(name).gender("M").status("ACTIVE")
                .academicSession(session).batch(section.getBatch())
                .section(section).semester(semester).program(program)
                .createdAt(now()).updatedAt(now()).build());
    }

    /**
     * Conducts {@code total} real classes of a subject for a section.
     *
     * <p>The sessions are the shared class events; each student's mark is then
     * recorded against them by {@link #markAll}, exactly as a teacher would.
     * One date and one lecture period per class keeps the
     * {@code (subject, section, date, period)} uniqueness intact.</p>
     */
    private List<AttendanceSession> conductClasses(Subject subject, Section section, Teacher teacher,
                                                  int total, String[] dates) {
        List<AttendanceSession> sessions = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            sessions.add(attendanceSessionRepository.save(AttendanceSession.builder()
                    .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                    .subject(subject.getName()).section(section.getName())
                    .teacher(teacher.getFullName())
                    .lecturePeriod("LP" + (i + 1)).date(dates[i % dates.length]).status("CONDUCTED")
                    .createdAt(now()).updatedAt(now()).build()));
        }
        return sessions;
    }

    /** Marks the first {@code presentCount} sessions PRESENT and the rest ABSENT. */
    private void markAll(List<AttendanceSession> sessions, Student student, int presentCount) {
        for (int i = 0; i < sessions.size(); i++) {
            AttendanceSession session = sessions.get(i);
            boolean present = i < presentCount;
            attendanceRecordRepository.save(AttendanceRecord.builder()
                    .session(session).student(student).subject(session.getSubjectEntity())
                    .section(session.getSectionEntity()).markedBy(session.getTeacherEntity())
                    .status(present ? "PRESENT" : "ABSENT")
                    .lecturePeriod(session.getLecturePeriod())
                    .date(session.getDate()).isPresent(present)
                    .createdAt(now()).build());
        }
    }

    private String matrixPath(World w, Section section, Subject subject) {
        return matrixPath(w.session, w.btech, w.semester, section, subject);
    }

    private String matrixPath(AcademicSession session, Program program, Semester semester,
                              Section section, Subject subject) {
        String path = "/api/hod/attendance/matrix"
                + "?academicSessionId=" + session.getId()
                + "&programId=" + program.getId()
                + "&semesterId=" + semester.getId()
                + "&sectionId=" + section.getId();
        return subject == null ? path : path + "&subjectId=" + subject.getId();
    }

    private String overviewPath(World w, Section section) {
        return "/api/hod/attendance/overview"
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.btech.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + section.getId();
    }

    private String lowPath(World w, Section section) {
        return "/api/hod/attendance/low"
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.btech.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + section.getId();
    }

    private String studentPath(World w, Section section, long studentId) {
        return "/api/hod/attendance/student/" + studentId
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.btech.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + section.getId();
    }

    private String subjectPath(World w, Section section, Subject subject) {
        return subjectPathById(w, section, subject.getId());
    }

    private String subjectPathById(World w, Section section, long subjectId) {
        return "/api/hod/attendance/subject/" + subjectId
                + "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.btech.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + section.getId();
    }

    /** Flushes so a lazily-initialised proxy carries a usable identifier. */
    private Long fresh(Student student) {
        entityManager.flush();
        entityManager.refresh(student);
        return student.getId();
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

    private static List<Long> idsOf(JsonNode array, String field) {
        List<Long> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(field).asLong()));
        return values;
    }

    private static Set<Long> idSetOf(JsonNode array, String field) {
        return new HashSet<>(idsOf(array, field));
    }

    private static List<String> codesOf(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get("subjectCode").asText()));
        return values;
    }

    private static List<String> enrollmentOf(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get("enrollmentNumber").asText()));
        return values;
    }

    private static List<String> namesOf(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get("studentName").asText()));
        return values;
    }

    private static String columnCodeAt(JsonNode matrix, int index) {
        return matrix.get("subjects").get(index).get("subjectCode").asText();
    }

    private static JsonNode rowFor(JsonNode students, String enrollmentNumber) {
        for (JsonNode row : students) {
            if (enrollmentNumber.equals(row.get("enrollmentNumber").asText())) {
                return row;
            }
        }
        return null;
    }

    private static JsonNode rowForStudentId(JsonNode students, long studentId) {
        for (JsonNode row : students) {
            if (row.get("studentId").asLong() == studentId) {
                return row;
            }
        }
        return null;
    }

    /** The row of [students] whose student name is [name], or null. */
    private static JsonNode studentNamed(JsonNode students, String name) {
        for (JsonNode row : students) {
            if (name.equals(row.get("studentName").asText())) {
                return row;
            }
        }
        return null;
    }
}
