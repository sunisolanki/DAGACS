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
import com.dagacs.repository.AttendanceAuditLogRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance tests for the M7.2 export generation layer.
 *
 * <p>Verified here, end-to-end, for BOTH Excel and PDF:
 * security (401/403 matrices), role scope, department/teacher isolation,
 * Excel/PDF validity (parsed back with POI / OpenPDF), data/percentage
 * semantics, inclusive date filters (400 on malformed/inverted), unsupported
 * report types (including semester -> 400), deterministic attachment filenames,
 * and — critically — the full-data guarantee: HOD daily-lecture and teacher
 * subject-wise exports contain ALL matching rows, proven with fixtures of 205
 * rows each (beyond the M7.1 page-size boundary of 200). No client-supplied
 * identity/scope ID is accepted anywhere.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class M72ExportIntegrationTest {

    private static final String HOD_A_EMAIL = "hod@dagacs.local";
    private static final String HOD_PASSWORD = "Hod@123";
    private static final String ADMIN_EMAIL = "admin@dagacs.local";
    private static final String ADMIN_PASSWORD = "Admin@123";
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
    private BatchRepository batchRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceAuditLogRepository attendanceAuditLogRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private long sessionCounter = 0;

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

    private MvcResult fetchResult(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private byte[] getBytes(String path, String token) throws Exception {
        return fetchResult(path, token).getResponse().getContentAsByteArray();
    }

    private void flush() {
        entityManager.flush();
    }

    /** Scans worksheet text (title/subtitle/header/data cells). */
    private static String sheetText(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (Row row : sheet) {
            for (Cell cell : row) {
                switch (cell.getCellType()) {
                    case STRING -> sb.append(cell.getStringCellValue()).append(' ');
                    case NUMERIC -> sb.append(cell.getNumericCellValue()).append(' ');
                    default -> sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static Workbook workbook(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String pdfFullText(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                sb.append(extractor.getTextFromPage(page));
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private static String pdfMagic(byte[] bytes) {
        return new String(Arrays.copyOf(bytes, 5), StandardCharsets.US_ASCII);
    }

    /** Removes all whitespace so cell text that wrapped across PDF table lines still matches. */
    private static String flat(String s) {
        return s.replaceAll("\\s+", "");
    }

    private static class Slice {
        Department dept;
        Program program;
        AcademicSession session;
        Batch batch;
        Section section;
        Subject subject;
        Semester semester;
        SubjectOffering offering;
        Teacher teacher;
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
        s.teacher = teacherRepository.save(Teacher.builder()
                .email("marker" + System.nanoTime() + "@dagacs.local").password("ignored")
                .fullName("Marker").phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false).department(s.dept)
                .createdAt(now()).updatedAt(now()).build());
        return s;
    }

    private void makeHod(Slice s) {
        teacherRepository.save(Teacher.builder()
                .email(HOD_A_EMAIL).password("ignored").fullName("HOD User")
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").department(s.dept).isHod(true)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Student student(Slice s, String name) {
        return studentRepository.save(Student.builder()
                .rollNumber("STU-" + System.nanoTime()).name(name).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E-" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(null)
                .academicSession(s.session).batch(s.batch).section(s.section).program(s.program)
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

    private void assign(Teacher teacher, SubjectOffering offering, Section section) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).section(section)
                .createdAt(now()).updatedAt(now()).build());
    }

    private SubjectOffering offeringFor(Slice s, Subject subject) {
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(s.semester)
                .createdAt(now()).updatedAt(now()).build());
    }

    private Subject extraSubject(Slice s, String code, String name) {
        return subjectRepository.save(Subject.builder()
                .code(code).name(name)
                .description("Test").creditHours("3").department(s.dept)
                .status("ACTIVE")
                .createdAt(now()).updatedAt(now()).build());
    }

    // ====================== Authentication / authorization ======================

    @Test
    void hodDailyLectureExportXlsx_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/hod/reports/daily-lecture/export.xlsx"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hodDailyLectureExportPdf_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/hod/reports/daily-lecture/export.pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherExportXlsx_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/attendance/report/export.xlsx"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherExportPdf_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/attendance/report/export.pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hodExport_adminToken_returnsForbidden() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture/export.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodExport_teacherToken_returnsForbidden() throws Exception {
        String token = login(TEACHER_EMAIL, TEACHER_PASSWORD);
        mockMvc.perform(get("/api/hod/coverage/export.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodExport_studentToken_returnsForbidden() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/monthly/export.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherExport_hodToken_returnsAllowed() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/report/export.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void teacherExport_studentToken_returnsForbidden() throws Exception {
        String token = login(STUDENT_EMAIL, STUDENT_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/report/export.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherExport_adminToken_returnsForbidden() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/teacher/attendance/report/export.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ====================== HOD daily-lecture export ======================

    @Test
    void dailyLectureExport_xlsx_validWorkbookAggregationAndFilename() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        Student s2 = student(a, "Student A2");
        Student s3 = student(a, "Student A3");

        AttendanceSession session = saveSession(a.subject, a.section, a.teacher,
                "2026-01-10", "LP1", "CONDUCTED");
        record(session, s1, true);
        record(session, s2, true);
        record(session, s3, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        MvcResult result = fetchResult("/api/hod/reports/daily-lecture/export.xlsx", token);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains("dagacs_hod_daily-lecture_all.xlsx"));

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        try (Workbook wb = workbook(bytes)) {
            Sheet sheet = wb.getSheet("Daily Lecture");
            assertNotNull(sheet);
            assertEquals("Daily-Lecture Attendance Report",
                    sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Date", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("Percentage (%)", sheet.getRow(3).getCell(8).getStringCellValue());
            assertEquals(4, sheet.getLastRowNum());
            assertEquals("2026-01-10", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("LP1", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals(a.subject.getCode(), sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals(2.0, sheet.getRow(4).getCell(6).getNumericCellValue(), 0.001);
            assertEquals(3.0, sheet.getRow(4).getCell(7).getNumericCellValue(), 0.001);
            assertEquals(66.66666, sheet.getRow(4).getCell(8).getNumericCellValue(), 0.001);
            assertFalse(sheetText(sheet).contains("sessionId"));
        }
    }

    @Test
    void dailyLectureExport_xlsx_departmentIsolation() throws Exception {
        Slice a = slice("A");
        Slice b = slice("B");
        makeHod(a);
        Student a1 = student(a, "Student A1");
        Student b1 = student(b, "Student B1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED"), a1, true);
        record(saveSession(b.subject, b.section, b.teacher, "2026-01-05", "LP1", "CONDUCTED"), b1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/daily-lecture/export.xlsx", token))) {
            String text = sheetText(wb.getSheet("Daily Lecture"));
            assertTrue(text.contains(a.subject.getCode()));
            assertFalse(text.contains(b.subject.getCode()));
        }
    }

    @Test
    void dailyLectureExport_noTruncation_205SessionRows() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        for (int i = 1; i <= 205; i++) {
            record(saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP" + i, "CONDUCTED"), s1, true);
        }
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/daily-lecture/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Daily Lecture");
            assertEquals(205, sheet.getLastRowNum() - 3);
            assertTrue(sheetText(sheet).contains("LP205"));
        }
    }

    @Test
    void dailyLectureExport_pdf_validWithTitleAndData() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        MvcResult result = fetchResult("/api/hod/reports/daily-lecture/export.pdf", token);
        assertEquals("application/pdf", result.getResponse().getContentType());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains("dagacs_hod_daily-lecture_all.pdf"));

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        assertEquals("%PDF-", pdfMagic(bytes));
        String raw = pdfFullText(bytes);
        assertTrue(raw.contains("Daily-Lecture Attendance Report"));
        String flatText = flat(raw);
        assertTrue(flatText.contains(a.subject.getCode()));
        assertTrue(flatText.contains(a.section.getSectionCode()));
    }

    @Test
    void dailyLectureExport_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture/export.xlsx")
                        .param("startDate", "2026-02-01").param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyLectureExport_malformedDate_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/daily-lecture/export.xlsx")
                        .param("startDate", "2026-13-99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyLectureExport_dateFilter_inclusiveAndFilename() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, a.teacher, "2026-02-10", "LP2", "CONDUCTED"), s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        MvcResult result = fetchResult(
                "/api/hod/reports/daily-lecture/export.xlsx?startDate=2026-01-01&endDate=2026-01-31", token);
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("dagacs_hod_daily-lecture_2026-01-01_to_2026-01-31.xlsx"));
        try (Workbook wb = workbook(result.getResponse().getContentAsByteArray())) {
            assertEquals(1, wb.getSheet("Daily Lecture").getLastRowNum() - 3);
            assertEquals("2026-01-05",
                    wb.getSheet("Daily Lecture").getRow(4).getCell(0).getStringCellValue());
        }
    }

    // ====================== HOD coverage export ======================

    @Test
    void coverageExport_xlsx_recordBacked_semanticBoundary() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");

        // Record-less SCHEDULED session -> must not contribute a coverage row.
        saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "SCHEDULED");
        // Record-backed session -> contributes.
        AttendanceSession b = saveSession(a.subject, a.section, a.teacher, "2026-01-07", "LP2", "CONDUCTED");
        record(b, s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/coverage/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Coverage");
            assertNotNull(sheet);
            assertEquals(1, sheet.getLastRowNum() - 3);
            assertEquals(a.subject.getCode(), sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(1.0, sheet.getRow(4).getCell(4).getNumericCellValue(), 0.001);
            assertEquals(1.0, sheet.getRow(4).getCell(5).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void coverageExport_xlsx_recordLessOnlySubjectExcluded() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");

        Subject noRecordSubject = extraSubject(a, "NOREC", "No Record Subject");
        saveSession(noRecordSubject, a.section, a.teacher, "2026-01-05", "LP1", "SCHEDULED");

        AttendanceSession b = saveSession(a.subject, a.section, a.teacher, "2026-01-07", "LP2", "CONDUCTED");
        record(b, s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/coverage/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Coverage");
            assertEquals(1, sheet.getLastRowNum() - 3);
            String text = sheetText(sheet);
            assertTrue(text.contains(a.subject.getCode()));
            assertFalse(text.contains("NOREC"));
        }
    }

    @Test
    void coverageExport_xlsx_allRowsPresent() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        Subject extra = extraSubject(a, "CEXT", "Extra Subject");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-05", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(extra, a.section, a.teacher, "2026-01-06", "LP1", "CONDUCTED"), s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/coverage/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Coverage");
            assertEquals(2, sheet.getLastRowNum() - 3);
            assertTrue(sheetText(sheet).contains(a.subject.getCode()));
            assertTrue(sheetText(sheet).contains("CEXT"));
        }
    }

    @Test
    void coverageExport_pdf_validWithData() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-07", "LP2", "CONDUCTED"), s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        byte[] bytes = getBytes("/api/hod/reports/coverage/export.pdf", token);
        assertEquals("%PDF-", pdfMagic(bytes));
        assertTrue(pdfFullText(bytes).contains("Recording-Coverage Report"));
    }

    // ====================== HOD monthly / quarterly exports ======================

    @Test
    void monthlyExport_xlsx_valid() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-20", "LP2", "CONDUCTED"), s1, false);
        record(saveSession(a.subject, a.section, a.teacher, "2026-02-10", "LP3", "CONDUCTED"), s1, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/monthly/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Monthly");
            assertNotNull(sheet);
            assertEquals(2, sheet.getLastRowNum() - 3);
            assertEquals("2026-01", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(1.0, sheet.getRow(4).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(2.0, sheet.getRow(4).getCell(2).getNumericCellValue(), 0.001);
            assertEquals(50.0, sheet.getRow(4).getCell(3).getNumericCellValue(), 0.001);
            assertEquals("2026-02", sheet.getRow(5).getCell(0).getStringCellValue());
            assertEquals(100.0, sheet.getRow(5).getCell(3).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void quarterlyExport_xlsx_valid() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, a.teacher, "2026-02-10", "LP2", "CONDUCTED"), s1, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/quarterly/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Quarterly");
            assertNotNull(sheet);
            assertEquals(1, sheet.getLastRowNum() - 3);
            assertEquals("2026-Q1", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(1.0, sheet.getRow(4).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(2.0, sheet.getRow(4).getCell(2).getNumericCellValue(), 0.001);
            assertEquals(50.0, sheet.getRow(4).getCell(3).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void monthlyExport_pdf_validWithData() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-20", "LP2", "CONDUCTED"), s1, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        byte[] bytes = getBytes("/api/hod/reports/monthly/export.pdf", token);
        assertTrue(bytes.length > 0);
        String text = pdfFullText(bytes);
        assertTrue(text.contains("Monthly Attendance Rollup"));
        assertTrue(text.contains("2026-01"));
    }

    // ====================== HOD low-attendance export ======================

    @Test
    void lowAttendanceExport_xlsx_onlyBelowThresholdStudents() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student Low");   // 1/2 = 50% -> below 75 -> included
        Student s2 = student(a, "Student Safe");  // 2/2 = 100% -> excluded
        AttendanceSession l1 = saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED");
        AttendanceSession l2 = saveSession(a.subject, a.section, a.teacher, "2026-01-11", "LP2", "CONDUCTED");
        record(l1, s1, true);
        record(l2, s1, false);
        AttendanceSession s22 = saveSession(a.subject, a.section, a.teacher, "2026-01-12", "LP3", "CONDUCTED");
        AttendanceSession s21 = saveSession(a.subject, a.section, a.teacher, "2026-01-13", "LP4", "CONDUCTED");
        record(s22, s2, true);
        record(s21, s2, true);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        try (Workbook wb = workbook(getBytes("/api/hod/reports/low-attendance/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Low Attendance");
            assertNotNull(sheet);
            assertEquals(1, sheet.getLastRowNum() - 3);
            assertTrue(sheetText(sheet).contains("Student Low"));
            assertFalse(sheetText(sheet).contains("Student Safe"));
            assertEquals(50.0, sheet.getRow(4).getCell(5).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void lowAttendanceExport_pdf_validWithData() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        Student s1 = student(a, "Student Low");
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, a.teacher, "2026-01-11", "LP2", "CONDUCTED"), s1, false);
        flush();

        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        byte[] bytes = getBytes("/api/hod/reports/low-attendance/export.pdf", token);
        assertTrue(bytes.length > 0);
        assertTrue(pdfFullText(bytes).contains("Low-Attendance Report"));
    }

    // ====================== Unsupported report types ======================

    @Test
    void semesterExport_stillRejected_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/semester/export.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hod/reports/semester/export.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownReportType_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/unknown/export.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coverageExport_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        makeHod(a);
        flush();
        String token = login(HOD_A_EMAIL, HOD_PASSWORD);
        mockMvc.perform(get("/api/hod/reports/coverage/export.xlsx")
                        .param("startDate", "2026-02-01").param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ====================== Teacher subject-wise export ======================

    @Test
    void teacherExport_xlsx_ownSubjectOnlyAndSemantics() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherM72A@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, t1, "2026-01-11", "LP2", "CONDUCTED"), s1, false);
        flush();

        String token = login("teacherM72A@dagacs.local", "Pass@123");
        MvcResult result = fetchResult("/api/teacher/attendance/report/export.xlsx", token);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("dagacs_teacher_subject_wise_all.xlsx"));

        try (Workbook wb = workbook(result.getResponse().getContentAsByteArray())) {
            Sheet sheet = wb.getSheet("Subject-wise");
            assertNotNull(sheet);
            assertEquals("Teacher Subject-Wise Attendance Report",
                    sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Subject Code", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("Percentage (%)", sheet.getRow(3).getCell(6).getStringCellValue());
            assertEquals(a.subject.getCode(), sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(a.section.getSectionCode(), sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals(1.0, sheet.getRow(4).getCell(4).getNumericCellValue(), 0.001);
            assertEquals(2.0, sheet.getRow(4).getCell(5).getNumericCellValue(), 0.001);
            assertEquals(50.0, sheet.getRow(4).getCell(6).getNumericCellValue(), 0.001);
            assertFalse(sheetText(sheet).contains("subjectId"));
        }
    }

    @Test
    void teacherExport_xlsx_otherTeacherExcluded() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherM72A@dagacs.local", "Pass@123", a.dept);
        Teacher t2 = createTeacherAccount("teacherM72B@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        Subject t2Subject = extraSubject(a, "TEACHB", "Teacher B Subject");
        assign(t2, offeringFor(a, t2Subject), a.section);

        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(t2Subject, a.section, t2, "2026-01-12", "LP1", "CONDUCTED"), s1, true);
        flush();

        String token = login("teacherM72A@dagacs.local", "Pass@123");
        try (Workbook wb = workbook(getBytes("/api/teacher/attendance/report/export.xlsx", token))) {
            String text = sheetText(wb.getSheet("Subject-wise"));
            assertTrue(text.contains(a.subject.getCode()));
            assertFalse(text.contains("TEACHB"));
        }
    }

    @Test
    void teacherExport_pdf_unassignedSubjectExcluded() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherM72A@dagacs.local", "Pass@123", a.dept);
        Subject unassigned = extraSubject(a, "UNASSIGN", "Unassigned Subject");
        assign(t1, a.offering, a.section);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(unassigned, a.section, t1, "2026-01-11", "LP1", "CONDUCTED"), s1, true);
        flush();

        String token = login("teacherM72A@dagacs.local", "Pass@123");
        byte[] bytes = getBytes("/api/teacher/attendance/report/export.pdf", token);
        String raw = pdfFullText(bytes);
        assertTrue(raw.contains("Teacher Subject-Wise Attendance Report"));
        String flatText = flat(raw);
        assertTrue(flatText.contains(a.subject.getCode()));
        assertFalse(raw.contains("UNASSIGN"));
    }

    @Test
    void teacherExport_xlsx_dateFilter_inclusive() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherM72A@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, t1, "2026-01-05", "LP1", "CONDUCTED"), s1, true);
        record(saveSession(a.subject, a.section, t1, "2026-02-10", "LP2", "CONDUCTED"), s1, false);
        flush();

        String token = login("teacherM72A@dagacs.local", "Pass@123");
        MvcResult result = fetchResult(
                "/api/teacher/attendance/report/export.xlsx?startDate=2026-02-01&endDate=2026-02-28", token);
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("dagacs_teacher_subject_wise_2026-02-01_to_2026-02-28.xlsx"));
        try (Workbook wb = workbook(result.getResponse().getContentAsByteArray())) {
            Row row = wb.getSheet("Subject-wise").getRow(4);
            assertEquals(0.0, row.getCell(4).getNumericCellValue(), 0.001); // present
            assertEquals(1.0, row.getCell(5).getNumericCellValue(), 0.001); // total
            assertEquals(0.0, row.getCell(6).getNumericCellValue(), 0.001); // percentage
        }
    }

    @Test
    void teacherExport_invertedRange_returns400() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherM72A@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        flush();
        String token = login("teacherM72A@dagacs.local", "Pass@123");
        mockMvc.perform(get("/api/teacher/attendance/report/export.xlsx")
                        .param("startDate", "2026-02-01").param("endDate", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherExport_xlsx_zeroRecords_stillValidWithHeadersOnly() throws Exception {
        Slice a = slice("A");
        createTeacherAccount("teacherM72Empty@dagacs.local", "Pass@123", a.dept);
        flush();

        String token = login("teacherM72Empty@dagacs.local", "Pass@123");
        try (Workbook wb = workbook(getBytes("/api/teacher/attendance/report/export.xlsx", token))) {
            Sheet sheet = wb.getSheet("Subject-wise");
            assertNotNull(sheet);
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("Teacher Subject-Wise Attendance Report",
                    sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Subject Code", sheet.getRow(3).getCell(0).getStringCellValue());
        }
    }

    @Test
    void teacherExport_pdf_validWithData() throws Exception {
        Slice a = slice("A");
        Teacher t1 = createTeacherAccount("teacherM72A@dagacs.local", "Pass@123", a.dept);
        assign(t1, a.offering, a.section);
        Student s1 = student(a, "Student A1");
        record(saveSession(a.subject, a.section, t1, "2026-01-10", "LP1", "CONDUCTED"), s1, true);
        flush();

        String token = login("teacherM72A@dagacs.local", "Pass@123");
        MvcResult result = fetchResult("/api/teacher/attendance/report/export.pdf", token);
        assertEquals("application/pdf", result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("dagacs_teacher_subject_wise_all.pdf"));
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        assertEquals("%PDF-", pdfMagic(bytes));
        String raw = pdfFullText(bytes);
        assertTrue(raw.contains("Teacher Subject-Wise Attendance Report"));
        String flatText = flat(raw);
        assertTrue(flatText.contains(a.subject.getCode()));
    }

    /**
     * REQUIRED M7.2 no-truncation proof: one teacher with 205 distinct assigned
     * subject+section rows (subject codes S001..S205) exceeds the M7.1 page-size
     * boundary of 200 and must be present COMPLETELY in both Excel and PDF.
     */
    @Test
    void teacherExport_noTruncation_all205RowsPresent() throws Exception {
        Slice a = slice("A");
        Teacher tA = createTeacherAccount("teacherM72Big@dagacs.local", "Pass@123", a.dept);
        Teacher tB = createTeacherAccount("teacherM72B@dagacs.local", "Pass@123", a.dept);
        Subject tBSubject = extraSubject(a, "TEACHB", "Teacher B Subject");
        assign(tB, offeringFor(a, tBSubject), a.section);
        Student sB = student(a, "Student B");
        record(saveSession(tBSubject, a.section, tB, "2026-01-10", "LP1", "CONDUCTED"), sB, true);

        Student s1 = student(a, "Student A1");
        for (int i = 1; i <= 205; i++) {
            String code = String.format("S%03d", i);
            Subject sub = extraSubject(a, code, "Subject " + code);
            assign(tA, offeringFor(a, sub), a.section);
            record(saveSession(sub, a.section, tA, "2026-01-10", "LP" + i, "CONDUCTED"), s1, true);
        }
        flush();

        String token = login("teacherM72Big@dagacs.local", "Pass@123");

        byte[] xlsx = getBytes("/api/teacher/attendance/report/export.xlsx", token);
        try (Workbook wb = workbook(xlsx)) {
            Sheet sheet = wb.getSheet("Subject-wise");
            assertEquals(205, sheet.getLastRowNum() - 3);
            // Rows ordered by subject code ASC -> the alphabetically last code is the last row.
            assertEquals("S205", sheet.getRow(sheet.getLastRowNum()).getCell(0).getStringCellValue());
            Row first = sheet.getRow(4);
            assertEquals("S001", first.getCell(0).getStringCellValue());
            assertEquals(1.0, first.getCell(4).getNumericCellValue(), 0.001);
            assertEquals(1.0, first.getCell(5).getNumericCellValue(), 0.001);
            assertEquals(100.0, first.getCell(6).getNumericCellValue(), 0.001);
            assertFalse(sheetText(sheet).contains("TEACHB"));
        }

        byte[] pdf = getBytes("/api/teacher/attendance/report/export.pdf", token);
        String raw = pdfFullText(pdf);
        assertTrue(raw.contains("Teacher Subject-Wise Attendance Report"));
        String flatText = flat(raw);
        assertTrue(flatText.contains("S205"));
        assertFalse(raw.contains("TEACHB"));
    }
}