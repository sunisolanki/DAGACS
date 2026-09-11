package com.dagacs.security;

import com.dagacs.dto.AttendanceAuditLogDTO;
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
import com.dagacs.repository.AttendanceAuditLogRepository;
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
import com.dagacs.service.AttendanceAuditLogService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3.5 audit-on-update consistency verification through the LIVE teacher API.
 * <p>
 * A legitimate attendance status change via {@code PUT /api/teacher/attendance/{id}} (real JWT)
 * writes an {@code attendance_audit_logs} row. This test asserts that {@code previousStatus} is
 * taken from the persisted DB state, {@code newStatus} matches the persisted status, and
 * {@code updatedBy} is derived from the authenticated teacher's DB profile — never from any
 * client-supplied identity/snapshot field.
 * </p>
 * <p>
 * All fixtures are created and removed within the test transaction (rolled back); no
 * production/demo attendance, student, or teacher data is seeded.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AttendanceAuditConsistencyIntegrationTest {

    private static final String TEACHER_EMAIL = "teacher@dagacs.local";
    private static final String TEACHER_PASSWORD = "Teacher@123";
    private static final String TEACHER_FULL_NAME = "Test Teacher";

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
    private SemesterRepository semesterRepository;

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    private AcademicSession sectionSession;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceAuditLogRepository auditLogRepository;

    @Autowired
    private AttendanceAuditLogService auditLogService;

    private Teacher createTeacher() {
        return teacherRepository.save(Teacher.builder()
                .email(TEACHER_EMAIL)
                .password("Temp#123")
                .fullName(TEACHER_FULL_NAME)
                .phone("1234567890")
                .designation("Professor")
                .status("ACTIVE")
                .avatarUrl("url")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Subject createSubject() {
        return subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name("TestSubject")
                .description("Test").creditHours("3")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Prog-" + System.nanoTime()).code("P").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        sectionSession = session;
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Prog").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private SubjectOffering createOffering(Subject subject) {
        Semester semester = semesterRepository.save(Semester.builder()
                .name("Sem-" + System.nanoTime()).code("SEM").year(2026)
                .academicSession(sectionSession)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Student createStudent() {
        Department dept = departmentRepository.save(Department.builder()
                .name("StDept-" + System.nanoTime()).code("SD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("StProg-" + System.nanoTime()).code("SP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("StSess-" + System.nanoTime()).code("SS")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("StBat-" + System.nanoTime()).name("StB1").year(2026)
                .program("StProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("STUA" + System.nanoTime()).name("Student A").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .batch(batch).section(sectionRepository.save(Section.builder()
                        .sectionCode("StSec-" + System.nanoTime()).name("A").maxCapacity(30)
                        .batch(batch).status("ACTIVE")
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()))
                .program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEACHER_EMAIL + "\",\"password\":\"" + TEACHER_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void updateAttendance_writesAuditFromPersistedState_andAuthenticatedIdentity() throws Exception {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        Section section = createSection();
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(createOffering(subject)).section(section)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Student student = createStudent();

        AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod("1st").date("2026-09-04").status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        AttendanceRecord record = attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student)
                .subject(subject).section(section).markedBy(teacher)
                .status("ABSENT").lecturePeriod("1st").date("2026-09-04").isPresent(false)
                .createdAt(LocalDateTime.now()).build());
        attendanceRecordRepository.flush();

        String token = loginToken();

        mockMvc.perform(put("/api/teacher/attendance/" + record.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PRESENT\",\"reason\":\"doctor certificate\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.isPresent").value(true));

        AttendanceRecord persisted = attendanceRecordRepository.findById(record.getId()).orElseThrow();
        assertEquals("PRESENT", persisted.getStatus());
        assertEquals(true, persisted.getIsPresent());

        List<AttendanceAuditLogDTO> audits = auditLogService.getAuditLogsByAttendanceId(record.getId());
        assertEquals(1, audits.size());
        AttendanceAuditLogDTO audit = audits.get(0);

        // previousStatus comes from the persisted DB state (ABSENT before the update).
        assertEquals("ABSENT", audit.getPreviousStatus());
        // newStatus matches the actual persisted attendance status.
        assertEquals("PRESENT", audit.getNewStatus());
        // updatedBy comes from the authenticated teacher's DB profile, not a client-supplied field.
        assertEquals(TEACHER_FULL_NAME, audit.getUpdatedBy());
        // Snapshot fields are derived from DB state, not the request body.
        assertEquals(student.getRollNumber(), audit.getRollNo());
        assertEquals("2026-09-04", audit.getDate());

        // Append-only: exactly one insert-only audit row for this attendance record.
        assertEquals(1, auditLogRepository.findByAttendanceId(record.getId()).size());
    }
}