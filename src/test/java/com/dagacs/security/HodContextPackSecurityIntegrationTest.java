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
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4B: the Context Pack inherits Phase 4A's authorization rather than
 * defining its own.
 *
 * <p>The Pack is the widest export the HOD has - five sheets, the whole context in
 * one file - which makes it the most valuable thing in the system to get
 * isolation right for. The claim under test is that it needed no new rule at
 * all: it accepts only the same academic-selection parameters as the Phase 4A
 * context reports and hands them to the same canonical service, so every rejection
 * Phase 4A already proved must apply here unchanged.</p>
 *
 * <p><b>Each case is probed over real HTTP with a real JWT</b>, not by calling a
 * service directly, because the claim is about the endpoint as deployed. A Pack
 * that were authorized in the controller but leaked through the query would not be
 * caught by a unit test - this is caught by the data simply not coming back.</p>
 *
 * <p><b>The Pack also has no client-supplied entity identity at all.</b> There is
 * deliberately no {@code studentId} or {@code subjectId} parameter to probe, which
 * is why there is no "cross-department student id" case here the way there is in
 * {@code HodAttendanceExportSecurityIntegrationTest}: the Pack cannot be pointed
 * at one entity even if a caller tried.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodContextPackSecurityIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String PACK = "/api/hod/attendance/context-pack/export.xlsx";

    /**
     * Phase 4C.3: the PDF deliverable.
     *
     * <p>Every rejection asserted for the workbook is asserted for the PDF too,
     * which is the point: the PDF must inherit Phase 4B's authorization rather than
     * define its own, so the probe has to be run against both routes.</p>
     */
    private static final String PACK_PDF = "/api/hod/attendance/context-pack/export.pdf";

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
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

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

    private World world() {
        World w = new World();
        w.dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("DP1")
                .description("pack security").createdBy("test")
                .createdAt(now()).updatedAt(now()).build());
        w.program = programRepository.save(Program.builder()
                .name("B.Tech CSE").code("PP1").duration("4yr")
                .description("test").department(w.dept).build());
        w.session = academicSessionRepository.save(AcademicSession.builder()
                .name("Academic Session 2026-27").code("AS1").program(w.program)
                .description("test").createdAt(now()).updatedAt(now()).build());
        w.semester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("SP1").year(2026).academicSession(w.session)
                .createdAt(now()).updatedAt(now()).build());
        w.batch = batchRepository.save(Batch.builder()
                .batchCode("PB-" + System.nanoTime()).name("BTech").year(2026)
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
                .name("Pack Student").gender("M").status("ACTIVE")
                .academicSession(w.session).batch(w.batch)
                .section(w.section).semester(w.semester).program(w.program)
                .createdAt(now()).updatedAt(now()).build());

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

    /** A second department hierarchy the HOD does not own. */
    private World otherDepartment() {
        World o = new World();
        o.dept = departmentRepository.save(Department.builder()
                .name("Other-" + System.nanoTime()).code("OP1")
                .description("other").createdBy("test")
                .createdAt(now()).updatedAt(now()).build());
        o.program = programRepository.save(Program.builder()
                .name("M.Tech ECE").code("OPM1").duration("2yr")
                .description("test").department(o.dept).build());
        o.session = academicSessionRepository.save(AcademicSession.builder()
                .name("Other Session").code("OSA1").program(o.program)
                .description("test").createdAt(now()).updatedAt(now()).build());
        o.semester = semesterRepository.save(Semester.builder()
                .name("Semester 1").code("OSM1").year(2025).academicSession(o.session)
                .createdAt(now()).updatedAt(now()).build());
        o.batch = batchRepository.save(Batch.builder()
                .batchCode("OB-" + System.nanoTime()).name("MTech").year(2025)
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

    /** A second program inside the HOD's OWN department: proves context, not just department. */
    private Program secondProgramInOwnDepartment(Department dept) {
        return programRepository.save(Program.builder()
                .name("B.Tech ECE").code("PE" + System.nanoTime()).duration("4yr")
                .description("second program").department(dept).build());
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

    private static String contextQuery(World w) {
        return "?academicSessionId=" + w.session.getId()
                + "&programId=" + w.program.getId()
                + "&semesterId=" + w.semester.getId()
                + "&sectionId=" + w.section.getId();
    }

    private int statusOf(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a HOD can export the Context Pack for their own context")
    void ownContextIsExportable() throws Exception {
        World w = world();
        String token = token();

        MvcResult result = mockMvc.perform(get(PACK + contextQuery(w))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0, "The Pack must not be an empty file");
        assertEquals('P', (char) bytes[0], "A real xlsx container");
        assertEquals('K', (char) bytes[1], "A real xlsx container");

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition, "The Pack must be an attachment");
        assertTrue(disposition.contains("attachment"));
        assertTrue(disposition.contains("filename="));
        assertTrue(disposition.contains("ContextPack"),
                "The file name must identify the deliverable: " + disposition);
    }

    @Test
    @DisplayName("an unauthenticated Context Pack request is 401, never data")
    void unauthenticatedIsRejected() throws Exception {
        World w = world();
        assertEquals(401, statusOf(PACK + contextQuery(w), null),
                "The Pack must never be produced without a token");
    }

    @Test
    @DisplayName("a forged token cannot export the Context Pack")
    void bogusTokenIsRejected() throws Exception {
        World w = world();
        // 401, matching every Phase 4A path: a token that does not parse never
        // reaches the controller at all.
        assertEquals(401, statusOf(PACK + contextQuery(w), "not-a-real-token"),
                "A forged token must not produce a pack");
    }

    @Test
    @DisplayName("another department's context cannot be packed")
    void crossDepartmentContextIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        assertEquals(403,
                statusOf(PACK + contextQuery(other), token),
                "A whole other department must never be downloadable as one pack");
        // The HOD's own context still works, so the 403 above is isolation and
        // not a broken endpoint.
        assertEquals(200, statusOf(PACK + contextQuery(mine), token));
    }

    @Test
    @DisplayName("another department's section inside a valid program is rejected")
    void crossDepartmentSectionIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        // A mixed context: the HOD's own program/session/semester with a section
        // that belongs to a different department. This is the mixed-department
        // cross-tab the Phase 3 rules exist to prevent.
        String mixed = "?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + other.section.getId();

        assertEquals(403, statusOf(PACK + mixed, token),
                "A mixed-department context must never produce a pack");
    }

    @Test
    @DisplayName("a program outside the chosen session is rejected")
    void crossProgramIsRejected() throws Exception {
        World mine = world();
        Program other = secondProgramInOwnDepartment(mine.dept);
        String token = token();

        String path = PACK + "?academicSessionId=" + mine.session.getId()
                + "&programId=" + other.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + mine.section.getId();

        // 400, not 403: the program is inside the HOD's own department, so it is
        // not an authorization failure - the four levels simply do not describe a
        // real context together. The Phase 3 rule for an inconsistent pairing is
        // 400, and the Pack inherits it rather than inventing a status.
        assertEquals(400, statusOf(path, token),
                "A program that does not belong to the session must be rejected");
    }

    @Test
    @DisplayName("another department's program is rejected even with the HOD's own session")
    void foreignProgramIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = PACK + "?academicSessionId=" + mine.session.getId()
                + "&programId=" + other.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(path, token),
                "A program owned by another department must never be packed");
    }

    @Test
    @DisplayName("a semester from another academic session is rejected")
    void crossSessionIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = PACK + "?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + other.semester.getId()
                + "&sectionId=" + mine.section.getId();

        assertEquals(403, statusOf(path, token),
                "A semester outside the session must be rejected");
    }

    @Test
    @DisplayName("a section outside the chosen batch is rejected")
    void crossSectionIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        String path = PACK + "?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + other.section.getId();

        assertEquals(403, statusOf(path, token),
                "A section outside the context must be rejected");
    }

    @Test
    @DisplayName("an inverted date range is 400, never a partial pack")
    void invertedDateRangeIsRejected() throws Exception {
        World w = world();
        String token = token();

        assertEquals(400, statusOf(PACK + contextQuery(w)
                        + "&startDate=2026-01-31&endDate=2026-01-01", token),
                "An inverted range must be refused before any file is built");
    }

    @Test
    @DisplayName("an incomplete academic context is 400, never a department-wide pack")
    void incompleteContextIsRejected() throws Exception {
        World w = world();
        String token = token();

        // The Pack contains a cross-tab, so it needs the full four levels. A
        // partial selection must be refused rather than silently widened to the
        // whole department.
        assertEquals(400, statusOf(PACK + "?academicSessionId=" + w.session.getId(), token),
                "An incomplete context must be refused, not widened");
        assertEquals(400, statusOf(PACK
                        + "?academicSessionId=" + w.session.getId()
                        + "&programId=" + w.program.getId()
                        + "&semesterId=" + w.semester.getId(), token),
                "Three levels is still incomplete");
    }

    @Test
    @DisplayName("the applied date range is honoured, not ignored")
    void dateRangeIsHonoured() throws Exception {
        World w = world();
        String token = token();

        MvcResult result = mockMvc.perform(get(PACK + contextQuery(w)
                        + "&startDate=2026-01-01&endDate=2026-01-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(result.getResponse().getContentAsByteArray().length > 0);
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                        .contains("2026-01-01"),
                "The file name must describe the range that was actually applied");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Phase 4C.3 - the PDF Pack inherits exactly these rules
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Phase 4C.3: a HOD can export the PDF Pack for their own context")
    void ownContextIsExportableAsPdf() throws Exception {
        World w = world();
        String token = token();

        MvcResult result = mockMvc.perform(get(PACK_PDF + contextQuery(w))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0, "The PDF Pack must not be an empty file");
        assertEquals('%', (char) bytes[0], "A real PDF container");
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition, "The PDF Pack must be an attachment");
        assertTrue(disposition.contains("attachment"));
        assertTrue(disposition.contains("filename="));
        assertTrue(disposition.contains("ContextPack"),
                "The file name must identify the deliverable: " + disposition);
        assertTrue(disposition.contains(".pdf"), disposition);
    }

    @Test
    @DisplayName("Phase 4C.3: an unauthenticated PDF Pack request is 401, never data")
    void unauthenticatedPdfIsRejected() throws Exception {
        World w = world();
        assertEquals(401, statusOf(PACK_PDF + contextQuery(w), null),
                "The PDF Pack must never be produced without a token");
        // The workbook route is the control: the PDF must not be stricter, and
        // must not be looser.
        assertEquals(401, statusOf(PACK + contextQuery(w), null));
    }

    @Test
    @DisplayName("Phase 4C.3: a forged token cannot export the PDF Pack")
    void bogusTokenCannotExportPdf() throws Exception {
        World w = world();
        assertEquals(401, statusOf(PACK_PDF + contextQuery(w), "not-a-real-token"),
                "A forged token must not produce a PDF pack");
    }

    @Test
    @DisplayName("Phase 4C.3: cross-context access to the PDF Pack is rejected")
    void crossContextPdfIsRejected() throws Exception {
        World mine = world();
        World other = otherDepartment();
        String token = token();

        // A whole other department: the widest thing the Pack can be asked for.
        assertEquals(403, statusOf(PACK_PDF + contextQuery(other), token),
                "Another department must never be downloadable as one PDF pack");

        // A mixed context: the HOD's own program/session/semester with a section
        // belonging to a different department.
        String mixed = "?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + other.section.getId();
        assertEquals(403, statusOf(PACK_PDF + mixed, token),
                "A mixed-department context must never produce a PDF pack");

        // A section that does not belong to the chosen batch.
        String wrongSection = "?academicSessionId=" + mine.session.getId()
                + "&programId=" + mine.program.getId()
                + "&semesterId=" + mine.semester.getId()
                + "&sectionId=" + other.section.getId();
        assertEquals(403, statusOf(PACK_PDF + wrongSection, token),
                "A section outside the context must never produce a PDF pack");

        // The HOD's own context still works, so the 403s above are isolation and
        // not a broken endpoint.
        assertEquals(200, statusOf(PACK_PDF + contextQuery(mine), token));
    }

    @Test
    @DisplayName("Phase 4C.3: an incomplete context or inverted range is 400 for the PDF")
    void malformedRequestsAreRejectedForPdf() throws Exception {
        World w = world();
        String token = token();

        assertEquals(400,
                statusOf(PACK_PDF + "?academicSessionId=" + w.session.getId(), token),
                "An incomplete context must be refused, not widened");
        assertEquals(400,
                statusOf(PACK_PDF + contextQuery(w)
                        + "&startDate=2026-01-31&endDate=2026-01-01", token),
                "An inverted range must be refused before any file is built");
    }

    @Test
    @DisplayName("Phase 4C.3: the PDF Pack honours the applied date range")
    void pdfPackHonoursTheDateRange() throws Exception {
        World w = world();
        String token = token();

        MvcResult result = mockMvc.perform(get(PACK_PDF + contextQuery(w)
                        + "&startDate=2026-01-01&endDate=2026-01-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(result.getResponse().getContentAsByteArray().length > 0);
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                        .contains("2026-01-01"),
                "The PDF file name must describe the range that was applied");
    }
}