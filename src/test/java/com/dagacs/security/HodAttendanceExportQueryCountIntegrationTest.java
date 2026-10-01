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
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4A: an export must not become an N+1.
 *
 * <p>The Phase 3 report service aggregates with a small, fixed set of grouped
 * queries rather than one query per student or per subject. That is a
 * performance property, and a performance property that is only ever
 * <i>described in a comment</i> decays the first time somebody adds a
 * convenience loop. This class makes it measurable.</p>
 *
 * <h3>Why the measurement method is the point</h3>
 * <p>Each test builds one hierarchy, measures an export, then <b>grows the same
 * hierarchy's student population</b> and measures again. The assertion is on the
 * <i>difference</i> between the two counts, not on an absolute budget.</p>
 * <p>An N+1 always appears as a count that grows with the data, so growth is the
 * signal that actually matters. It also keeps the test honest in the other
 * direction: if a legitimate, unrelated query is added to the report later, both
 * runs rise together and the test still passes. A hard-coded ceiling would have
 * gone stale and would eventually have been "fixed" by loosening the number.</p>
 *
 * <p><b>Exports</b> are measured rather than the on-screen reports, because the
 * export is the one path that must be unpaged: it deliberately returns the whole
 * population of the context. An N+1 there is at its worst, so that is where the
 * guard belongs.</p>
 *
 * <p>Two subjects and four conducted classes per subject are seeded, so the
 * cross-tab has real dynamic columns and every cell has a real denominator. A
 * single-subject, zero-session fixture could return an empty result set and hide
 * a per-subject query entirely.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodAttendanceExportQueryCountIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final int CLASSES_PER_SUBJECT = 4;
    private static final int SMALL_POPULATION = 2;
    private static final int LARGE_POPULATION = 60;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private HodAttendanceReportService attendanceReportService;

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private AcademicSessionRepository academicSessionRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SubjectOfferingRepository subjectOfferingRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private TeacherSubjectSectionAssignmentRepository assignmentRepository;
    @Autowired private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private record World(Department department, Program program, AcademicSession session,
                         Semester semester, Batch batch, Section section,
                         List<Subject> subjects, List<Student> students,
                         List<AttendanceSession> lectures) {
    }

    /** The two export formats, as they appear in a URL path. */
    private enum Format { xlsx, pdf }

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Fixture
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * One complete, owned hierarchy, seeded with a small student population and
     * full, real attendance so no report can short-circuit on an empty result.
     *
     * <p>The login identity is the application's own seeded HOD account; only
     * the {@link Teacher} profile is re-pointed at this world, which is what
     * makes the HOD the owner of exactly this department and therefore unable to
     * reach any other.</p>
     */
    private World world() {
        Department department = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("QC1")
                .description("query count").createdBy("test")
                .createdAt(now()).updatedAt(now()).build());
        Program program = programRepository.save(Program.builder()
                .name("B.Tech CSE").code("QCP" + tag()).duration("4yr")
                .description("test").department(department).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Academic Session 2026-27").code("QCS" + tag()).program(program)
                .description("test").createdAt(now()).updatedAt(now()).build());
        Semester semester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("QCM" + tag()).year(2026).academicSession(session)
                .createdAt(now()).updatedAt(now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("QB-" + System.nanoTime()).name("BTech").year(2026)
                .program("B.Tech CSE").maxCapacity(1000).academicSession(session)
                .createdAt(now()).updatedAt(now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("Q-" + System.nanoTime()).name("A")
                .maxCapacity(1000).batch(batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());

        // Two subjects, so the cross-tab has real dynamic columns rather than a
        // degenerate single-column case that could hide a per-subject query.
        List<Subject> subjects = new ArrayList<>();
        List<SubjectOffering> offerings = new ArrayList<>();
        for (String name : List.of("Data Structures", "Computer Networks")) {
            Subject subject = subjectRepository.save(Subject.builder()
                    .code("QC" + tag()).name(name).description("test")
                    .creditHours("3").department(department).status("ACTIVE")
                    .createdAt(now()).updatedAt(now()).build());
            offerings.add(subjectOfferingRepository.save(SubjectOffering.builder()
                    .subject(subject).semester(semester)
                    .createdAt(now()).updatedAt(now()).build()));
            subjects.add(subject);
        }

        // The seeded HOD account is re-pointed at this department, so this test's
        // exports are inside the HOD's own scope and can never be satisfied by
        // accident.
        Teacher hod = teacherRepository.findByEmail(HOD_EMAIL)
                .orElseGet(() -> teacherRepository.save(Teacher.builder()
                        .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                        .phone("").designation("Professor").status("ACTIVE")
                        .avatarUrl("").isHod(true)
                        .createdAt(now()).updatedAt(now()).build()));
        hod.setDepartment(department);
        hod.setIsHod(true);
        hod.setStatus("ACTIVE");
        teacherRepository.save(hod);
        for (SubjectOffering offering : offerings) {
            assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                    .teacher(hod).subjectOffering(offering).section(section)
                    .batch(batch).createdAt(now()).updatedAt(now()).build());
        }

        List<Student> students = new ArrayList<>();
        for (int i = 0; i < SMALL_POPULATION; i++) {
            students.add(studentRepository.save(Student.builder()
                    .rollNumber("QR-" + tag()).enrollmentNumber("QE-" + tag())
                    .name("Student " + i).gender("M").status("ACTIVE")
                    .academicSession(session).batch(batch)
                    .section(section).semester(semester).program(program)
                    .createdAt(now()).updatedAt(now()).build()));
        }
        entityManager.flush();
        entityManager.clear();

        World world = new World(department, program, session, semester, batch, section,
                subjects, students, new ArrayList<>());
        conductAllClasses(world);
        return world;
    }

    /**
     * Every subject held {@value #CLASSES_PER_SUBJECT} classes, and every enrolled
     * student was marked present in each of them.
     *
     * <p>Idempotent by design: the classes already held by the world are reused
     * rather than recreated, because {@code uk_session_class} makes
     * (section, subject, date, period) unique. That is what lets a test grow the
     * population and re-mark, instead of rebuilding the whole world.</p>
     */
    private void conductAllClasses(World w) {
        if (w.lectures().isEmpty()) {
            Teacher hod = teacherRepository.findByEmail(HOD_EMAIL).orElseThrow();
            for (int day = 0; day < CLASSES_PER_SUBJECT; day++) {
                for (Subject subject : w.subjects()) {
                    w.lectures().add(attendanceSessionRepository.save(
                            AttendanceSession.builder()
                                    .subjectEntity(subject)
                                    .sectionEntity(w.section())
                                    .batchEntity(w.batch())
                                    .teacherEntity(hod)
                                    .subject(subject.getName())
                                    .section(w.section().getName())
                                    .batch(w.batch().getBatchCode())
                                    .teacher(hod.getFullName())
                                    .lecturePeriod("LP1")
                                    .date(LocalDate.of(2026, 5, 1).plusDays(day).toString())
                                    .status("CONDUCTED")
                                    .createdAt(now()).updatedAt(now()).build()));
                }
            }
        }
        markAll(w, w.students());
    }

    /** Marks every given student present in every class the world has held. */
    private void markAll(World w, List<Student> students) {
        Teacher hod = teacherRepository.findByEmail(HOD_EMAIL).orElseThrow();
        for (AttendanceSession lecture : w.lectures()) {
            for (Student student : students) {
                attendanceRecordRepository.save(AttendanceRecord.builder()
                        .session(lecture)
                        .student(student)
                        .subject(lecture.getSubjectEntity())
                        .section(w.section())
                        .batch(w.batch())
                        .markedBy(hod)
                        .status("PRESENT")
                        .lecturePeriod("LP1")
                        .date(lecture.getDate())
                        .isPresent(true)
                        .createdAt(now()).build());
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    /** Grows the world's population to {@code target} students, fully marked. */
    private void grow(World w, int target) {
        List<Student> added = new ArrayList<>();
        while (w.students().size() + added.size() < target) {
            added.add(studentRepository.save(Student.builder()
                    .rollNumber("QR-" + tag()).enrollmentNumber("QE-" + tag())
                    .name("Student " + (w.students().size() + added.size()))
                    .gender("M").status("ACTIVE")
                    .academicSession(w.session()).batch(w.batch())
                    .section(w.section()).semester(w.semester())
                    .program(w.program())
                    .createdAt(now()).updatedAt(now()).build()));
        }
        entityManager.flush();
        entityManager.clear();
        // The new students must be marked too, or the report would grow its
        // population without growing its data and the comparison would be unfair
        // in the other direction.
        markAll(w, added);
        w.students().addAll(added);
    }

    private static String tag() {
        return String.valueOf(System.nanoTime());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // URLs, auth and measurement
    // ═══════════════════════════════════════════════════════════════════════

    private String contextQuery(World w) {
        return "?academicSessionId=" + w.session().getId()
                + "&programId=" + w.program().getId()
                + "&semesterId=" + w.semester().getId()
                + "&sectionId=" + w.section().getId();
    }

    /** One export URL, addressing entity-scoped reports by path variable. */
    private String exportUrl(World w, String report, Format format) {
        return switch (report) {
            case "student" -> "/api/hod/attendance/student/" + w.students().get(0).getId()
                    + "/export." + format.name() + contextQuery(w);
            case "subject" -> "/api/hod/attendance/subject/" + w.subjects().get(0).getId()
                    + "/export." + format.name() + contextQuery(w);
            default -> "/api/hod/attendance/" + report + "/export." + format.name()
                    + contextQuery(w);
        };
    }

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

    /**
     * The identity {@link com.dagacs.security.AuthenticatedHodResolver} expects.
     *
     * <p>It reads the security-context principal <i>as an email string</i>, which
     * {@code @WithMockUser} does not provide - that installs a
     * {@code UserDetails}. A context is therefore built by hand for the single
     * test that calls the report service directly instead of over HTTP.</p>
     */
    private void authenticateAsHod() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        HOD_EMAIL, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_HOD"))));
    }

    /** Runs one export and returns how many statements it executed. */
    private long countQueriesFor(String jwt, String url) throws Exception {
        // Warm the first call: it pays for lazily-loaded fixtures and a cold
        // identity lookup, which would make the comparison meaningless.
        mockMvc.perform(get(url).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        mockMvc.perform(get(url).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsByteArray().length > 0,
                        "the export must produce a real file: " + url));
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }

    private Statistics statistics() {
        Statistics statistics = ((SessionFactory) entityManagerFactory
                .unwrap(SessionFactory.class)).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    private HodAcademicSelection selection(World w) {
        return new HodAcademicSelection(w.session().getId(), w.program().getId(),
                w.semester().getId(), w.section().getId());
    }

    /**
     * The whole guard, for one report: measure, grow, measure again, compare.
     *
     * <p>Named so every report reads identically and a new one is a one-liner.</p>
     */
    private void assertQueryCountIsIndependentOfPopulation(String report,
                                                           World w, String jwt)
            throws Exception {
        long small = countQueriesFor(jwt, exportUrl(w, report, Format.xlsx));
        grow(w, LARGE_POPULATION);
        long large = countQueriesFor(jwt, exportUrl(w, report, Format.xlsx));

        assertEquals(w.students().size(), LARGE_POPULATION,
                "the fixture must really have grown");
        assertEquals(small, large,
                "The " + report + " export must aggregate with a fixed set of "
                        + "queries. It used " + small + " for " + SMALL_POPULATION
                        + " students and " + large + " for " + LARGE_POPULATION
                        + ", which is the signature of an N+1.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // The guard
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the overview export issues the same queries for 2 and for 60 students")
    void overviewExportIsNotPerStudent() throws Exception {
        World w = world();
        String jwt = token();
        assertQueryCountIsIndependentOfPopulation("overview", w, jwt);
    }

    @Test
    @DisplayName("the matrix export issues the same queries for 2 and for 60 students")
    void matrixExportIsNotPerStudent() throws Exception {
        World w = world();
        String jwt = token();
        assertQueryCountIsIndependentOfPopulation("matrix", w, jwt);
    }

    @Test
    @DisplayName("the low-attendance export issues the same queries for 2 and for 60 students")
    void lowExportIsNotPerStudent() throws Exception {
        World w = world();
        String jwt = token();
        assertQueryCountIsIndependentOfPopulation("low", w, jwt);
    }

    @Test
    @DisplayName("the subject export issues the same queries for 2 and for 60 students")
    void subjectExportIsNotPerStudent() throws Exception {
        World w = world();
        String jwt = token();
        assertQueryCountIsIndependentOfPopulation("subject", w, jwt);
    }

    @Test
    @DisplayName("the student export issues the same queries for 2 and for 60 students")
    void studentExportIsNotPerStudent() throws Exception {
        World w = world();
        String jwt = token();
        assertQueryCountIsIndependentOfPopulation("student", w, jwt);
    }

    @Test
    @DisplayName("the Context Pack issues the same queries for 2 and for 60 students")
    void contextPackIsNotPerStudent() throws Exception {
        // Phase 4B. The Pack renders five sheets, which is precisely where a
        // per-sheet query would hide: the count would grow with the population
        // even though no individual sheet looks wrong. Growing the cohort is what
        // exposes it.
        World w = world();
        String jwt = token();
        assertQueryCountIsIndependentOfPopulation("context-pack", w, jwt);
    }

    @Test
    @DisplayName("the Context Pack reuses loaded data instead of re-querying per sheet")
    void contextPackCostsNoMoreThanTheReportsItCombines() throws Exception {
        // The strongest form of the reuse claim: five sheets must not cost more
        // than the three canonical reports the sheets are made from. If any sheet
        // re-fetched, the pack would exceed their sum.
        World w = world();
        String jwt = token();
        grow(w, LARGE_POPULATION);

        callOk(jwt, exportUrl(w, "context-pack", Format.xlsx));

        long pack = measure(jwt, exportUrl(w, "context-pack", Format.xlsx));
        long combined = measure(jwt, exportUrl(w, "overview", Format.xlsx))
                + measure(jwt, exportUrl(w, "matrix", Format.xlsx))
                + measure(jwt, exportUrl(w, "low", Format.xlsx));

        assertTrue(pack <= combined,
                "The Context Pack must reuse the reports it combines. It cost "
                        + pack + " queries while its three sources cost " + combined
                        + "; a per-sheet reload would show up here as extra cost.");
    }

    @Test
    @DisplayName("the PDF exports are no more expensive than the Excel exports")
    void pdfExportIsNoMoreExpensive() throws Exception {
        // The renderer differs, not the data path: both formats must cost the
        // same queries, or one of them is quietly taking a different route.
        World w = world();
        String jwt = token();
        grow(w, 20);

        for (String report : List.of("overview", "matrix", "low")) {
            long xlsx = countQueriesFor(jwt, exportUrl(w, report, Format.xlsx));
            long pdf = countQueriesFor(jwt, exportUrl(w, report, Format.pdf));
            assertEquals(xlsx, pdf,
                    "The " + report + " PDF must cost the same queries as its "
                            + "Excel: " + pdf + " vs " + xlsx + ".");
        }
    }

    @Test
    @DisplayName("every export costs a bounded number of queries, not a per-row budget")
    void exportsHaveAnAbsoluteCeiling() throws Exception {
        // The relative guard catches growth. This catches a report rewritten to
        // issue, say, forty grouped queries - still constant, still not an N+1,
        // and still far too expensive to run per HOD per context.
        World w = world();
        String jwt = token();
        grow(w, 30);

        for (String report : List.of("overview", "matrix", "low", "subject", "student",
                "context-pack")) {
            long count = countQueriesFor(jwt, exportUrl(w, report, Format.xlsx));
            // The Pack combines three reports into five sheets, so its budget is
            // their sum rather than any single report's ceiling.
            assertTrue(count > 0 && count <= (report.equals("context-pack") ? 70 : 40),
                    "The " + report + " export used " + count
                            + " queries for one export; a report must be a fixed, "
                            + "small set of aggregates, not a per-row walk.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // The export must not be a second, more expensive data path
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("an export costs no more queries than the report it came from")
    void exportIsNoMoreExpensiveThanTheOnScreenReport() throws Exception {
        // The strongest form of the Phase 4A claim: the exported file and the
        // on-screen report are produced by the *same* service method, so they
        // must cost the same number of statements. If an export ever grew its own
        // aggregation, this is the assertion that would catch it - and it is
        // measured over HTTP so the service owns its own persistence context,
        // exactly as it does in production.
        World w = world();
        String jwt = token();
        grow(w, 30);

        assertSameQueryCost("overview", jwt, w, "/api/hod/attendance/overview");
        assertSameQueryCost("matrix", jwt, w, "/api/hod/attendance/matrix");
        assertSameQueryCost("low", jwt, w, "/api/hod/attendance/low");
        assertSameQueryCost("subject", jwt, w, "/api/hod/attendance/subject/"
                + w.subjects().get(0).getId());
        assertSameQueryCost("student", jwt, w, "/api/hod/attendance/student/"
                + w.students().get(0).getId());
    }

    private void assertSameQueryCost(String report, String jwt, World w, String readPath)
            throws Exception {
        // One warm-up call each, so neither measurement pays for a cold lookup.
        callOk(jwt, readPath + contextQuery(w));
        callOk(jwt, exportUrl(w, report, Format.xlsx));

        long read = measure(jwt, readPath + contextQuery(w));
        long exported = measure(jwt, exportUrl(w, report, Format.xlsx));

        assertEquals(read, exported,
                "The " + report + " export must reuse the on-screen report's data "
                        + "path. The report cost " + read + " queries and the export "
                        + "cost " + exported + "; a second, separate aggregation "
                        + "would mean the file could disagree with the screen.");
    }

    private void callOk(String jwt, String url) throws Exception {
        mockMvc.perform(get(url).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    private long measure(String jwt, String url) throws Exception {
        Statistics statistics = statistics();
        mockMvc.perform(get(url).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }
}
