package com.dagacs.service;

import com.dagacs.dto.TeacherCreateRequestDTO;
import com.dagacs.dto.TeacherLoginRequestDTO;
import com.dagacs.dto.TeacherManagementDTO;
import com.dagacs.dto.TeacherUpdateRequestDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Role;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherManagementServiceTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentManagementRepository studentRepository;

    @Mock
    private AccountProvisioningService accountProvisioningService;

    @InjectMocks
    private TeacherManagementService service;

    private Department department;

    @BeforeEach
    void setUp() {
        department = Department.builder().id(1L).name("Computer Science").code("CS").build();
    }

    private TeacherCreateRequestDTO createRequest() {
        return TeacherCreateRequestDTO.builder()
                .email("Prof.New@dagacs.local")
                .fullName("Prof New")
                .phone("1234567890")
                .designation("Professor")
                .departmentId(1L)
                .password("TempPass#1")
                .build();
    }

    private Teacher existingTeacher(Long id) {
        return Teacher.builder()
                .id(id)
                .email("prof.one@dagacs.local")
                .password("")
                .fullName("Prof One")
                .phone("1234567890")
                .designation("Associate Professor")
                .status("ACTIVE")
                .department(department)
                .isHod(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .avatarUrl("")
                .build();
    }

    private User linkedUser() {
        return User.builder()
                .id(10L)
                .email("prof.one@dagacs.local")
                .password("enc")
                .fullName("Prof One")
                .phone("")
                .status("ACTIVE")
                .role(Role.builder().id(3L).name("TEACHER").build())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .avatarUrl("")
                .build();
    }

    @Test
    void createTeacher_valid_savesProfileAndProvisionsLogin() {
        when(teacherRepository.findByEmail("prof.new@dagacs.local")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(false);
        when(studentRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherManagementDTO result = service.createTeacher(createRequest());

        assertEquals("prof.new@dagacs.local", result.getEmail());
        assertEquals("Prof New", result.getFullName());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("Computer Science", result.getDepartmentName());
        assertFalse(result.getIsHod());

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountProvisioningService).provisionLogin(emailCaptor.capture(), any(), any(), any(), any());
        assertEquals("prof.new@dagacs.local", emailCaptor.getValue());
    }

    @Test
    void createTeacher_duplicateTeacherEmail_returns409() {
        when(teacherRepository.findByEmail("prof.new@dagacs.local"))
                .thenReturn(Optional.of(existingTeacher(1L)));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.createTeacher(createRequest()));
        assertEquals(409, ex.getStatus());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void createTeacher_emailUsedByStudent_returns409() {
        when(teacherRepository.findByEmail("prof.new@dagacs.local")).thenReturn(Optional.empty());
        when(studentRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> service.createTeacher(createRequest()));
        assertEquals(409, ex.getStatus());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void createTeacher_emailUsedByLogin_returns409() {
        when(teacherRepository.findByEmail("prof.new@dagacs.local")).thenReturn(Optional.empty());
        when(studentRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(false);
        when(userRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> service.createTeacher(createRequest()));
        assertEquals(409, ex.getStatus());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void createTeacher_missingEmail_returns400() {
        TeacherCreateRequestDTO request = createRequest();
        request.setEmail("   ");

        AuthException ex = assertThrows(AuthException.class,
                () -> service.createTeacher(request));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void createTeacher_departmentNotFound_returns404() {
        when(teacherRepository.findByEmail("prof.new@dagacs.local")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(false);
        when(studentRepository.existsByEmail("prof.new@dagacs.local")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.createTeacher(createRequest()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void listTeachers_includesNullLoginFactsWhenUnlinked() {
        Teacher teacher = existingTeacher(1L);
        when(teacherRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(teacher));

        List<TeacherManagementDTO> result = service.listTeachers();

        assertEquals(1, result.size());
        assertFalse(result.get(0).getLoginLinked());
        assertNull(result.get(0).getLoginStatus());
    }

    @Test
    void listTeachers_includesLoginStatusWhenLinked() {
        Teacher teacher = existingTeacher(1L);
        when(teacherRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(teacher));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.of(linkedUser()));

        List<TeacherManagementDTO> result = service.listTeachers();

        assertTrue(result.get(0).getLoginLinked());
        assertEquals("ACTIVE", result.get(0).getLoginStatus());
    }

    @Test
    void getTeacher_unknown_returns404() {
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.getTeacher(99L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void updateTeacher_updatesMutableProfileFields() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherUpdateRequestDTO request = TeacherUpdateRequestDTO.builder()
                .fullName("Prof One Updated")
                .designation("Head Professor")
                .phone("000")
                .build();

        TeacherManagementDTO result = service.updateTeacher(1L, request);

        assertEquals("Prof One Updated", result.getFullName());
        assertEquals("Head Professor", result.getDesignation());
    }

    @Test
    void updateTeacher_changingDepartmentOfHod_returns409() {
        Teacher teacher = existingTeacher(1L);
        teacher.setIsHod(true);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));

        TeacherUpdateRequestDTO request = TeacherUpdateRequestDTO.builder()
                .departmentId(2L).build();
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(
                Department.builder().id(2L).name("Maths").code("M").build()));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.updateTeacher(1L, request));
        assertEquals(409, ex.getStatus());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void setTeacherStatus_neverTouchesLogin() {
        Teacher teacher = existingTeacher(1L);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherManagementDTO result = service.setTeacherStatus(1L, "INACTIVE");

        assertEquals("INACTIVE", result.getStatus());
        verify(accountProvisioningService, never()).changeStatus(any(), any());
    }

    @Test
    void setTeacherStatus_invalid_returns400() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.setTeacherStatus(1L, "SUSPENDED"));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void provisionLogin_createsUserWithTeacherEmail() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.empty());

        TeacherManagementDTO result = service.provisionLogin(1L,
                TeacherLoginRequestDTO.builder().password("TempPass#1").build());

        assertEquals(1L, result.getId());
        verify(accountProvisioningService).provisionLogin(
                "prof.one@dagacs.local", "Prof One", "TempPass#1", "TEACHER", null);
    }

    @Test
    void provisionLogin_alreadyLinkedToTeacher_returns409() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.of(linkedUser()));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.provisionLogin(1L, TeacherLoginRequestDTO.builder().password("TempPass#1").build()));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void provisionLogin_emailUsedByOtherRole_returns409() {
        User otherRole = linkedUser();
        otherRole.setRole(Role.builder().id(1L).name("ADMIN").build());
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.of(otherRole));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.provisionLogin(1L, TeacherLoginRequestDTO.builder().password("TempPass#1").build()));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void provisionLogin_unknownTeacher_returns404() {
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.provisionLogin(99L, TeacherLoginRequestDTO.builder().password("TempPass#1").build()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void setLoginStatus_togglesOnlyTheLogin() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.of(linkedUser()));

        service.setLoginStatus(1L, "INACTIVE");

        verify(accountProvisioningService).changeStatus(any(User.class), any());
    }

    @Test
    void setLoginStatus_noLoginLinked_returns404() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.setLoginStatus(1L, "INACTIVE"));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void setLoginPassword_resetsViaProvisioningService() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(existingTeacher(1L)));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.of(linkedUser()));

        service.setLoginPassword(1L, "NewPass#123");

        verify(accountProvisioningService).resetPassword(any(User.class), any());
    }

