package com.dagacs.service;

import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentManagementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentManagementServiceTest {

    @Mock
    private StudentManagementRepository studentRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private StudentManagementService service;

    private Program program;
    private Batch batch;
    private Section section;

    @BeforeEach
    void setUp() {
        program = Program.builder().id(1L).name("Computer Science").code("CS").build();
        batch = Batch.builder().id(1L).batchCode("B-2026").name("B1").year(2026)
                .program("Computer Science").build();
        section = Section.builder().id(1L).sectionCode("SEC-A").name("A").batch(batch)
                .status("ACTIVE").build();
    }

    private StudentManagementRequestDTO validRequest() {
        return StudentManagementRequestDTO.builder()
                .rollNumber("2201CE001")
                .email("student1@dagacs.local")
                .name("Rahul Kumar")
                .gender("M")
                .fatherName("Father")
                .motherName("Mother")
                .photoUrl("")
                .enrollmentNumber("ENR-001")
                .age(20)
                .admissionDate("2026-01-01")
                .programId(1L)
                .batchId(1L)
                .sectionId(1L)
                .build();
    }

    private StudentManagementRequestDTO minimalRequest() {
        return StudentManagementRequestDTO.builder()
                .rollNumber("2201CE001")
                .name("Rahul Kumar")
                .programId(1L)
                .batchId(1L)
                .sectionId(1L)
                .build();
    }

    private void stubValidReferences() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
    }

    private Student existingStudent() {
        return Student.builder()
                .id(1L)
                .rollNumber("2201CE001")
                .email("student1@dagacs.local")
                .name("Rahul Kumar")
                .gender("M")
                .fatherName("Father")
                .motherName("Mother")
                .photoUrl("")
                .enrollmentNumber("ENR-001")
                .age(20)
                .admissionDate("2026-01-01")
                .status("ACTIVE")
                .program(program)
                .batch(batch)
                .section(section)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createStudent_valid_savesAndReturnsDTO() {
        stubValidReferences();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.createStudent(validRequest());

        assertEquals("2201CE001", result.getRollNumber());
        assertEquals("Rahul Kumar", result.getName());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("Computer Science", result.getProgramName());
        assertEquals("B1", result.getBatchName());
        assertEquals("A", result.getSectionName());
        verify(studentRepository).save(any(Student.class));
    }

    @Test
    void createStudent_onlyMandatoryFields_nullOptionalsAcceptedAndSaved() {
        stubValidReferences();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.createStudent(minimalRequest());

        assertEquals("2201CE001", result.getRollNumber());
        assertEquals("Rahul Kumar", result.getName());
        assertEquals("ACTIVE", result.getStatus()); // status defaults to ACTIVE when absent
        assertEquals("Computer Science", result.getProgramName());
        verify(studentRepository).save(any(Student.class));
    }

    @Test
    void createStudent_missingRollNumber_returns400() {
        StudentManagementRequestDTO request = validRequest();
        request.setRollNumber("   ");

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(request));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_missingName_returns400() {
        StudentManagementRequestDTO request = validRequest();
        request.setName(null);

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(request));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_duplicateRollNumber_returns409() {
        when(studentRepository.existsByRollNumber("2201CE001")).thenReturn(true);
        stubValidReferences();

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_duplicateEmail_returns409() {
        when(studentRepository.existsByRollNumber("2201CE001")).thenReturn(false);
        when(studentRepository.existsByEmail("student1@dagacs.local")).thenReturn(true);
        stubValidReferences();

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_missingProgramReference_returns404() {
        when(programRepository.findById(1L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void createStudent_missingBatchReference_returns404() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(batchRepository.findById(1L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void createStudent_missingSectionReference_returns404() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findById(1L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void createStudent_invalidStatus_returns400() {
        StudentManagementRequestDTO request = validRequest();
        request.setStatus("SUSPENDED");
        stubValidReferences();

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(request));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_statusDefaultsToActive() {
        stubValidReferences();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.createStudent(validRequest());

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void getAllStudents_returnsListInNameOrder() {
        Student s = existingStudent();
        when(studentRepository.findAllByOrderByNameAsc()).thenReturn(List.of(s));

        List<StudentManagementDTO> result = service.getAllStudents();

        assertEquals(1, result.size());
        assertEquals("2201CE001", result.get(0).getRollNumber());
        assertEquals("Computer Science", result.get(0).getProgramName());
    }

    @Test
    void getStudentById_found_returnsDTO() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existingStudent()));

        StudentManagementDTO result = service.getStudentById(1L);

        assertEquals("Rahul Kumar", result.getName());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void getStudentById_unknown_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.getStudentById(99L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void updateStudent_valid_updatesFields() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existingStudent()));
        stubValidReferences();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementRequestDTO request = validRequest();
        request.setName("Rahul Kumar Updated");

        StudentManagementDTO result = service.updateStudent(1L, request);

        assertEquals("Rahul Kumar Updated", result.getName());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void updateStudent_onlyMandatoryFields_nullOptionalsAcceptedAndSaved() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existingStudent()));
        stubValidReferences();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.updateStudent(1L, minimalRequest());

        assertEquals("2201CE001", result.getRollNumber());
        verify(studentRepository).save(any(Student.class));
    }

    @Test
    void updateStudent_unknown_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.updateStudent(99L, validRequest()));
        assertEquals(404, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_duplicateRollNumber_returns409() {
        Student existing = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubValidReferences();
        when(studentRepository.existsByRollNumber("2201CE002")).thenReturn(true);

        StudentManagementRequestDTO request = validRequest();
        request.setRollNumber("2201CE002");

        AuthException ex = assertThrows(AuthException.class, () -> service.updateStudent(1L, request));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_duplicateEmail_returns409() {
        Student existing = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubValidReferences();
        when(studentRepository.existsByEmail("other@dagacs.local")).thenReturn(true);

        StudentManagementRequestDTO request = validRequest();
        request.setEmail("other@dagacs.local");

        AuthException ex = assertThrows(AuthException.class, () -> service.updateStudent(1L, request));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void setStudentStatus_activeToInactive() {
        Student student = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.setStudentStatus(1L, "INACTIVE");

        assertEquals("INACTIVE", student.getStatus());
        assertEquals("INACTIVE", result.getStatus());
    }

    @Test
    void setStudentStatus_inactiveToActive() {
        Student student = existingStudent();
        student.setStatus("INACTIVE");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.setStudentStatus(1L, "ACTIVE");

        assertEquals("ACTIVE", student.getStatus());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void setStudentStatus_invalidStatus_returns400() {
        AuthException ex = assertThrows(AuthException.class,
                () -> service.setStudentStatus(1L, "SUSPENDED"));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void setStudentStatus_unknownStudent_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.setStudentStatus(99L, "ACTIVE"));
        assertEquals(404, ex.getStatus());
    }
}