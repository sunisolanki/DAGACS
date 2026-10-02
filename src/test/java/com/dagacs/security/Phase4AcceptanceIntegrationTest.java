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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4C.4: the final acceptance gate for Phase 4, exercised over real HTTP.
 *
 * <h3>Why this suite exists at all</h3>
 * <p>4A, 4B, 4C.1, 4C.2 and 4C.3 each proved their own slice, mostly against a
 * mocked report service, because a rendering failure should be identifiable as a
 * rendering failure. The consequence is that <b>no suite had ever asked, over
 * HTTP, that all of it is actually reachable and actually correct at once</b>. This
 * one does, and it is the difference between "each phase passed" and "Phase 4 is
 * complete".</p>
 *
 * <p>What it deliberately does <b>not</b> do is repeat what is already proven in
 * depth elsewhere: the per-report authorization matrix is
 * {@code HodAttendanceExportSecurityIntegrationTest} and
 * {@code HodContextPackSecurityIntegrationTest}; the query budget is
 * {@code HodAttendanceExportQueryCountIntegrationTest}; the pre-existing M7.2 and
 * Teacher export routes are {@code M72ExportIntegrationTest}; the arithmetic itself
 * is {@code HodAttendanceConductedSessionIntegrationTest}. This suite states only
 * the whole-of-Phase-4 claims.</p>
 *
 * <h3>The four claims</h3>
 * <ol>
 *   <li>every Phase 4 export route returns a real file of the right container;</li>
 *   <li>every Phase 4 <b>PDF</b> route carries {@code Page X of Y} - the 4C.1
 *       infrastructure proven reachable over HTTP, not only at unit level;</li>
 *   <li>an export's attendance figures equal the on-screen report's, which is the
 *       "the export layer changed no arithmetic" claim stated on real numbers;</li>
 *   <li>the Phase 4 routes reject the other three roles exactly as the older HOD
 *       routes do.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class Phase4AcceptanceIntegrationTest {

    private static final String HOD_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String STUDENT_EMAIL = "student@dagacs.local";
    private static final String STUDENT_PASSWORD = "Student@123";
    private static final String TEACHER_EMAIL = "teacher@dagacs.local";
    private static final String TEACHER_PASSWORD = "Teacher@123";

    private static final String BASE = "/api/hod/attendance";
    private static final int CLASSES_PER_SUBJECT = 3;

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
    @Autowired private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;

    private Department department;
    private Program program;
    private AcademicSession session;
    private Semester semester;
    private Batch batch;
    private Section section;
    private Subject subject;
    private Teacher hod;
    private List<Student> students = new ArrayList<>();

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String tag() {
        return String.valueOf(System.nanoTime());
    }

    /**
     * One complete, owned hierarchy with two subjects and real conducted classes.
     *
     * <p>The seeded HOD profile is re-pointed at this department, so this test's
     * exports are inside the HOD's own scope and cannot be satisfied by accident.
     * Every enrolled student is marked present in every class, so every report
     * has real numbers to compare against.</p>
     */
    private void world() {
        department = departmentRepository.save(Department.builder()
                .name("Dept-" + tag()).code("A4" + tag().substring(0, 6))
                .description("phase 4 acceptance").createdBy("test")
                .createdAt(now()).updatedAt(now()).build());
        program = programRepository.save(Program.builder()
                .name("B.Tech CSE").code("P" + tag().substring(0, 6)).duration("4yr")
                .description("test").department(department).build());
        session = academicSessionRepository.save(AcademicSession.builder()
                .name("Academic Session 2026-27").code("S" + tag().substring(0, 6))
                .program(program).description("test")
                .createdAt(now()).updatedAt(now()).build());
        semester = semesterRepository.save(Semester.builder()
                .name("Semester 3").code("M" + tag().substring(0, 6)).year(2026)
                .academicSession(session).createdAt(now()).updatedAt(now()).build());
        batch = batchRepository.save(Batch.builder()
                .batchCode("B-" + tag()).name("BTech").year(2026)
                .program("B.Tech CSE").maxCapacity(1000).academicSession(session)
                .createdAt(now()).updatedAt(now()).build());
        section = sectionRepository.save(Section.builder()
                .sectionCode("S-" + tag()).name("A").maxCapacity(1000)
                .batch(batch).status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());

        subject = subjectRepository.save(Subject.builder()
                .code("CS" + tag().substring(0, 6)).name("Data Structures")
                .description("test").creditHours("3").department(department)
                .status("ACTIVE").createdAt(now()).updatedAt(now()).build());
        SubjectOffering offering = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(now()).updatedAt(now()).build());

        hod = teacherRepository.findByEmail(HOD_EMAIL)
                .orElseGet(() -> teacherRepository.save(Teacher.builder()
                        .email(HOD_EMAIL).password("ignored").fullName("HOD User")
                        .phone("").designation("Professor").status("ACTIVE")
                        .avatarUrl("").isHod(true)
                        .createdAt(now()).updatedAt(now()).build()));
        hod.setDepartment(department);
        hod.setIsHod(true);
        hod.setStatus("ACTIVE");
        teacherRepository.save(hod);
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(hod).subjectOffering(offering).section(section).batch(batch)
                .createdAt(now()).updatedAt(now()).build());

        for (int i = 0; i < 3; i++) {
            students.add(studentRepository.save(Student.builder()
                    .rollNumber("R-" + tag()).enrollmentNumber("QE-" + tag())
                    .name("Student " + i).gender("M").status("ACTIVE")
                    .academicSession(session).batch(batch).section(section)
                    .semester(semester).program(program)
                    .createdAt(now()).updatedAt(now()).build()));
        }
        entityManager.flush();
        entityManager.clear();

        // Conduct every class and mark every student present in each.
        for (int day = 0; day < CLASSES_PER_SUBJECT; day++) {
            String date = LocalDate.of(2026, 5, 1).plusDays(day).toString();
            AttendanceSession lecture = attendanceSessionRepository.save(
                    AttendanceSession.builder()
                            .subjectEntity(subject).sectionEntity(section)
                            .batchEntity(batch).teacherEntity(hod)
                            .subject(subject.getName()).section(section.getName())
                            .batch(batch.getBatchCode()).teacher(hod.getFullName())
                            .lecturePeriod("LP1").date(date).status("CONDUCTED")
                            .createdAt(now()).updatedAt(now()).build());
            for (Student student : students) {
                attendanceRecordRepository.save(AttendanceRecord.builder()
                        .session(lecture).student(student).subject(subject)
                        .section(section).batch(batch).markedBy(hod)
                        .status("PRESENT").lecturePeriod("LP1").date(date)
                        .isPresent(true).createdAt(now()).build());
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    private String token(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String contextQuery() {
        return "?academicSessionId=" + session.getId()
                + "&programId=" + program.getId()
                + "&semesterId=" + semester.getId()
                + "&sectionId=" + section.getId();
    }

    private byte[] export(String jwt, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition, path + " must be an attachment");
        assertTrue(disposition.contains("attachment"), path + " disposition: " + disposition);
        return result.getResponse().getContentAsByteArray();
    }

    /** The twelve report-export paths, as (name, path, isPdf). */
    private List<String[]> reportExports() {
        return List.of(
                new String[]{"overview", BASE + "/overview/export", "x"},
                new String[]{"matrix", BASE + "/matrix/export", "x"},
                new String[]{"student", BASE + "/student/" + students.get(0).getId() + "/export", "x"},
                new String[]{"subject", BASE + "/subject/" + subject.getId() + "/export", "x"},
                new String[]{"low", BASE + "/low/export", "x"},
                new String[]{"context-pack", BASE + "/context-pack/export", "x"});
    }

    private static boolean startsWith(byte[] bytes, String magic) {
        return new String(bytes, 0, Math.min(bytes.length, magic.length()),
                StandardCharsets.US_ASCII).equals(magic);
    }

    private static String pdfTextOf(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader =
                new com.lowagie.text.pdf.PdfReader(pdf);
        try {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                    new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                sb.append(extractor.getTextFromPage(page)).append(' ');
            }
            return sb.toString().replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }

    private static int pdfPagesOf(byte[] pdf) throws Exception {
        try (com.lowagie.text.pdf.PdfReader reader =
                     new com.lowagie.text.pdf.PdfReader(pdf)) {
            return reader.getNumberOfPages();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Claim 1 - every route produces a real file
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. every Phase 4 export route returns a real file in both formats")
    void everyExportRouteReturnsARealFile() throws Exception {
        world();
        String jwt = token(HOD_EMAIL, HOD_PASSWORD);

        for (String[] report : reportExports()) {
            byte[] xlsx = export(jwt, report[1] + ".xlsx" + contextQuery());
            byte[] pdf = export(jwt, report[1] + ".pdf" + contextQuery());

            assertTrue(xlsx.length > 100,
                    report[0] + ".xlsx must be a real file, was " + xlsx.length + " bytes");
            assertTrue(startsWith(xlsx, "PK"), report[0] + ".xlsx must be a zip container");
            assertTrue(pdf.length > 100,
                    report[0] + ".pdf must be a real file, was " + pdf.length + " bytes");
            assertTrue(startsWith(pdf, "%PDF-"), report[0] + ".pdf must be a PDF container");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Claim 2 - the 4C infrastructure is reachable over HTTP
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2. every Phase 4 PDF route carries continuous Page X of Y")
    void everyPdfRouteCarriesTheFooter() throws Exception {
        world();
        String jwt = token(HOD_EMAIL, HOD_PASSWORD);

        for (String[] report : reportExports()) {
            byte[] pdf = export(jwt, report[1] + ".pdf" + contextQuery());
            int pages = pdfPagesOf(pdf);
            String text = pdfTextOf(pdf);

            assertTrue(text.contains("Page 1 of " + pages),
                    "the " + report[0] + " PDF must open with a resolved total, so the "
                            + "4C page-counting is reachable over HTTP. Read: ["
                            + text.substring(Math.max(0, text.length() - 80)) + "]");
            // No page may claim to be a document of one page inside a longer one.
            assertTrue(!text.contains("Page 1 of 1") || pages == 1,
                    "the " + report[0] + " PDF has " + pages + " pages, so no page "
                            + "may read 'Page 1 of 1'");
        }
    }

    @Test
    @DisplayName("3. the context pack PDF is one document, not five concatenated files")
    void contextPackPdfIsOneDocument() throws Exception {
        world();
        String jwt = token(HOD_EMAIL, HOD_PASSWORD);

        byte[] pdf = export(jwt, BASE + "/context-pack/export.pdf" + contextQuery());
        int pages = pdfPagesOf(pdf);

        // Five sections, each starting on its own page, numbered as one document.
        assertTrue(pages >= 5,
                "five sections must occupy at least five pages, got " + pages);
        String text = pdfTextOf(pdf);
        for (String section : List.of("Executive Summary", "Attendance Matrix",
                "Low Attendance", "Subject Summary", "Student Summary")) {
            assertTrue(text.contains(section), "the " + section + " section must be present");
        }
        for (int i = 1; i <= pages; i++) {
            assertTrue(text.contains("Page " + i + " of " + pages),
                    "page " + i + " must be numbered inside the whole document");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Claim 3 - the export layer changed no arithmetic
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4. the exported figures equal the on-screen figures")
    void exportedFiguresMatchTheOnScreenReport() throws Exception {
        world();
        String jwt = token(HOD_EMAIL, HOD_PASSWORD);

        // The on-screen overview, straight from the API.
        String overviewJson = mockMvc.perform(get(BASE + "/overview" + contextQuery())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String overall = objectMapper.readTree(overviewJson).get("overallPercentage")
                .asText();
        assertNotNull(overall);

        // The same figure must appear in every rendered form of that report. All
        // three students were present in all three classes, so it is 100.00%.
        String expected = String.format(Locale.ROOT, "%.2f%%", Double.parseDouble(overall));

        assertTrue(pdfTextOf(export(jwt, BASE + "/overview/export.pdf" + contextQuery()))
                        .contains(expected),
                "the overview PDF must print the API's own percentage " + expected);
        assertTrue(pdfTextOf(export(jwt, BASE + "/context-pack/export.pdf" + contextQuery()))
                        .contains(expected),
                "the Context Pack PDF must print the same percentage " + expected);

        // And the workbook, which is the format Phase 4 introduced first.
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                             new java.io.ByteArrayInputStream(
                                     export(jwt, BASE + "/overview/export.xlsx"
                                             + contextQuery())))) {
            boolean found = false;
            for (int r = 0; r <= workbook.getSheetAt(0).getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = workbook.getSheetAt(0).getRow(r);
                if (row == null) {
                    continue;
                }
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(0);
                if (cell != null && cell.getCellType()
                        == org.apache.poi.ss.usermodel.CellType.STRING
                        && cell.getStringCellValue().startsWith("Overall Attendance:")) {
                    assertTrue(cell.getStringCellValue().contains(expected),
                            "the workbook must print the API's own percentage "
                                    + expected + ", read: " + cell.getStringCellValue());
                    found = true;
                }
            }
            assertTrue(found, "the overview header must state the overall percentage");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Claim 4 - the Phase 4 routes reject the other three roles
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5. a non-HOD role is refused every Phase 4 export route")
    void nonHodRolesAreRefused() throws Exception {
        world();
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD);
        String teacher = token(TEACHER_EMAIL, TEACHER_PASSWORD);
        String student = token(STUDENT_EMAIL, STUDENT_PASSWORD);

        for (String[] report : reportExports()) {
            for (String path : List.of(report[1] + ".xlsx" + contextQuery(),
                    report[1] + ".pdf" + contextQuery())) {
                for (String role : List.of(admin, teacher, student)) {
                    mockMvc.perform(get(path).header("Authorization", "Bearer " + role))
                            .andExpect(status().isForbidden());
                }
            }
        }
    }

    @Test
    @DisplayName("6. an unauthenticated Phase 4 export is 401, never data")
    void unauthenticatedIsRefused() throws Exception {
        world();
        for (String[] report : reportExports()) {
            for (String path : List.of(report[1] + ".xlsx" + contextQuery(),
                    report[1] + ".pdf" + contextQuery())) {
                mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Filenames - the established conventions must still hold
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("7. the attachment names follow the established conventions")
    void attachmentNamesFollowTheConventions() throws Exception {
        world();
        String jwt = token(HOD_EMAIL, HOD_PASSWORD);

        // The four reports Phase 4A introduced, plus the two Phase 4 formats.
        assertTrue(nameOf(jwt, BASE + "/overview/export.pdf" + contextQuery())
                .startsWith("DAGACS_Attendance_Overview"));
        assertTrue(nameOf(jwt, BASE + "/low/export.pdf" + contextQuery())
                .startsWith("DAGACS_Attendance_Low"));
        assertTrue(nameOf(jwt, BASE + "/context-pack/export.pdf" + contextQuery())
                .startsWith("DAGACS_Attendance_ContextPack"));
        assertTrue(nameOf(jwt, BASE + "/context-pack/export.pdf" + contextQuery())
                .endsWith(".pdf"));
        // The matrix keeps its Phase 3 name verbatim - 4A deliberately did not
        // change it, so this is the regression that would catch someone doing so.
        assertTrue(nameOf(jwt, BASE + "/matrix/export.pdf" + contextQuery())
                .startsWith("dagacs_hod_attendance_matrix_"));
    }

    private String nameOf(String jwt, String path) throws Exception {
        String disposition = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition, path);
        int equals = disposition.indexOf("filename=");
        assertTrue(equals >= 0, disposition);
        return disposition.substring(equals + "filename=".length())
                .replace("\"", "").trim();
    }
}
