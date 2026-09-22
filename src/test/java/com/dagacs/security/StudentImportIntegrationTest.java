package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Semester;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.10 true end-to-end bulk import coverage through the real ADMIN JWT login
 * and the multipart controller path ({@code POST /api/admin/students/import}).
 * <p>
 * Every case exercises the full stack: multipart parsing, XLSX/CSV parsing,
 * Bean Validation on the frozen DTO, the shared M5.2 validation path (including
 * the M9.9 academic-consistency guard), all-or-nothing rollback, HTTP
 * semantics (200/400/401/403/409) and the SOW summary shape.
 * </p>
 * <p>
 * All reference data and students are created inside the test transaction and
 * rolled back, so nothing production/demo is written.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentImportIntegrationTest {

    private static final String HEADER = "Name,Roll Number,Academic Session,Program,Email,Gender,Father Name,"
            + "Mother Name,Photo URL,Enrollment Number,Age,Admission Date,Status,Batch,Section";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private SemesterRepository semesterRepository;

    @Autowired
    private StudentManagementRepository studentManagementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String login(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String adminToken() throws Exception {
        return login("admin@dagacs.local", "Admin@123");
    }

    private String teacherToken() throws Exception {
        return login("teacher@dagacs.local", "Teacher@123");
    }

    private Department createDepartment() {
        String stamp = String.valueOf(System.nanoTime());
        return departmentRepository.save(Department.builder()
                .name("Import-Dept-" + stamp).code("D" + stamp.substring(stamp.length() - 4))
                .description("Test").createdBy("test")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Program createProgram(Department dept, String name, String code) {
        return programRepository.save(Program.builder()
                .name(name).code(code).duration("4yr")
                .description("Test").department(dept).build());
    }

    private AcademicSession createSession(Program program, String prefix) {
        return academicSessionRepository.save(AcademicSession.builder()
                .name(prefix).code(prefix)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Batch createBatch(AcademicSession session, String name, String code) {
        return batchRepository.save(Batch.builder()
                .batchCode(code + "-" + System.nanoTime()).name(name).year(2026)
                .program(session.getProgram().getName()).maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createSection(Batch batch, String name, String code) {
        return sectionRepository.save(Section.builder()
                .sectionCode(code + "-" + System.nanoTime()).name(name).maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

private long createStudentOnAnotherProgram(String token, Section section,
                                                String rollNumber, String email) throws Exception {
        String body = "{"
                + "\"rollNumber\":\"" + rollNumber + "\","
                + "\"email\":\"" + email + "\","
                + "\"name\":\"Existing Student\","
                + "\"gender\":\"M\","
                + "\"photoUrl\":\"\","
                + "\"enrollmentNumber\":\"" + rollNumber + "\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"status\":\"ACTIVE\","
                + "\"academicSessionId\":" + section.getBatch().getAcademicSession().getId() + ","
                + "\"programId\":" + section.getBatch().getAcademicSession().getProgram().getId() + ","
                + "\"batchId\":" + section.getBatch().getId() + ","
                + "\"sectionId\":" + section.getId()
                + "}";
        MvcResult result = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult upload(String token, String filename, String contentType, byte[] bytes)
            throws Exception {
        return mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    @Test
    void validXlsxImport_createsStudents_returns200Summary() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] xlsx = xlsx(
                new String[]{"Aarav Sharma","IMPR1","SessY","Computer Science Y","impr1@dagacs.local","M","","","","IMPR1","20","2026-01-01","ACTIVE","B-Y","A"},
                new String[]{"Priya Patel","IMPR2","SessY","Computer Science Y","impr2@dagacs.local","F","","","","IMPR2","21","2026-01-01","INACTIVE","B-Y","A"});

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                xlsx))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.rejectedRows").value(0));

        assertTrue(studentManagementRepository.existsByRollNumber("IMPR1"));
        assertTrue(studentManagementRepository.existsByRollNumber("IMPR2"));
    }

    @Test
    void bulkImport_artifactPasswordsMatchPersistedAccounts() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] xlsx = xlsx(
                new String[]{"Mia Kapoor","MATCH1","SessY","Computer Science Y","match1@dagacs.local","F","","","","MATCH1","20","2026-01-01","ACTIVE","B-Y","A"},
                new String[]{"Arjun Rao","MATCH2","SessY","Computer Science Y","match2@dagacs.local","M","","","","MATCH2","21","2026-01-01","ACTIVE","B-Y","A"});

        MvcResult importResult = mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                xlsx))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.credentialDownloadId").isNotEmpty())
                .andReturn();

        String downloadId = objectMapper
                .readTree(importResult.getResponse().getContentAsString())
                .get("credentialDownloadId").asText();

        // one STUDENT account per imported row, each forcing a password change
        assertTrue(userRepository.existsByEmail("match1@dagacs.local"));
        assertTrue(userRepository.existsByEmail("match2@dagacs.local"));
        User user1 = userRepository.findByEmail("match1@dagacs.local").orElseThrow();
        User user2 = userRepository.findByEmail("match2@dagacs.local").orElseThrow();
        assertTrue(user1.isMustChangePassword());
        assertTrue(user2.isMustChangePassword());

        // the downloadable XLSX must contain exactly the passwords that were
        // persisted as BCrypt hashes
        MvcResult download = mockMvc.perform(get("/api/admin/students/import/credentials/" + downloadId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        byte[] artifact = download.getResponse().getContentAsByteArray();

        java.util.Map<String, String> passwords = new java.util.LinkedHashMap<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(artifact))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                passwords.put(row.getCell(0).getStringCellValue(),
                        row.getCell(2).getStringCellValue());
            }
        }

        assertEquals(2, passwords.size());
        assertTrue(passwordEncoder.matches(passwords.get("MATCH1"), user1.getPassword()));
        assertTrue(passwordEncoder.matches(passwords.get("MATCH2"), user2.getPassword()));

        // and each artifact password actually authenticates
        login("match1@dagacs.local", passwords.get("MATCH1"));
        login("match2@dagacs.local", passwords.get("MATCH2"));
    }

    @Test
    void validCsvImport_createsStudents_returns200Summary() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] csv = csvBody(
                "Aarav Sharma,IMPC1,SessY,Computer Science Y,impc1@dagacs.local,M,,,,IMPC1,20,2026-01-01,ACTIVE,B-Y,A",
                "Priya Patel,IMPC2,SessY,Computer Science Y,impc2@dagacs.local,F,,,,IMPC2,21,2026-01-01,ACTIVE,B-Y,A");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv",
                                "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.rejectedRows").value(0));

        assertTrue(studentManagementRepository.existsByRollNumber("IMPC1"));
        assertTrue(studentManagementRepository.existsByRollNumber("IMPC2"));
    }

    @Test
    void duplicateRollNumberInsideFile_returns409_nothingPersisted() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] csv = csvBody(
                "First,DUPR1,SessY,Computer Science Y,dupr1a@dagacs.local,M,,,,DUPR1,20,2026-01-01,ACTIVE,B-Y,A",
                "Second,DUPR1,SessY,Computer Science Y,dupr1b@dagacs.local,M,,,,DUPR1,20,2026-01-01,ACTIVE,B-Y,A");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("rollNumber"))
                .andExpect(jsonPath("$.errors[0].status").value(409));

        assertFalse(studentManagementRepository.existsByRollNumber("DUPR1"));
    }

    @Test
    void emailConflictWithExistingStudent_returns409_nothingPersisted() throws Exception {
        String admin = adminToken();
        Section section = oneSectionFixture();
        createStudentOnAnotherProgram(admin, section, "EXIST1", "shared@dagacs.local");

        byte[] csv = csvBody(
                "New Row,NEWROW,SessY,Computer Science Y,shared@dagacs.local,M,,,,NEWROW,20,2026-01-01,ACTIVE,B-Y,A");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        assertFalse(studentManagementRepository.existsByRollNumber("NEWROW"));
        assertTrue(studentManagementRepository.existsByRollNumber("EXIST1"));
    }

    @Test
    void academicMismatch_returns400_nothingPersisted() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program cs = createProgram(dept, "Computer Science X", "CSX");
        Program ee = createProgram(dept, "Electrical X", "EEX");
        AcademicSession sessCS = createSession(cs, "SessCS");
        AcademicSession sessEE = createSession(ee, "SessEE");
        Batch batchCS = createBatch(sessCS, "B-CS", "BCS");
        Batch batchEE = createBatch(sessEE, "B-EE", "BEE");
        Section sectionCS = createSection(batchCS, "A", "A");

        byte[] csv = csvBody(
                "MISMATCH,MISMATCH,SessCS,Electrical X,mismatch@dagacs.local,M,,,,MISMATCH,20,2026-01-01,ACTIVE,B-CS,A");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Academic session program does not match student program"));

        assertFalse(studentManagementRepository.existsByRollNumber("MISMATCH"));
    }

    @Test
    void missingRequiredField_returns400Summary_nothingPersisted() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] csv = csvBody(
                ",MISSING1,SessY,Computer Science Y,,M,,,,MISSING1,20,2026-01-01,ACTIVE,B-Y,A",
                "Valid,MISSING2,SessY,Computer Science Y,missing2@dagacs.local,M,,,,MISSING2,20,2026-01-01,ACTIVE,B-Y,A");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        assertFalse(studentManagementRepository.existsByRollNumber("MISSING1"));
        assertFalse(studentManagementRepository.existsByRollNumber("MISSING2"));
    }

    @Test
    void previewImport_validXlsx_returns200SummaryWithoutPersisting() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] xlsx = xlsx(
                new String[]{"Aarav Sharma","IMPR1","SessY","Computer Science Y","impr1@dagacs.local","M","","","","IMPR1","20","2026-01-01","ACTIVE","B-Y","A"},
                new String[]{"Priya Patel","IMPR2","SessY","Computer Science Y","impr2@dagacs.local","F","","","","IMPR2","21","2026-01-01","INACTIVE","B-Y","A"});

        mockMvc.perform(multipart("/api/admin/students/import/preview")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                xlsx))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.rejectedRows").value(0));

        assertFalse(studentManagementRepository.existsByRollNumber("IMPR1"));
        assertFalse(studentManagementRepository.existsByRollNumber("IMPR2"));
    }

    @Test
    void headerMismatch_returns400() throws Exception {
        String admin = adminToken();
        byte[] csv = ("Roll Number,Name,Program ID,Batch ID,Section ID\n"
                + "HDR1,Rahul,1,1,1\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void headerAliases_programId_batchId_sectionId_accepted() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] csv = ("Name,Roll Number,Academic Session,Program ID,Batch ID,Section ID\n"
                + "Rahul,2201CE001,SessY,Computer Science Y,B-Y,A\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.importedRows").value(1));
    }

    @Test
    void semesterColumn_accepted() throws Exception {
        String admin = adminToken();
        oneSectionFixture();

        byte[] csv = ("Name,Roll Number,Academic Session,Program,Batch,Section,Semester\n"
                + "Rahul,2201CE001,SessY,Computer Science Y,B-Y,A,Sem 1\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.importedRows").value(1));
    }

    @Test
    void malformedFile_returns400() throws Exception {
        String admin = adminToken();
        byte[] garbage = "this-is-not-a-workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/octet-stream", garbage))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFile_returns400() throws Exception {
        String admin = adminToken();
        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", new byte[0]))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void headerOnlyFile_returns400() throws Exception {
        String admin = adminToken();
        byte[] csv = (HEADER + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file has no student rows (header-only or all rows blank)"));
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        byte[] csv = csvBody("A1,,Alice,M,,,,E,20,,ACTIVE,1,1,1");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdmin_returns403() throws Exception {
        String teacher = teacherToken();
        byte[] csv = csvBody("A1,,Alice,M,,,,E,20,,ACTIVE,1,1,1");

        mockMvc.perform(multipart("/api/admin/students/import")
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", csv))
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
    }

    private Section oneSectionFixture() {
        Department dept = createDepartment();
        Program program = createProgram(dept, "Computer Science Y", "CSY");
        AcademicSession session = createSession(program, "SessY");
        Batch batch = createBatch(session, "B-Y", "BY");
        Semester semester = createSemester(session, "Sem 1", "SEM1");
        return createSection(batch, "A", "A");
    }

    private Semester createSemester(AcademicSession session, String name, String code) {
        return semesterRepository.save(Semester.builder()
                .name(name).code(code).year(1)
                .academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private static byte[] csvBody(String... dataRows) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (String row : dataRows) {
            sb.append(row).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] xlsx(String[]... dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Students");
            String[] header = HEADER.split(",");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < header.length; c++) {
                headerRow.createCell(c).setCellValue(header[c]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    row.createCell(c).setCellValue(dataRows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected condition to be false");
        }
    }
}