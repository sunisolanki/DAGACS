package com.dagacs.repository;

import com.dagacs.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@Rollback
class AttendanceRecordAggregationRepositoryTest {

    @Autowired
    private AttendanceRecordAggregationRepository aggregationRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

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

    private Student createTestStudent(Section section) {
        return studentRepository.save(Student.builder()
                .rollNumber("R" + System.nanoTime()).name("TestStudent").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .email("student-" + System.nanoTime() + "@dagacs.local")
                .batch(section.getBatch()).section(section).program(section.getBatch().getAcademicSession().getProgram())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Subject createTestSubject() {
        return subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name("TestSubject")
                .description("Test").creditHours("3").department("CSE")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private AttendanceSession createTestSession(Teacher teacher, Subject subject, Section section,
                                                String period, String status) {
        return createTestSession(teacher, subject, section, period, status, "2026-09-04");
    }

    private AttendanceSession createTestSession(Teacher teacher, Subject subject, Section section,
                                                String period, String status, String date) {
        return attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod(period).date(date).status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private void saveRecord(Student student, Subject subject, Section section, Teacher teacher,
                            AttendanceSession session, boolean isPresent) {
        saveRecord(student, subject, section, teacher, session, isPresent, "2026-09-04");
    }

    private void saveRecord(Student student, Subject subject, Section section, Teacher teacher,
                            AttendanceSession session, boolean isPresent, String date) {
        attendanceRecordRepository.save(AttendanceRecord.builder()
                .session(session).student(student).subject(subject).section(section)
                .markedBy(teacher).status(isPresent ? "PRESENT" : "ABSENT")
                .lecturePeriod(session.getLecturePeriod()).date(date)
                .isPresent(isPresent).createdAt(LocalDateTime.now()).build());
    }

    private void saveRecord(Student student, Subject subject, Section section, Teacher teacher,
                            boolean isPresent, String date) {
        AttendanceSession session = createTestSession(teacher, subject, section,
                "P-" + System.nanoTime(), "CONDUCTED", date);
        saveRecord(student, subject, section, teacher, session, isPresent, date);
    }

    @Test
    void countByStudentId_distinctSessionsAggregateCorrectly() {
        Section section = createTestSection();
        Student student = createTestStudent(section);
        Subject subject = createTestSubject();
        Teacher teacher = createTestTeacher();

        // 8 distinct CONDUCTED sessions: 5 PRESENT + 3 ABSENT
        for (int i = 0; i < 8; i++) {
            AttendanceSession session = createTestSession(teacher, subject, section,
                    "P" + i, "CONDUCTED");
            saveRecord(student, subject, section, teacher, session, i < 5);
        }

        long total = aggregationRepository.countByStudentId(student.getId());
        long present = aggregationRepository.countByStudentIdAndIsPresentTrue(student.getId());
        long subjectTotal = aggregationRepository.countByStudentIdAndSubjectId(student.getId(), subject.getId());
        long subjectPresent = aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(student.getId(), subject.getId());

        assertEquals(8L, total);
        assertEquals(5L, present);
        assertEquals(8L, subjectTotal);
        assertEquals(5L, subjectPresent);
    }

    @Test
    void countByStudentId_filteredFromAnotherStudentsRecords() {
        Section section = createTestSection();
        Student studentA = createTestStudent(section);
        Student studentB = createTestStudent(section);
        Subject subject = createTestSubject();
        Teacher teacher = createTestTeacher();

        // Student A: 3 PRESENT across 3 distinct sessions
        for (int i = 0; i < 3; i++) {
            AttendanceSession session = createTestSession(teacher, subject, section, "A" + i, "CONDUCTED");
            saveRecord(studentA, subject, section, teacher, session, true);
        }
        // Student B: 2 ABSENT across 2 distinct sessions
        for (int i = 0; i < 2; i++) {
            AttendanceSession session = createTestSession(teacher, subject, section, "B" + i, "CONDUCTED");
            saveRecord(studentB, subject, section, teacher, session, false);
        }

        assertEquals(3L, aggregationRepository.countByStudentId(studentA.getId()));
        assertEquals(3L, aggregationRepository.countByStudentIdAndIsPresentTrue(studentA.getId()));
        assertEquals(2L, aggregationRepository.countByStudentId(studentB.getId()));
        assertEquals(0L, aggregationRepository.countByStudentIdAndIsPresentTrue(studentB.getId()));
    }

    @Test
    void countByStudentIdAndSubjectId_filteredFromAnotherSubject() {
        Section section = createTestSection();
        Student student = createTestStudent(section);
        Subject subjectX = createTestSubject();
        Subject subjectY = createTestSubject();
        Teacher teacher = createTestTeacher();

        // Subject X: 4 PRESENT in 4 distinct sessions
        for (int i = 0; i < 4; i++) {
            AttendanceSession session = createTestSession(teacher, subjectX, section, "X" + i, "CONDUCTED");
            saveRecord(student, subjectX, section, teacher, session, true);
        }
        // Subject Y: 2 PRESENT in 2 distinct sessions
        for (int i = 0; i < 2; i++) {
            AttendanceSession session = createTestSession(teacher, subjectY, section, "Y" + i, "CONDUCTED");
            saveRecord(student, subjectY, section, teacher, session, true);
        }

        assertEquals(4L, aggregationRepository.countByStudentIdAndSubjectId(student.getId(), subjectX.getId()));
        assertEquals(4L, aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(student.getId(), subjectX.getId()));
        assertEquals(2L, aggregationRepository.countByStudentIdAndSubjectId(student.getId(), subjectY.getId()));
        assertEquals(6L, aggregationRepository.countByStudentId(student.getId()));
    }

    @Test
    void countByStudentId_scheduledSessionsCount() {
        Section section = createTestSection();
        Student student = createTestStudent(section);
        Subject subject = createTestSubject();
        Teacher teacher = createTestTeacher();

        // 2 SCHEDULED + 1 CONDUCTED = 3 records total; 1 PRESENT
        AttendanceSession sched1 = createTestSession(teacher, subject, section, "S1", "SCHEDULED");
        saveRecord(student, subject, section, teacher, sched1, false);
        AttendanceSession sched2 = createTestSession(teacher, subject, section, "S2", "SCHEDULED");
        saveRecord(student, subject, section, teacher, sched2, true);
        AttendanceSession conducted = createTestSession(teacher, subject, section, "S3", "CONDUCTED");
        saveRecord(student, subject, section, teacher, conducted, false);

        assertEquals(3L, aggregationRepository.countByStudentId(student.getId()));
        assertEquals(1L, aggregationRepository.countByStudentIdAndIsPresentTrue(student.getId()));
    }

    @Test
    void countByStudentId_zeroRecords_returnsZero() {
        Section section = createTestSection();
        Student student = createTestStudent(section);

        assertEquals(0L, aggregationRepository.countByStudentId(student.getId()));
        assertEquals(0L, aggregationRepository.countByStudentIdAndIsPresentTrue(student.getId()));
    }

    @Test
    void countByStudentAndDateRange_inclusiveBounds() {
        Section section = createTestSection();
        Student student = createTestStudent(section);
        Subject subject = createTestSubject();
        Teacher teacher = createTestTeacher();

        saveRecord(student, subject, section, teacher, true, "2026-09-01");
        saveRecord(student, subject, section, teacher, true, "2026-09-04");
        saveRecord(student, subject, section, teacher, false, "2026-09-10");

        // Bound records (09-01 and 09-04) are included: start <= date <= end
        assertEquals(2L, aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), "2026-09-01", "2026-09-04"));
        assertEquals(2L, aggregationRepository.countPresentByStudentAndDateRange(student.getId(), "2026-09-01", "2026-09-04"));
        // Interior-only range
        assertEquals(1L, aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), "2026-09-02", "2026-09-04"));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndDateRange(student.getId(), "2026-09-02", "2026-09-04"));
        // Range matching the ABSENT record only
        assertEquals(1L, aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), "2026-09-05", "2026-09-10"));
        assertEquals(0L, aggregationRepository.countPresentByStudentAndDateRange(student.getId(), "2026-09-05", "2026-09-10"));
    }

    @Test
    void countByStudentAndDateRange_startOnly_endOnly_andNulls() {
        Section section = createTestSection();
        Student student = createTestStudent(section);
        Subject subject = createTestSubject();
        Teacher teacher = createTestTeacher();

        saveRecord(student, subject, section, teacher, true, "2026-09-01");
        saveRecord(student, subject, section, teacher, true, "2026-09-04");
        saveRecord(student, subject, section, teacher, false, "2026-09-10");

        // start-only
        assertEquals(2L, aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), "2026-09-04", null));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndDateRange(student.getId(), "2026-09-04", null));
        // end-only
        assertEquals(2L, aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), null, "2026-09-04"));
        assertEquals(2L, aggregationRepository.countPresentByStudentAndDateRange(student.getId(), null, "2026-09-04"));
        // null/null behaves like the full unfiltered count
        assertEquals(aggregationRepository.countByStudentId(student.getId()),
                aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), null, null));
        assertEquals(aggregationRepository.countByStudentIdAndIsPresentTrue(student.getId()),
                aggregationRepository.countPresentByStudentAndDateRange(student.getId(), null, null));
        // empty range
        assertEquals(0L, aggregationRepository.countRecordedByStudentAndDateRange(student.getId(), "2026-09-02", "2026-09-03"));
        assertEquals(0L, aggregationRepository.countPresentByStudentAndDateRange(student.getId(), "2026-09-02", "2026-09-03"));
    }

    @Test
    void countByStudentAndSubjectAndDateRange_filtersBySubject() {
        Section section = createTestSection();
        Student student = createTestStudent(section);
        Subject subjectX = createTestSubject();
        Subject subjectY = createTestSubject();
        Teacher teacher = createTestTeacher();

        saveRecord(student, subjectX, section, teacher, true, "2026-09-01");
        saveRecord(student, subjectX, section, teacher, false, "2026-09-04");
        saveRecord(student, subjectY, section, teacher, true, "2026-09-04");
        saveRecord(student, subjectY, section, teacher, false, "2026-09-10");

        // Subject X within 09-01..09-04: 2 recorded, 1 present
        assertEquals(2L, aggregationRepository.countRecordedByStudentAndSubjectAndDateRange(student.getId(), subjectX.getId(), "2026-09-01", "2026-09-04"));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndSubjectAndDateRange(student.getId(), subjectX.getId(), "2026-09-01", "2026-09-04"));
        // Subject Y in same range: 1 recorded, 1 present
        assertEquals(1L, aggregationRepository.countRecordedByStudentAndSubjectAndDateRange(student.getId(), subjectY.getId(), "2026-09-01", "2026-09-04"));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndSubjectAndDateRange(student.getId(), subjectY.getId(), "2026-09-01", "2026-09-04"));
        // Subject Y full range: 2 recorded, 1 present
        assertEquals(2L, aggregationRepository.countRecordedByStudentAndSubjectAndDateRange(student.getId(), subjectY.getId(), null, null));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndSubjectAndDateRange(student.getId(), subjectY.getId(), null, null));
    }

    @Test
    void countByStudentAndDateRange_filteredFromAnotherStudentsRecords() {
        Section section = createTestSection();
        Student studentA = createTestStudent(section);
        Student studentB = createTestStudent(section);
        Subject subject = createTestSubject();
        Teacher teacher = createTestTeacher();

        saveRecord(studentA, subject, section, teacher, true, "2026-09-01");
        saveRecord(studentA, subject, section, teacher, false, "2026-09-10");
        saveRecord(studentB, subject, section, teacher, true, "2026-09-04");

        // Student B's record is excluded even when inside the range
        assertEquals(2L, aggregationRepository.countRecordedByStudentAndDateRange(studentA.getId(), "2026-09-01", "2026-09-10"));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndDateRange(studentA.getId(), "2026-09-01", "2026-09-10"));
        assertEquals(1L, aggregationRepository.countRecordedByStudentAndDateRange(studentB.getId(), "2026-09-01", "2026-09-10"));
        assertEquals(1L, aggregationRepository.countPresentByStudentAndDateRange(studentB.getId(), "2026-09-01", "2026-09-10"));
    }
}
