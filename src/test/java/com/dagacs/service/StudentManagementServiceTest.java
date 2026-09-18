package com.dagacs.service;

import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentManagementServiceTest {

    @Mock
    private StudentManagementRepository studentRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private AcademicSessionRepository academicSessionRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountProvisioningService accountProvisioningService;

    @InjectMocks
    private StudentManagementService service;

    private Program program;
    private Batch batch;
    private Section section;

    @BeforeEach
    void setUp() {
        program = Program.builder().id(1L).name("Computer Science").code("CS").build();
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27")
                .program(program).description("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        batch = Batch.builder().id(1L).batchCode("B-2026").name("B1")
                .year(2026).academicSession(session).program("Computer Science")
                .build();
        section = Section.builder().id(1L).sectionCode("SEC-A").name("A")
                .batch(batch).status("ACTIVE").build();
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
                .academicSessionId(1L)
                .programId(1L)
                .batchId(1L)
                .sectionId(1L)
                .build();
    }

    private StudentManagementRequestDTO minimalRequest() {
        return StudentManagementRequestDTO.builder()
                .rollNumber("2201CE001")
                .name("Rahul Kumar")
                .academicSessionId(1L)
                .programId(1L)
                .batchId(1L)
                .sectionId(1L)
                .build();
    }

    private void stubValidReferences() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(
                AcademicSession.builder().id(1L).name("2026-27").code("2026-27")
                        .program(program).description("")
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build()));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
    }

    /**
     * Default absent states for the M9.5.2 cross-entity email checks: the email
     * is not owned by a teacher profile or a login account, and no login is
     * linked to the student email (so {@code convertToDTO} can resolve it).
     */
    private void stubNoCollisionOrLink() {
        stubTeacherNoCollision();
        stubNoLinkedLogin();
    }

    /**
     * Only needed when a code path reaches ensureEmailAvailableForStudent (create
     * or update with an email change).
     */
    private void stubTeacherNoCollision() {
        when(teacherRepository.findByEmail("student1@dagacs.local")).thenReturn(Optional.empty());
    }

    /** Only needed when convertToDTO reaches the user-linked-email lookup. */
    private void stubNoLinkedLogin() {
        when(userRepository.findByEmail("student1@dagacs.local")).thenReturn(Optional.empty());
    }

    private User studentLoginFixture() {
        return User.builder()
                .id(7L)
                .email("student1@dagacs.local")
                .password("encoded")
                .fullName("Rahul Kumar")
                .phone("")
                .status("ACTIVE")
                .avatarUrl("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .role(Role.builder().id(1L).name("STUDENT").build())
                .build();
    }

    private Student existingStudent() {
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27")
                .program(program).description("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
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
                .academicSession(session)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createStudent_valid_savesAndReturnsDTO() {
        stubValidReferences();
        stubNoCollisionOrLink();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        User provisionedUser = User.builder()
                .id(7L).email("student1@dagacs.local")
                .password("encoded").fullName("Rahul Kumar")
                .phone("").status("ACTIVE").avatarUrl("")
                .mustChangePassword(true)
                .role(Role.builder().id(1L).name("STUDENT").build())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(accountProvisioningService.provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123"))
                .thenReturn(provisionedUser);

        StudentManagementDTO result = service.createStudent(validRequest());

        assertEquals("2201CE001", result.getRollNumber());
        assertEquals("Rahul Kumar", result.getName());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("Computer Science", result.getProgramName());
        assertEquals("B1", result.getBatchName());
        assertEquals("A", result.getSectionName());
        assertTrue(result.isLoginLinked());
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
        stubNoCollisionOrLink();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        User provisionedUser = User.builder()
                .id(7L).email("student1@dagacs.local")
                .password("encoded").fullName("Rahul Kumar")
                .phone("").status("ACTIVE").avatarUrl("")
                .mustChangePassword(true)
                .role(Role.builder().id(1L).name("STUDENT").build())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        lenient().when(accountProvisioningService.provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123"))
                .thenReturn(provisionedUser);

        StudentManagementDTO result = service.createStudent(validRequest());

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void createStudent_autoProvisionsStudentLogin_withTemporaryPassword() {
        stubValidReferences();
        stubTeacherNoCollision();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        User provisioned = User.builder()
                .id(7L)
                .email("student1@dagacs.local")
                .password("encoded")
                .fullName("Rahul Kumar")
                .phone("")
                .status("ACTIVE")
                .avatarUrl("")
                .mustChangePassword(true)
                .role(Role.builder().id(1L).name("STUDENT").build())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.of(provisioned));
        when(accountProvisioningService.provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123"))
                .thenReturn(provisioned);

        StudentManagementDTO result = service.createStudent(validRequest());

        assertEquals("Dagacs@123", result.getTemporaryPassword());
        assertTrue(result.isLoginLinked());
        assertTrue(result.isMustChangePassword());
        assertEquals("ACTIVE", result.getLoginStatus());
        verify(accountProvisioningService).provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123");
        verify(studentRepository).save(any(Student.class));
    }

    @Test
    void createStudent_noEmail_doesNotProvisionLogin() {
        stubValidReferences();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentManagementDTO result = service.createStudent(minimalRequest());

        assertEquals(null, result.getTemporaryPassword());
        verify(accountProvisioningService, never())
                .provisionTemporaryLogin(any(), any(), any(), any(), any());
        verify(accountProvisioningService, never()).generateSecurePassword();
    }

    @Test
    void getAllStudents_returnsListInNameOrder() {
        Student s = existingStudent();
        when(studentRepository.findAllByOrderByNameAsc()).thenReturn(List.of(s));
        stubNoLinkedLogin();

        List<StudentManagementDTO> result = service.getAllStudents();

        assertEquals(1, result.size());
        assertEquals("2201CE001", result.get(0).getRollNumber());
        assertEquals("Computer Science", result.get(0).getProgramName());
    }

    @Test
    void getStudentById_found_returnsDTO() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existingStudent()));
        stubNoLinkedLogin();

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
        stubNoLinkedLogin();
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
        stubNoLinkedLogin();
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
        stubNoLinkedLogin();
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
        stubNoLinkedLogin();
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

    @Test
    void provisionLogin_valid_provisionsTemporaryStudentLogin() {
        Student student = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        User provisioned = User.builder()
                .id(7L)
                .email("student1@dagacs.local")
                .password("encoded")
                .fullName("Rahul Kumar")
                .phone("")
                .status("ACTIVE")
                .avatarUrl("")
                .mustChangePassword(true)
                .role(Role.builder().id(1L).name("STUDENT").build())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.empty(), Optional.of(provisioned));
        when(accountProvisioningService.provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123"))
                .thenReturn(provisioned);

        StudentManagementDTO result = service.provisionLogin(1L);

        verify(accountProvisioningService).provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123");
        assertEquals("Dagacs@123", result.getTemporaryPassword());
        assertEquals("Rahul Kumar", result.getName());
        assertTrue(result.isLoginLinked());
        assertTrue(result.isMustChangePassword());
        assertEquals("ACTIVE", result.getLoginStatus());
    }

    @Test
    void provisionLogin_studentWithoutEmail_returns400() {
        Student student = existingStudent();
        student.setEmail(null);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        AuthException ex = assertThrows(AuthException.class, () -> service.provisionLogin(1L));
        assertEquals(400, ex.getStatus());
        verify(accountProvisioningService, never())
                .provisionTemporaryLogin(any(), any(), any(), any(), any());
        verify(accountProvisioningService, never()).generateSecurePassword();
    }

    @Test
    void provisionLogin_alreadyLinkedStudentLogin_returns409() {
        Student student = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.of(studentLoginFixture()));

        AuthException ex = assertThrows(AuthException.class, () -> service.provisionLogin(1L));
        assertEquals(409, ex.getStatus());
        verify(accountProvisioningService, never())
                .provisionTemporaryLogin(any(), any(), any(), any(), any());
        verify(accountProvisioningService, never()).generateSecurePassword();
    }

    @Test
    void provisionLogin_emailUsedByAnotherLogin_returns409() {
        Student student = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.of(User.builder()
                        .role(Role.builder().name("TEACHER").build()).build()));

        AuthException ex = assertThrows(AuthException.class, () -> service.provisionLogin(1L));
        assertEquals(409, ex.getStatus());
        verify(accountProvisioningService, never())
                .provisionTemporaryLogin(any(), any(), any(), any(), any());
    }

    @Test
    void provisionLogin_unknownStudent_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.provisionLogin(99L));
        assertEquals(404, ex.getStatus());
        verify(accountProvisioningService, never())
                .provisionTemporaryLogin(any(), any(), any(), any(), any());
    }

    @Test
    void setLoginStatus_linkedUser_changesLoginStatus() {
        Student student = existingStudent();
        User user = studentLoginFixture();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findByEmail("student1@dagacs.local")).thenReturn(Optional.of(user));

        StudentManagementDTO result = service.setLoginStatus(1L, "INACTIVE");

        verify(accountProvisioningService).changeStatus(user, "INACTIVE");
        assertTrue(result.isLoginLinked());
    }

    @Test
    void setLoginStatus_noLinkedLogin_returns404() {
        Student student = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        stubNoLinkedLogin();

        AuthException ex = assertThrows(AuthException.class,
                () -> service.setLoginStatus(1L, "INACTIVE"));
        assertEquals(404, ex.getStatus());
        verify(accountProvisioningService, never()).changeStatus(any(), any());
    }

    @Test
    void setLoginPassword_linkedUser_resetsPassword() {
        Student student = existingStudent();
        User user = studentLoginFixture();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findByEmail("student1@dagacs.local")).thenReturn(Optional.of(user));

        StudentManagementDTO result = service.setLoginPassword(1L, "NewPass#9");

        verify(accountProvisioningService).resetPassword(user, "NewPass#9");
        assertTrue(result.isLoginLinked());
    }

    @Test
    void setLoginPassword_unknownStudent_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.setLoginPassword(99L, "NewPass#9"));
        assertEquals(404, ex.getStatus());
        verify(accountProvisioningService, never()).resetPassword(any(), any());
    }

    @Test
    void createStudent_emailUsedByTeacher_returns409() {
        stubValidReferences();
        when(teacherRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.of(Teacher.builder().email("student1@dagacs.local").build()));

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_emailUsedByLogin_returns409() {
        stubValidReferences();
        when(teacherRepository.findByEmail("student1@dagacs.local")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("student1@dagacs.local")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_emailChangeWhileLoginLinked_returns409() {
        Student existing = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubValidReferences();
        when(userRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.of(studentLoginFixture()));

        StudentManagementRequestDTO request = validRequest();
        request.setEmail("new@dagacs.local");

        AuthException ex = assertThrows(AuthException.class, () -> service.updateStudent(1L, request));
        assertEquals(409, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void getStudentById_linkedLogin_populatesLoginFields() {
        Student student = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findByEmail("student1@dagacs.local"))
                .thenReturn(Optional.of(studentLoginFixture()));

        StudentManagementDTO result = service.getStudentById(1L);

        assertTrue(result.isLoginLinked());
        assertEquals("ACTIVE", result.getLoginStatus());
    }

    @Test
    void createStudent_programMismatch_returns400() {
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27")
                .program(program).description("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        AcademicSession otherSession = AcademicSession.builder()
                .id(2L).name("2027-28").code("2027-28")
                .program(Program.builder().id(2L).name("Electronics").build())
                .description("").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(
                Batch.builder().id(1L).batchCode("B-2026").name("B1")
                        .year(2026).academicSession(otherSession).program("Electronics").build()));

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_sectionBatchMismatch_returns400() {
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27")
                .program(program).description("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        Section otherSection = Section.builder().id(2L).sectionCode("SEC-B").name("B")
                .batch(Batch.builder().id(2L).batchCode("B-2027").name("B2")
                        .year(2027).academicSession(batch.getAcademicSession())
                        .program("Computer Science").build())
                .status("ACTIVE").build();
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(otherSection));

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_programMismatch_returns400() {
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27")
                .program(program).description("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        Student existing = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        AcademicSession otherSession = AcademicSession.builder()
                .id(2L).name("2027-28").code("2027-28")
                .program(Program.builder().id(2L).name("Electronics").build())
                .description("").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(
                Batch.builder().id(1L).batchCode("B-2026").name("B1")
                        .year(2026).academicSession(otherSession).program("Electronics").build()));

        AuthException ex = assertThrows(AuthException.class, () -> service.updateStudent(1L, validRequest()));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_sectionBatchMismatch_returns400() {
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27")
                .program(program).description("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        Student existing = existingStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        Section otherSection = Section.builder().id(2L).sectionCode("SEC-B").name("B")
                .batch(Batch.builder().id(2L).batchCode("B-2027").name("B2")
                        .year(2027).academicSession(batch.getAcademicSession())
                        .program("Computer Science").build())
                .status("ACTIVE").build();
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(otherSection));

        AuthException ex = assertThrows(AuthException.class, () -> service.updateStudent(1L, validRequest()));
        assertEquals(400, ex.getStatus());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createStudent_sameProgramIdDifferentNames_accepted() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        AcademicSession sameIdSession = AcademicSession.builder()
                .id(1L).name("2027-28").code("2027-28")
                .program(Program.builder().id(1L).name("B.Tech CSE").build())
                .description("").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(sameIdSession));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(
                Batch.builder().id(1L).batchCode("B-2026").name("B1")
                        .year(2026).academicSession(sameIdSession).program("B.Tech CSE").build()));
        stubTeacherNoCollision();
        stubNoLinkedLogin();
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountProvisioningService.provisionTemporaryLogin(
                "student1@dagacs.local", "Rahul Kumar", "STUDENT", "ACTIVE", "Dagacs@123"))
                .thenReturn(studentLoginFixture());

        StudentManagementDTO result = service.createStudent(validRequest());

        assertNotNull(result);
        assertEquals("2201CE001", result.getRollNumber());
        verify(studentRepository).save(any(Student.class));
    }

    @Test
    void createStudent_differentProgramIdsSameName_returns400() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        AcademicSession otherIdSession = AcademicSession.builder()
                .id(1L).name("2027-28").code("2027-28")
                .program(Program.builder().id(2L).name("Computer Science").build())
                .description("").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(otherIdSession));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(
                Batch.builder().id(1L).batchCode("B-2026").name("B1")
                        .year(2026).academicSession(otherIdSession).program("Computer Science").build()));

        AuthException ex = assertThrows(AuthException.class, () -> service.createStudent(validRequest()));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("program"));
        verify(studentRepository, never()).save(any());
    }
}