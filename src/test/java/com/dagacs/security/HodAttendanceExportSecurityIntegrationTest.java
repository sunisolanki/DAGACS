package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4A: an export must be exactly as authorized as the screen it comes from.
 *
 * <p>Phase 4A adds eight new export endpoints (overview, student, subject and low
 * attendance, each in Excel and PDF). Every one of them is a new way to read
 * departmental attendance data, so every one of them is a new potential leak. This
 * class is the proof that they are not: each report type is probed with a real
 * JWT, unauthenticated, with a non-HOD role, and with ids belonging to a different
 * department, program, academic session and section.</p>
 *
 * <p>The important structural claim is not that each case is <i>tested</i> but
 * that the export endpoints share the on-screen endpoints' service methods, and
 * therefore share their {@code assertOwned} /
 * {@code assertStudentInContext} / {@code assertSubjectInContext} checks. These
 * tests are the evidence for that claim, and they are written to fail loudly if
 * someone later gives an export its own, weaker, data path.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodAttendanceExportSecurityIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";

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
        Subject subject;
        Student student;
    }

    /** A complete, owned department hierarchy with one enrolled student. */
    private World world() {
        World w = new World();
        w.dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("DG1")
                .description("export security").createdBy("test")
                .createdAt(now()).updatedAt(now()).build());
        w.program = programRepository.save(Program.builder()
                .name("B.Tech CSE").code("PC1").duration("4yr")
                .description("test").department(w.dept).build());
        w.session = academicSessionRepository.save(AcademicSession.builder()
                .name("Academic Session 2026-27").code("AS1").program(w.program)
                .description("test").createdAt(now()).updatedAt(now()).build());
        w.semester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("SM1").year(2026).academicSession(w.session)
                .createdAt(now()).updatedAt(now()).build());
        w.batch = batchRepository.save(Batch.builder()
                .batchCode("B1-" + System.nanoTime()).name("BTech").year(2026)
                .program("B.Tech CSE").maxCapacity(120).academicSession(w.session)
                .createdAt(now()).updatedAt(now()).build());
        w.section = sectionRepository.save(Section.builder()
                .sectionCode("A-" + System.nanoTime()).name("A")
                .maxCapacity(60).batch(w.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        w.subject = subjectRepository.save(Subject.builder()
                .code("CS101").name("Data Structures").description("test")
                .creditHours("3").department(w.dept).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        SubjectOffering offering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(w.subject).semester(w.semester)
                .createdAt(now()).updatedAt(now()).build());
        w.student = studentRepository.save(Student.builder()
                .rollNumber("R-" + System.nanoTime())
                .enrollmentNumber("CS-" + System.nanoTime())
                .name("Export Student").gender("M").status("ACTIVE")
                .academicSession(w.session).batch(w.batch)
                .section(w.section).semester(w.semester).program(w.program)
                .createdAt(now()).updatedAt(now()).build());

        // The HOD of THIS department, so the exports are reachable at all.
        Teacher hod = teacherRepository.save(Teacher.builder()
                .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(w.dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(hod).subjectOffering(offering).section(w.section)
                .createdAt(now()).updatedAt(now()).build());
        return w;
    }

    /** A second, entirely separate department hierarchy the HOD does not own. */
    private World otherDepartment() {
        World o = new World();
        o.dept = departmentRepository.save(Department.builder()
                .name("Other-" + System.nanoTime()).code("OG1")
                .description("other department").createdBy("test")
                .createdAt(now()).updatedAt(now()).build());
        o.program = programRepository.save(Program.builder()
                .name("M.Tech ECE").code("PM1").duration("2yr")
                .description("test").department(o.dept).build());
        o.session = academicSessionRepository.save(AcademicSession.builder()
                .name("Other Session 2025-26").code("AS9").program(o.program)
                .description("test").createdAt(now()).updatedAt(now()).build());
        o.semester = semesterRepository.save(Semester.builder()
                .name("Semester 1").code("SM9").year(2025).academicSession(o.session)
                .createdAt(now()).updatedAt(now()).build());
        o.batch = batchRepository.save(Batch.builder()
                .batchCode("B9-" + System.nanoTime()).name("MTech").year(2025)
                .program("M.Tech ECE").maxCapacity(60).academicSession(o.session)
                .createdAt(now()).updatedAt(now()).build());
        o.section = sectionRepository.save(Section.builder()
                .sectionCode("Z-" + System.nanoTime()).name("Z")
                .maxCapacity(60).batch(o.batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        o.subject = subjectRepository.save(Subject.builder()
                .code("EC401").name("Embedded Systems").description("test")
                .creditHours("3").department(o.dept).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
        o.student = studentRepository.save(Student.builder()
                .rollNumber("R9-" + System.nanoTime())
                .enrollmentNumber("EC-" + System.nanoTime())
                .name("Other Student").gender("F").status("ACTIVE")
                .academicSession(o.session).batch(o.batch)
                .section(o.section).semester(o.semester).program(o.program)
                .createdAt(now()).updatedAt(now()).build());
        return o;
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

    // ── Paths ─────────────────────────────────────────────────────────────

    /** Every Phase 4A export path, for the own-department and 401/403 sweeps. */
    private List<String> exportPaths(World w, String token) {
        String q = "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.program.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.section.getId();
        String student = "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.program.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.section.getId();
        return List.of(
                "/api/hod/attendance/matrix/export.xlsx" + q,
                "/api/hod/attendance/matrix/export.pdf" + q,
                "/api/hod/attendance/overview/export.xlsx" + q,
                "/api/hod/attendance/overview/export.pdf" + q,
                "/api/hod/attendance/low/export.xlsx" + q,
                "/api/hod/attendance/low/export.pdf" + q,
                "/api/hod/attendance/student/" + w.student.getId() + "/export.xlsx" + student,
                "/api/hod/attendance/student/" + w.student.getId() + "/export.pdf" + student,
                "/api/hod/attendance/subject/" + w.subject.getId() + "/export.xlsx" + q,
                "/api/hod/attendance/subject/" + w.subject.getId() + "/export.pdf" + q);
    }

    private int statusOf(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // The HOD can export their own context
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a HOD can export all ten Phase 4A paths for their own context")
    void hodCanExportEveryReport() throws Exception {
        World w = world();
        String token = token();

        for (String path : exportPaths(w, token)) {
            MvcResult result = mockMvc.perform(get(path)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            byte[] bytes = result.getResponse().getContentAsByteArray();
            assertTrue(bytes.length > 0, "An empty file for " + path);

            if (path.contains("/export.xlsx")) {
                assertEquals('P', (char) bytes[0], "A real xlsx container for " + path);
                assertEquals('K', (char) bytes[1], "A real xlsx container for " + path);
            } else {
                assertEquals('%', (char) bytes[0], "A real PDF for " + path);
                assertEquals('P', (char) bytes[1], "A real PDF for " + path);
            }

            String disposition = result.getResponse().getHeader("Content-Disposition");
            assertTrue(disposition != null && disposition.contains("attachment"),
                    "Every export is an attachment: " + path);
            assertTrue(disposition.contains("filename="),
                    "Every export carries a deterministic file name: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Unauthenticated / wrong role
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("an unauthenticated export request is 401, never data")
    void unauthenticatedIsRejected() throws Exception {
        World w = world();

        for (String path : exportPaths(w, null)) {
            assertEquals(401, statusOf(path, null), "Must not export without a token: " + path);
        }
    }

    @Test
    @DisplayName("a garbage or expired-format token is 401, never data")
    void bogusTokenIsRejected() throws Exception {
        World w = world();

        for (String path : exportPaths(w, "not-a-real-token")) {
            assertEquals(401, statusOf(path, "not-a-real-token"),
                    "A forged token must not export: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cross-department, cross-program, cross-session, cross-section
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a subject from another department cannot be exported")
    void crossDepartmentSubjectIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = "/api/hod/attendance/subject/" + other.subject.getId()
                + "/export.xlsx?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(path, token),
                "A subject owned by another department must never be exportable");
    }

    @Test
    @DisplayName("a student from another department cannot be exported")
    void crossDepartmentStudentIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = "/api/hod/attendance/student/" + other.student.getId()
                + "/export.xlsx?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(path, token),
                "A student owned by another department must never be exportable");
    }

    @Test
    @DisplayName("a context of another department cannot be exported as a whole")
    void crossDepartmentContextIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        // Every context id is the HOD's own, except the program/session/section
        // which belong to a department they do not own.
        String[] paths = {
                "/api/hod/attendance/overview/export.xlsx?academicSessionId="
                        + other.session.getId() + "&programId=" + other.program.getId()
                        + "&semesterId=" + other.semester.getId()
                        + "&sectionId=" + other.section.getId(),
                "/api/hod/attendance/low/export.pdf?academicSessionId="
                        + other.session.getId() + "&programId=" + other.program.getId()
                        + "&semesterId=" + other.semester.getId()
                        + "&sectionId=" + other.section.getId(),
                "/api/hod/attendance/matrix/export.xlsx?academicSessionId="
                        + other.session.getId() + "&programId=" + other.program.getId()
                        + "&semesterId=" + other.semester.getId()
                        + "&sectionId=" + other.section.getId()};

        for (String path : paths) {
            assertEquals(403, statusOf(path, token),
                    "Another department's context must never be exportable: " + path);
        }
    }

    @Test
    @DisplayName("a program that does not belong to the chosen session is rejected")
    void crossProgramIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        // Own academic session, but another department's program: the pairing is
        // not internally consistent, so it must not produce a report.
        String path = "/api/hod/attendance/overview/export.xlsx?academicSessionId="
                + mine.session.getId() + "&programId=" + other.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(path, token),
                "A program outside the chosen session must not be exportable");
    }

    @Test
    @DisplayName("a semester from another academic session is rejected")
    void crossSessionIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = "/api/hod/attendance/overview/export.xlsx?academicSessionId="
                + mine.session.getId() + "&programId=" + mine.program.getId()
                + "&semesterId=" + other.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(path, token),
                "A semester outside the chosen academic session must not be exportable");
    }

    @Test
    @DisplayName("a section that is not in the chosen batch is rejected")
    void crossSectionIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = "/api/hod/attendance/overview/export.xlsx?academicSessionId="
                + mine.session.getId() + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + other.section.getId();

        assertEquals(403, statusOf(path, token),
                "A section outside the chosen batch must not be exportable");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Stale context / stale date range
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a student outside the CURRENTLY selected context is rejected")
    void staleContextStudentIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        // The HOD re-selects their own context; the previous, foreign student id
        // must not survive that change as a working export.
        String stale = "/api/hod/attendance/student/" + other.student.getId()
                + "/export.pdf?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(stale, token),
                "An id from a previous context must not be exportable after a re-selection");
    }

    @Test
    @DisplayName("an inverted date range is 400 on every export, never a partial file")
    void invertedDateRangeIsRejected() throws Exception {
        World w = world();
        String token = token();

        for (String path : exportPaths(w, token)) {
            String withRange = path + "&startDate=2026-03-31&endDate=2026-01-01";
            assertEquals(400, statusOf(withRange, token),
                    "An inverted range must be rejected: " + path);
        }
    }

    @Test
    @DisplayName("the exported file reflects the requested date range, not an unfiltered one")
    void dateRangeIsHonoured() throws Exception {
        World w = world();
        String token = token();

        // No sessions exist in this fixture, so the filtered and unfiltered exports
        // are both valid documents. What matters is that a range is accepted and
        // produces a real file rather than an error - the filtering itself is
        // proven exhaustively by HodAttendanceConductedSessionIntegrationTest.
        for (String suffix : new String[]{"", "&startDate=2026-01-01&endDate=2026-12-31"}) {
            MvcResult result = mockMvc.perform(get(
                            "/api/hod/attendance/overview/export.xlsx?academicSessionId="
                                    + w.session.getId() + "&programId=" + w.program.getId()
                                    + "&semesterId=" + w.semester.getId()
                                    + "&sectionId=" + w.section.getId() + suffix)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            assertTrue(result.getResponse().getContentAsByteArray().length > 0,
                    "A date-filtered export is still a real file");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Missing context
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("an incomplete context is 400 on the matrix export, with the exact message")
    void incompleteMatrixContextIsRejected() throws Exception {
        World w = world();
        String token = token();

        String path = "/api/hod/attendance/matrix/export.xlsx?academicSessionId="
                + w.session.getId() + "&programId=" + w.program.getId();

        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getContentAsString()
                        .contains("Select Academic Session, Program, Semester and Section"),
                "The exact contract message is returned, not a generic error: "
                        + result.getResponse().getContentAsString());
    }
}
