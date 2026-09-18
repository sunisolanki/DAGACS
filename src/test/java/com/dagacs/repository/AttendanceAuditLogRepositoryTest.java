package com.dagacs.repository;

import com.dagacs.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class AttendanceAuditLogRepositoryTest {

    @Autowired
    private AttendanceAuditLogRepository attendanceAuditLogRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

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

    private Teacher createTestTeacher() {
        return teacherRepository.save(Teacher.builder()
                .email("at" + System.nanoTime() + "@dagacs.local")
                .password("Temp#123")
                .fullName("Audit Teacher")
                .phone("1234567890")
                .designation("Professor")
                .status("ACTIVE")
                .avatarUrl("url")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Student createTestStudent() {
        Department dept = departmentRepository.save(Department.builder()
                .name("ADept-" + System.nanoTime()).code("AD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("AProg-" + System.nanoTime()).code("AP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("ASess-" + System.nanoTime()).code("AS")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("ABat-" + System.nanoTime()).name("AB1").year(2026)
                .program("AProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("ASec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("AR" + System.nanoTime()).name("AuditStudent").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("AE" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .academicSession(session).batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private AttendanceRecord createTestAttendanceRecord(Student student) {
        Subject subject = subjectRepository.save(Subject.builder()
                .code("ASubj-" + System.nanoTime()).name("AuditSubject")
                .description("Test").creditHours("3")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Teacher teacher = createTestTeacher();
        Section section = student.getSection();
        AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod("1st").date("2026-09-04").status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(LocalDateTime.now()).build());
    }

    @Test
    void saveAndRetrieve_validData() {
        Student student = createTestStudent();
        AttendanceRecord record = createTestAttendanceRecord(student);

        AttendanceAuditLog auditLog = AttendanceAuditLog.builder()
                .attendance(record).student(student)
                .rollNo(student.getRollNumber()).studentName(student.getName())
                .subject("DBMS").subjectName("Database Management Systems")
                .section("CS-A").sectionName("CS-A")
                .date("2026-09-04")
                .previousStatus("ABSENT").newStatus("PRESENT")
                .updatedBy("teacher@dagacs.local").updatedAt(LocalDateTime.now())
                .reason("Doctor certificate").build();
        auditLog = attendanceAuditLogRepository.save(auditLog);

        Optional<AttendanceAuditLog> found = attendanceAuditLogRepository.findById(auditLog.getId());
        assertTrue(found.isPresent());
        assertEquals(student.getRollNumber(), found.get().getRollNo());
        assertEquals("ABSENT", found.get().getPreviousStatus());
        assertEquals("PRESENT", found.get().getNewStatus());
        assertEquals(record.getId(), found.get().getAttendance().getId());
    }

    @Test
    void reasonNullable_saveWithNullReason_succeeds() {
        Student student = createTestStudent();
        AttendanceRecord record = createTestAttendanceRecord(student);

        AttendanceAuditLog auditLog = AttendanceAuditLog.builder()
                .attendance(record).student(student)
                .rollNo("R123").studentName("Test")
                .subject("Subj").subjectName("Subject Name")
                .section("Sec").sectionName("Section Name")
                .date("2026-09-04")
                .previousStatus("ABSENT").newStatus("PRESENT")
                .updatedBy("teacher").updatedAt(LocalDateTime.now())
                .reason(null).build();
        auditLog = attendanceAuditLogRepository.save(auditLog);

        Optional<AttendanceAuditLog> found = attendanceAuditLogRepository.findById(auditLog.getId());
        assertTrue(found.isPresent());
        assertNull(found.get().getReason());
    }

    @Test
    void snapshotFields_persistCorrectly() {
        Student student = createTestStudent();
        AttendanceRecord record = createTestAttendanceRecord(student);

        AttendanceAuditLog auditLog = AttendanceAuditLog.builder()
                .attendance(record).student(student)
                .rollNo("SNAPSHOT_ROLL").studentName("Snapshot Name")
                .subject("Subj").subjectName("Snapshot Subject")
                .section("Sec").sectionName("Snapshot Section")
                .date("2026-09-04")
                .previousStatus("ABSENT").newStatus("PRESENT")
                .updatedBy("teacher").updatedAt(LocalDateTime.now())
                .build();
        auditLog = attendanceAuditLogRepository.save(auditLog);

        AttendanceAuditLog found = attendanceAuditLogRepository.findById(auditLog.getId()).orElseThrow();
        assertEquals("SNAPSHOT_ROLL", found.getRollNo());
        assertEquals("Snapshot Name", found.getStudentName());
        assertEquals("Snapshot Subject", found.getSubjectName());
        assertEquals("Snapshot Section", found.getSectionName());
    }

    @Test
    void findByStudentId_returnsLogs() {
        Student student = createTestStudent();
        AttendanceRecord record = createTestAttendanceRecord(student);

        attendanceAuditLogRepository.save(AttendanceAuditLog.builder()
                .attendance(record).student(student)
                .rollNo("R1").studentName("S1")
                .subject("Subj").subjectName("SubjName")
                .section("Sec").sectionName("SecName")
                .date("2026-09-04")
                .previousStatus("ABSENT").newStatus("PRESENT")
                .updatedBy("teacher").updatedAt(LocalDateTime.now())
                .build());

        assertEquals(1, attendanceAuditLogRepository.findByStudentId(student.getId()).size());
    }

    @Test
    void findByAttendanceId_returnsLogs() {
        Student student = createTestStudent();
        AttendanceRecord record = createTestAttendanceRecord(student);

        attendanceAuditLogRepository.save(AttendanceAuditLog.builder()
                .attendance(record).student(student)
                .rollNo("R1").studentName("S1")
                .subject("Subj").subjectName("SubjName")
                .section("Sec").sectionName("SecName")
                .date("2026-09-04")
                .previousStatus("ABSENT").newStatus("PRESENT")
                .updatedBy("teacher").updatedAt(LocalDateTime.now())
                .build());

        assertEquals(1, attendanceAuditLogRepository.findByAttendanceId(record.getId()).size());
    }
}
