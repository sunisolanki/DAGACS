package com.dagacs.repository;

import com.dagacs.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class SubjectOfferingRepositoryTest {

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Subject createSubject() {
        return subjectRepository.save(Subject.builder()
                .code("SO-" + System.nanoTime()).name("OfferedSubject")
                .description("Test").creditHours("3")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Semester createSemester() {
        Department dept = departmentRepository.save(Department.builder()
                .name("OffDept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("OffProg-" + System.nanoTime()).code("P").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("OffSess-" + System.nanoTime()).code("S")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return semesterRepository.save(Semester.builder()
                .name("Sem-" + System.nanoTime()).code("SEM").year(2026)
                .academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void saveAndRetrieve_validOffering() {
        Subject subject = createSubject();
        Semester semester = createSemester();

        SubjectOffering saved = subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        assertNotNull(saved.getId());
        assertTrue(subjectOfferingRepository.existsBySubjectAndSemester(subject, semester));
        SubjectOffering fetched = subjectOfferingRepository.findById(saved.getId()).orElseThrow();
        assertEquals(subject.getId(), fetched.getSubject().getId());
        assertEquals(semester.getId(), fetched.getSemester().getId());
    }

    @Test
    void sameSubjectAcrossDifferentSemesters_allowed() {
        Subject subject = createSubject();
        Semester semesterA = createSemester();
        Semester semesterB = createSemester();

        subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semesterA)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semesterB)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        assertEquals(2L, subjectOfferingRepository.count());
    }

    @Test
    void duplicateSubjectAndSemester_violatesUniqueConstraint() {
        Subject subject = createSubject();
        Semester semester = createSemester();

        subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        assertThrows(DataIntegrityViolationException.class,
                () -> subjectOfferingRepository.saveAndFlush(SubjectOffering.builder()
                        .subject(subject).semester(semester)
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()));
    }

    @Test
    void foreignKeyBlocksDeletingMappedSubject() {
        Subject subject = createSubject();
        Semester semester = createSemester();
        subjectOfferingRepository.saveAndFlush(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        subjectRepository.delete(subject);

        assertThrows(DataIntegrityViolationException.class,
                () -> subjectRepository.flush());
    }
}