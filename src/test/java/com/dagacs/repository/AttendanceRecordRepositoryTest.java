package com.dagacs.repository;

import com.dagacs.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class AttendanceRecordRepositoryTest {

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
                .email("t" + System.nanoTime() + "@dagacs.local")
                .password("Temp#123")
                .fullName("Test Teacher")
                .phone("1234567890")
                .designation("Professor")
                .status("ACTIVE")
                .avatarUrl("url")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Student createTestStudent() {
        Department dept = departmentRepository.save(Department.builder()
                .name("TestDept-" + System.nanoTime()).code("TD").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("TestProg-" + System.nanoTime()).code("TP").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("TestSession-" + System.nanoTime()).code("TS").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("TB-" + System.nanoTime()).name("Batch1").year(2026)
                .program("TestProg").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("TS-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber("R" + System.nanoTime()).name("TestStudent").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .batch(batch).section(section).program(program)
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

    private AttendanceSession createTestSession(Teacher teacher, Subject subject, Section section, String period) {
        return attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod(period).date("2026-09-04").status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void saveAndRetrieve_validData() {
        Student student = createTestStudent();
        Subject subject = createTestSubject();
        Section section = student.getSection();
        Teacher teacher = createTestTeacher();
        AttendanceSession session = createTestSession(teacher, subject, section, "1st");

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(LocalDateTime.now()).build();
        record = attendanceRecordRepository.save(record);

        Optional<AttendanceRecord> found = attendanceRecordRepository.findById(record.getId());
        assertTrue(found.isPresent());
        assertEquals("PRESENT", found.get().getStatus());
        assertEquals("2026-09-04", found.get().getDate());
    }

    @Test
    void uniqueConstraint_duplicateStudentInSession_throws() {
        Student student = createTestStudent();
        Subject subject = createTestSubject();
        Section section = student.getSection();
        Teacher teacher = createTestTeacher();
        AttendanceSession session = createTestSession(teacher, subject, section, "1st");

        AttendanceRecord record1 = AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(LocalDateTime.now()).build();
        attendanceRecordRepository.save(record1);

        AttendanceRecord record2 = AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("ABSENT").lecturePeriod("1st").date("2026-09-04").isPresent(false)
                .createdAt(LocalDateTime.now()).build();
        assertThrows(DataIntegrityViolationException.class,
                () -> attendanceRecordRepository.saveAndFlush(record2));
    }

    @Test
    void findByStudentId_returnsRecords() {
        Student student = createTestStudent();
        Subject subject = createTestSubject();
        Section section = student.getSection();
        Teacher teacher = createTestTeacher();
        AttendanceSession session = createTestSession(teacher, subject, section, "1st");

        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(LocalDateTime.now()).build());

        assertEquals(1, attendanceRecordRepository.findByStudentId(student.getId()).size());
    }

    @Test
    void findBySessionId_returnsRecordsForSession() {
        Student student = createTestStudent();
        Subject subject = createTestSubject();
        Section section = student.getSection();
        Teacher teacher = createTestTeacher();
        AttendanceSession session = createTestSession(teacher, subject, section, "1st");

        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(LocalDateTime.now()).build());

        assertEquals(1, attendanceRecordRepository.findBySessionId(session.getId()).size());
    }

    @Test
    void existsBySessionAndStudent_returnsTrue() {
        Student student = createTestStudent();
        Subject subject = createTestSubject();
        Section section = student.getSection();
        Teacher teacher = createTestTeacher();
        AttendanceSession session = createTestSession(teacher, subject, section, "1st");

        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .createdAt(LocalDateTime.now()).build());

        assertTrue(attendanceRecordRepository.existsBySessionIdAndStudentId(session.getId(), student.getId()));
    }

    @Test
    void existsBySessionAndStudent_returnsFalseWhenAbsent() {
        assertFalse(attendanceRecordRepository.existsBySessionIdAndStudentId(999L, 999L));
    }
}
