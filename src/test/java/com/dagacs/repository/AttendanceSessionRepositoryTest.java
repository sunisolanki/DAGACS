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
class AttendanceSessionRepositoryTest {

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private TeacherRepository teacherRepository;

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

    private Teacher createTeacher() {
        return teacherRepository.save(Teacher.builder()
                .email("t" + System.nanoTime() + "@dagacs.local")
                .password("Temp#123")
                .fullName("Teacher")
                .phone("1234567890")
                .designation("Professor")
                .status("ACTIVE")
                .avatarUrl("url")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Subject createSubject() {
        return subjectRepository.save(Subject.builder()
                .code("SUBJ-" + System.nanoTime()).name("TestSubject")
                .description("Test").creditHours("3").department("CSE")
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

    private AttendanceSession buildSession(Teacher teacher, Subject subject, Section section,
                                           String period, String date) {
        return AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod(period).date(date).status("SCHEDULED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void saveAndRetrieve_validSession() {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        Section section = createSection();

        AttendanceSession session = attendanceSessionRepository.save(
                buildSession(teacher, subject, section, "1st", "2026-09-04"));

        Optional<AttendanceSession> found = attendanceSessionRepository.findById(session.getId());
        assertTrue(found.isPresent());
        assertEquals("1st", found.get().getLecturePeriod());
        assertEquals("SCHEDULED", found.get().getStatus());
        assertEquals(teacher.getId(), found.get().getTeacherEntity().getId());
    }

    @Test
    void duplicateSessionConstraint_throws() {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        Section section = createSection();

        attendanceSessionRepository.save(buildSession(teacher, subject, section, "1st", "2026-09-04"));
        attendanceSessionRepository.flush();

        AttendanceSession duplicate = buildSession(teacher, subject, section, "1st", "2026-09-04");
        assertThrows(DataIntegrityViolationException.class,
                () -> attendanceSessionRepository.saveAndFlush(duplicate));
    }

    @Test
    void existsBySubjectSectionDatePeriod_returnsTrue() {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        Section section = createSection();

        attendanceSessionRepository.save(buildSession(teacher, subject, section, "1st", "2026-09-04"));

        assertTrue(attendanceSessionRepository
                .existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                        subject.getId(), section.getId(), "2026-09-04", "1st"));
    }

    @Test
    void findSessionById_returnsSession() {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        Section section = createSection();

        AttendanceSession session = attendanceSessionRepository.save(
                buildSession(teacher, subject, section, "2nd", "2026-09-05"));

        assertTrue(attendanceSessionRepository.findById(session.getId()).isPresent());
    }
}
