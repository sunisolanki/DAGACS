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
 * True end-to-end M3.4 ownership / IDOR verification:
 * <p>
 * Real JWT login → JWT student identity → {@code StudentRepository.findByEmail} on the actual
 * {@code students.email} DB profile → {@code AttendanceRecord} ownership query → only that
 * student's records are returned. Also proves a client-supplied {@code studentId} query
 * parameter cannot bypass identity (the API never reads it).
 * </p>
 * <p>
 * All test data is created and removed within the test transaction (rolled back), so no
 * production/demo Student or attendance data is seeded.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentAttendanceOwnershipIntegrationTest {

    private static final String STUDENT_A_EMAIL = "student@dagacs.local";
    private static final String STUDENT_B_EMAIL = "studentB@dagacs.local";
    private static final String STUDENT_PASSWORD = "Student@123";

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

    private Section createTestSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Prog-" + System.nanoTime()).code("P").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Prog").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Student createTestStudent(String email, String suffix) {
        Department dept = departmentRepository.save(Department.builder()
                .name("StDept-" + System.nanoTime()).code("SD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("StProg-" + System.nanoTime()).code("SP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("StSess-" + System.nanoTime()).code("SS").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("StBat-" + System.nanoTime()).name("StB1").year(2026)
                .program("StProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("StSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("STU" + suffix).name("Student " + suffix).gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email(email)
                .batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private AttendanceSession createTestSession(Teacher teacher, Subject subject, Section section) {
        return attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod("1st").date("2026-09-04").status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private AttendanceRecord buildRecord(AttendanceSession session, Student student, String status,
                                          boolean present) {
        return AttendanceRecord.builder()
                .session(session).student(student)
                .subject(session.getSubjectEntity()).section(session.getSectionEntity())
                .markedBy(session.getTeacherEntity())
                .status(status).lecturePeriod("1st").date("2026-09-04").isPresent(present)
                .createdAt(LocalDateTime.now()).build();
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + STUDENT_A_EMAIL + "\",\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void studentCanOnlySeeOwnAttendance_and_clientStudentIdCannotBypassIdentity() throws Exception {
        Subject subject = createTestSubject();
        Section section = createTestSection();
        Teacher teacher = createTestTeacher();
        AttendanceSession session = createTestSession(teacher, subject, section);

        Student studentA = createTestStudent(STUDENT_A_EMAIL, "A");
        Student studentB = createTestStudent(STUDENT_B_EMAIL, "B");

        AttendanceRecord recordA = attendanceRecordRepository.save(
                buildRecord(session, studentA, "PRESENT", true));
        AttendanceRecord recordB = attendanceRecordRepository.save(
                buildRecord(session, studentB, "ABSENT", false));
        attendanceRecordRepository.flush();

        // Sanity: both records exist in this transaction.
        assertEquals(1, attendanceRecordRepository.findByStudentId(studentA.getId()).size());
        assertEquals(1, attendanceRecordRepository.findByStudentId(studentB.getId()).size());

        String token = loginToken();

        // Authenticated Student A sees only their own record.
        mockMvc.perform(get("/api/student/attendance/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(recordA.getId()))
                .andExpect(jsonPath("$[0].studentId").value(studentA.getId()))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));

        // A client-supplied studentId cannot change identity or expose Student B's records.
        mockMvc.perform(get("/api/student/attendance/my")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(recordA.getId()))
                .andExpect(jsonPath("$[0].studentId").value(studentA.getId()))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
    }
}
