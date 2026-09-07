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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * True end-to-end M4.1 ownership / IDOR verification for the attendance calculation API.
 * <p>
 * Real JWT login → JWT student identity → {@code StudentRepository.findByEmail} on the actual
 * {@code students.email} DB profile → {@code AttendanceRecord} aggregation query scoped to the
 * authenticated student. Only Student A's records are included; a client-supplied
 * {@code studentId} query parameter cannot bypass identity (the API never reads it).
 * </p>
 * <p>
 * This mirrors the existing M3.4 {@code StudentAttendanceOwnershipIntegrationTest} pattern.
 * All test data is created and removed within the test transaction (rolled back).
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AttendanceCalculationOwnershipIntegrationTest {

    private static final String STUDENT_A_EMAIL = "student@dagacs.local";
    private static final String STUDENT_A_PASSWORD = "Student@123";
    private static final String STUDENT_B_EMAIL = "calcupB@dagacs.local";

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
                .name("CalDept-" + System.nanoTime()).code("CD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("CalProg-" + System.nanoTime()).code("CP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("CalSess-" + System.nanoTime()).code("CS").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("CalBat-" + System.nanoTime()).name("CalB1").year(2026)
                .program("CalProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("CalSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("CAL" + suffix).name("Calc Student " + suffix).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(email)
                .batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createTestSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("CalSecDept-" + System.nanoTime()).code("CSD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("CalSecProg-" + System.nanoTime()).code("CSP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("CalSecSess-" + System.nanoTime()).code("CSS").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("CalSecBat-" + System.nanoTime()).name("CSB1").year(2026)
                .program("CalSecProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("CalSecSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private AttendanceSession createTestSession(Teacher teacher, Subject subject, Section section, String period) {
        return attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod(period).date("2026-09-04").status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private AttendanceRecord buildRecord(AttendanceSession session, Student student, String status,
                                          boolean present) {
        return AttendanceRecord.builder()
                .session(session).student(student)
                .subject(session.getSubjectEntity()).section(session.getSectionEntity())
                .markedBy(session.getTeacherEntity())
                .status(status).lecturePeriod(session.getLecturePeriod()).date("2026-09-04").isPresent(present)
                .createdAt(LocalDateTime.now()).build();
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
    void calculation_includesOnlyAuthenticatedStudentsRecords() throws Exception {
        Subject subject = createTestSubject();
        Section section = createTestSection();
        Teacher teacher = createTestTeacher();

        Student studentA = createTestStudent(STUDENT_A_EMAIL, "A");
        Student studentB = createTestStudent(STUDENT_B_EMAIL, "B");

        // Student A: 2 PRESENT + 1 ABSENT across 3 distinct sessions.
        AttendanceSession sessionA1 = createTestSession(teacher, subject, section, "1st");
        AttendanceSession sessionA2 = createTestSession(teacher, subject, section, "2nd");
        AttendanceSession sessionA3 = createTestSession(teacher, subject, section, "3rd");
        attendanceRecordRepository.save(buildRecord(sessionA1, studentA, "PRESENT", true));
        attendanceRecordRepository.save(buildRecord(sessionA2, studentA, "PRESENT", true));
        attendanceRecordRepository.save(buildRecord(sessionA3, studentA, "ABSENT", false));

        // Student B: 1 PRESENT in a 4th distinct session.
        AttendanceSession sessionB1 = createTestSession(teacher, subject, section, "4th");
        attendanceRecordRepository.save(buildRecord(sessionB1, studentB, "PRESENT", true));
        attendanceRecordRepository.flush();

        // Sanity: both students have records in this transaction.
        assertEquals(3, attendanceRecordRepository.findByStudentId(studentA.getId()).size());
        assertEquals(1, attendanceRecordRepository.findByStudentId(studentB.getId()).size());

        String token = loginToken();

        // Authenticated Student A's overall calculation counts only A's 3 records (2 present).
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(3));

        // A client-supplied studentId cannot bypass identity or include Student B's records.
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(3));

        // Subject-scoped calculation stays scoped to A's own records for this subject.
        mockMvc.perform(get("/api/student/attendance/calculation/subject/" + subject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.totalRecordedCount").value(3));
    }
}