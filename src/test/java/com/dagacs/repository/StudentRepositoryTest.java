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
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SectionRepository sectionRepository;

    private Student createTestStudent(String rollNumber) {
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
        Section section = sectionRepository.save(Section.builder()
                .sectionCode("StSec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return studentRepository.save(Student.builder()
                .rollNumber(rollNumber).name("TestStudent").gender("M")
                .fatherName("Father").motherName("Mother").photoUrl("url")
                .enrollmentNumber("E" + System.nanoTime()).age(20)
                .admissionDate("2026-01-01").status("ACTIVE")
                .academicSession(session).batch(batch).section(section).program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void saveAndRetrieve_validData() {
        Student student = createTestStudent("STU001");

        Optional<Student> found = studentRepository.findById(student.getId());
        assertTrue(found.isPresent());
        assertEquals("STU001", found.get().getRollNumber());
        assertEquals("TestStudent", found.get().getName());
    }

    @Test
    void existsById_afterSave_returnsTrue() {
        Student student = createTestStudent("STU002");
        assertTrue(studentRepository.existsById(student.getId()));
    }

    @Test
    void existsById_nonExistent_returnsFalse() {
        assertFalse(studentRepository.existsById(999999L));
    }

    @Test
    void uniqueRollNumber_duplicate_throws() {
        createTestStudent("DUP001");
        assertThrows(DataIntegrityViolationException.class,
                () -> createTestStudent("DUP001"));
    }

    @Test
    void findByEmail_returnsStudent() {
        Student student = createTestStudent("STU009");
        student.setEmail("stu9" + System.nanoTime() + "@dagacs.local");
        student = studentRepository.save(student);

        Optional<Student> found = studentRepository.findByEmail(student.getEmail());
        assertTrue(found.isPresent());
        assertEquals(student.getId(), found.get().getId());
    }

    @Test
    void findByEmail_unknown_returnsEmpty() {
        Optional<Student> found = studentRepository.findByEmail("nobody@dagacs.local");
        assertTrue(found.isEmpty());
    }
}