@Test
    void setTeacherStatus_profileInactiveLoginActive_independence() {
        // Setup: teacher with login linked and active
        Teacher teacher = existingTeacher(1L);
        teacher.setStatus("ACTIVE"); // profile active
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: set profile status to INACTIVE
        service.setTeacherStatus(1L, "INACTIVE");

        // Assert: profile status changed, login provisioning never touched (independence)
        assertEquals("INACTIVE", teacher.getStatus());
        verify(teacherRepository).save(teacher);
        verify(accountProvisioningService, never()).changeStatus(any(), any());
    }

    @Test
    void setLoginStatus_loginInactiveProfileActive_independence() {
        // Setup: teacher with login linked and active
        Teacher teacher = existingTeacher(1L);
        teacher.setStatus("ACTIVE"); // profile active
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(userRepository.findByEmail("prof.one@dagacs.local")).thenReturn(Optional.of(linkedUser()));

        // Act: set login status to INACTIVE
        service.setLoginStatus(1L, "INACTIVE");

        // Assert: only the login is toggled via provisioning; profile repository never touched (independence)
        assertEquals("ACTIVE", teacher.getStatus());
        verify(teacherRepository, never()).save(any());
        verify(accountProvisioningService).changeStatus(any(User.class), eq("INACTIVE"));
    }
}