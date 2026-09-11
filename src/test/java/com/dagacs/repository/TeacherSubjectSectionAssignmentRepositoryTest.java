package com.dagacs.repository;

import com.dagacs.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private SemesterRepository semesterRepository;

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

    private AcademicSession createAcademicSession() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Prog-" + System.nanoTime()).code("P").duration("4yr")
                .description("Test").department(dept).build());
        return academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S")
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private SubjectOffering createOffering(Subject subject) {
        AcademicSession session = createAcademicSession();
        Semester semester = semesterRepository.save(Semester.builder()
                .name("Sem-" + System.nanoTime()).code("SEM").year(2026)
                .academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createSection(AcademicSession session) {
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Prog").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private TeacherSubjectSectionAssignment persistAssignment(Teacher teacher, SubjectOffering offering, Section section) {
        return assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).section(section)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void existsByTeacherSectionAndOfferingSubject_returnsTrue() {
        Teacher teacher = createTeacher();
        Subject subject = createSubject();
        SubjectOffering offering = createOffering(subject);
        Section section = createSection(offering.getSemester().getAcademicSession());

        persistAssignment(teacher, offering, section);

        assertTrue(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(
                teacher.getId(), section.getId(), subject.getId()));
    }

    @Test
    void existsByTeacherSectionAndOfferingSubject_returnsFalseWhenAbsent() {
        assertFalse(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(
                999L, 999L, 999L));
    }

    @Test
    void existsByExactTriple_returnsTrue() {
        Teacher teacher = createTeacher();
        SubjectOffering offering = createOffering(createSubject());
        Section section = createSection(offering.getSemester().getAcademicSession());

        persistAssignment(teacher, offering, section);

        assertTrue(assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndSectionId(
                teacher.getId(), offering.getId(), section.getId()));
    }

    @Test
    void existsBySubjectOfferingId_returnsTrueWhenReferenced() {
        Teacher teacher = createTeacher();
        SubjectOffering offering = createOffering(createSubject());
        Section section = createSection(offering.getSemester().getAcademicSession());

        persistAssignment(teacher, offering, section);

        assertTrue(assignmentRepository.existsBySubjectOfferingId(offering.getId()));
        assertFalse(assignmentRepository.existsBySubjectOfferingId(999L));
    }

    @Test
    void multipleTeachersForSameOfferingAndSection_areAllowed() {
        Teacher teacher1 = createTeacher();
        Teacher teacher2 = createTeacher();
        SubjectOffering offering = createOffering(createSubject());
        Section section = createSection(offering.getSemester().getAcademicSession());

        persistAssignment(teacher1, offering, section);
        persistAssignment(teacher2, offering, section);

        assertEquals(2, assignmentRepository.findByTeacherIdOrderByIdAsc(teacher1.getId()).size()
                + assignmentRepository.findByTeacherIdOrderByIdAsc(teacher2.getId()).size());
    }

    @Test
    void sameTeacherSameOfferingSameSection_uniqueViolation() {
        Teacher teacher = createTeacher();
        SubjectOffering offering = createOffering(createSubject());
        Section section = createSection(offering.getSemester().getAcademicSession());

        persistAssignment(teacher, offering, section);

        assertThrows(DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(TeacherSubjectSectionAssignment.builder()
                        .teacher(teacher).subjectOffering(offering).section(section)
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()));
    }

    @Test
    void findByTeacherIdOrderByIdAsc_returnsOwnedOnly() {
        Teacher teacher = createTeacher();
        Teacher other = createTeacher();

        Long ownId = 0L;
        Long otherId = 0L;
        for (int i = 0; i < 2; i++) {
            SubjectOffering offering = createOffering(createSubject());
            Section section = createSection(offering.getSemester().getAcademicSession());
            ownId = persistAssignment(teacher, offering, section).getId();
        }
        for (int i = 0; i < 1; i++) {
            SubjectOffering offering = createOffering(createSubject());
            Section section = createSection(offering.getSemester().getAcademicSession());
            otherId = persistAssignment(other, offering, section).getId();
        }

        List<TeacherSubjectSectionAssignment> owned = assignmentRepository.findByTeacherIdOrderByIdAsc(teacher.getId());
        assertEquals(2, owned.size());
        assertTrue(owned.stream().allMatch(a -> a.getTeacher().getId().equals(teacher.getId())));
        assertTrue(owned.get(0).getId() < owned.get(1).getId());
        assertEquals(otherId, assignmentRepository.findByTeacherIdOrderByIdAsc(other.getId()).get(0).getId());
        assertEquals(ownId, owned.get(1).getId());
    }

    @Test
    void findAllByOrderByIdAsc_returnsAll() {
        Teacher teacher = createTeacher();
        SubjectOffering offering = createOffering(createSubject());
        Section section = createSection(offering.getSemester().getAcademicSession());
        persistAssignment(teacher, offering, section);

        List<TeacherSubjectSectionAssignment> all = assignmentRepository.findAllByOrderByIdAsc();
        assertTrue(all.size() >= 1);
    }
}