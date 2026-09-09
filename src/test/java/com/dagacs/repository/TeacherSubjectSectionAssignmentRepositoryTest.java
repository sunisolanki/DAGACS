package com.dagacs.repository;

import com.dagacs.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class TeacherSubjectSectionAssignmentRepositoryTest {

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

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
                .password("Temp#123").fullName("Teacher")
                .phone("1234567890").designation("Professor").status("ACTIVE")
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
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Prog").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void existsByTeacherSubjectSection_returnsTrue() {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        Section section = createSection();

        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subject(subject).section(section).build());

        assertTrue(assignmentRepository.existsByTeacherIdAndSubjectIdAndSectionId(
                teacher.getId(), subject.getId(), section.getId()));
    }

    @Test
    void existsByTeacherSubjectSection_returnsFalseWhenAbsent() {
        assertFalse(assignmentRepository.existsByTeacherIdAndSubjectIdAndSectionId(999L, 999L, 999L));
    }
}
