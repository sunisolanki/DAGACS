package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * True end-to-end M4.3 verification for the optional inclusive date-range filtering
 * ({@code startDate} / {@code endDate}) on the student attendance calculation API.
 * <p>
 * Real JWT login → JWT student identity → {@code AttendanceRecord} aggregation scoped to the
 * authenticated student and bounded by the requested date range. The range is inclusive
 * ({@code startDate <= date <= endDate}), start-only and end-only requests work, inverted
 * ranges return 400, malformed dates return 400, and a client-supplied {@code studentId} can
 * never bypass identity or include another student's records.
 * </p>
 * <p>
 * All test data is created and removed within the test transaction (rolled back).
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AttendanceCalculationDateRangeIntegrationTest {

    private static final String STUDENT_A_EMAIL = "student@dagacs.local";
    private static final String STUDENT_A_PASSWORD = "Student@123";
    private static final String STUDENT_B_EMAIL = "calcrangeB@dagacs.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    private Teacher createTestTeacher() {
        return teacherRepository.save(Teacher.builder()
                .email("t" + System.nanoTime() + "@dagacs.local")
                .password("Temp#123")
                .fullName("Test Teacher")
                .phone("1234567890")
                .designation("Professor")
                .status("ACTIVE")
                .avatarUrl("url")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Subject createTestSubject() {
        return subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name("TestSubject")
                .description("Test").creditHours("3").department("CSE")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Student createTestStudent(String email, String suffix) {
        Department dept = departmentRepository.save(Department.builder()
                .name("CalRDept-" + System.nanoTime()).code("CRD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("CalRProg-" + System.nanoTime()).code("CRP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("CalRSess-" + System.nanoTime()).code("CRS").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("CalRBat-" + System.nanoTime()).name("CRB1").year(2026)
                .program("CalRProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("CalRSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("CR" + suffix).name("CalcRange Student " + suffix).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(email)
                .batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createTestSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("CalRSecDept-" + System.nanoTime()).code("CRSD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("CalRSecProg-" + System.nanoTime()).code("CRSP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("CalRSecSess-" + System.nanoTime()).code("CRSS").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("CalRSecBat-" + System.nanoTime()).name("CRSB1").year(2026)
                .program("CalRSecProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("CalRSecSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private void saveRecord(Student student, Subject subject, Section section, Teacher teacher,
                            boolean isPresent, String date) {
        AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod("P-" + System.nanoTime()).date(date).status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher).status(isPresent ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod()).date(date).isPresent(isPresent)
                .createdAt(LocalDateTime.now()).build());
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + STUDENT_A_EMAIL + "\",\"password\":\"" + STUDENT_A_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void calculation_withInclusiveDateRange_appliesBoundsToAuthenticatedStudentRecords() throws Exception {
        Subject subjectX = createTestSubject();
        Subject subjectY = createTestSubject();
        Section section = createTestSection();
        Teacher teacher = createTestTeacher();

        Student studentA = createTestStudent(STUDENT_A_EMAIL, "A");
        Student studentB = createTestStudent(STUDENT_B_EMAIL, "B");

        // Student A: subject X on 09-01 PRESENT, 09-04 PRESENT, 09-07 ABSENT; subject Y on 09-10 PRESENT.
        saveRecord(studentA, subjectX, section, teacher, true, "2026-09-01");
        saveRecord(studentA, subjectX, section, teacher, true, "2026-09-04");
        saveRecord(studentA, subjectX, section, teacher, false, "2026-09-07");
        saveRecord(studentA, subjectY, section, teacher, true, "2026-09-10");
        // Student B: a PRESENT record inside A's date window, must never be counted for A.
        saveRecord(studentB, subjectX, section, teacher, true, "2026-09-05");
        attendanceRecordRepository.flush();

        String token = loginToken();

        // No date params → unchanged unfiltered behavior: A has 3 present of 4 recorded.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(3))
                .andExpect(jsonPath("$.totalRecordedCount").value(4));

        // Inclusive range 09-01..09-04 → 2/2 (boundary records included), 100%.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-04")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(2))
                .andExpect(jsonPath("$.percentage").value(100.0));

        // Range 09-05..09-07 → only the ABSENT 09-07 record → 0%, not zero-record null.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-05")
                        .param("endDate", "2026-09-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(0))
                .andExpect(jsonPath("$.totalRecordedCount").value(1))
                .andExpect(jsonPath("$.percentage").value(0.0));

        // Disjoint range with no records → 0/0 and percentage null.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-08")
                        .param("endDate", "2026-09-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(0))
                .andExpect(jsonPath("$.totalRecordedCount").value(0))
                .andExpect(jsonPath("$.percentage").doesNotExist());

        // Range spanning Student B's PRESENT record: B's 09-05 record is still excluded → 2/3.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(3));

        // A client-supplied studentId cannot bypass identity even with a date range → still A's 2/3.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-07")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(3));

        // Subject-scoped range: subject X 09-01..09-07 → 2/3; subject Y same window → 0/0 null.
        mockMvc.perform(get("/api/student/attendance/calculation/subject/" + subjectX.getId())
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(3));

        mockMvc.perform(get("/api/student/attendance/calculation/subject/" + subjectY.getId())
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(0))
                .andExpect(jsonPath("$.totalRecordedCount").value(0))
                .andExpect(jsonPath("$.percentage").doesNotExist());

        // Subject Y unfiltered → its only record (09-10 PRESENT) → 100%.
        mockMvc.perform(get("/api/student/attendance/calculation/subject/" + subjectY.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.totalRecordedCount").value(1))
                .andExpect(jsonPath("$.percentage").value(100.0));

        // Start-only: 09-07 onward → records 09-07 (ABSENT) and 09-10 (PRESENT) → 1/2.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.totalRecordedCount").value(2))
                .andExpect(jsonPath("$.percentage").value(50.0));

        // End-only: through 09-04 → records 09-01 and 09-04 (both PRESENT) → 2/2.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("endDate", "2026-09-04")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(2))
                .andExpect(jsonPath("$.percentage").value(100.0));
    }

    @Test
    void calculation_invertedDateRange_returns400() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-09-10")
                        .param("endDate", "2026-09-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/student/attendance/calculation/subject/1")
                        .param("startDate", "2026-09-10")
                        .param("endDate", "2026-09-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculation_malformedDate_returns400() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "not-a-date")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("endDate", "01/09/2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}