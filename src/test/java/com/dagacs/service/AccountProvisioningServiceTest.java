package com.dagacs.service;

import com.dagacs.entity.Role;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M10A login-flow correction: admin {@code resetPassword} forces the password
 * change ONLY for STUDENT logins. TEACHER/HOD/ADMIN resets must keep the flag
 * untouched so a non-student account is never trapped in the STUDENT-only
 * change-password flow. Student temporary-login provisioning must keep forcing
 * the flag (M10A first-login contract).
 */
@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountProvisioningService accountProvisioningService;

    private Role role(String name) {
        return Role.builder()
                .name(name)
                .permissionLevel("1")
                .description(name + " role")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User userWithRole(String roleName) {
        return User.builder()
                .email(roleName.toLowerCase() + "@dagacs.local")
                .password("old-hash")
                .fullName(roleName + " User")
                .phone("")
                .status("ACTIVE")
                .role(role(roleName))
                .avatarUrl("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void resetPassword_student_setsMustChangePasswordTrue() {
        User student = userWithRole("STUDENT");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountProvisioningService.resetPassword(student, "NewStudentPass#1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().isMustChangePassword(),
                "STUDENT reset must force the password change");
        assertEquals("new-hash", captor.getValue().getPassword());
    }

    @Test
    void resetPassword_teacher_leavesMustChangePasswordFalse() {
        User teacher = userWithRole("TEACHER");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountProvisioningService.resetPassword(teacher, "NewTeacherPass#1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertFalse(captor.getValue().isMustChangePassword(),
                "TEACHER reset must not force the Student password-change flow");
        assertEquals("new-hash", captor.getValue().getPassword());
    }

    @Test
    void resetPassword_hod_leavesMustChangePasswordFalse() {
        User hod = userWithRole("HOD");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountProvisioningService.resetPassword(hod, "NewHodPass#1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertFalse(captor.getValue().isMustChangePassword(),
                "HOD reset must not force the Student password-change flow");
        assertEquals("new-hash", captor.getValue().getPassword());
    }

    @Test
    void resetPassword_admin_leavesMustChangePasswordFalse() {
        User admin = userWithRole("ADMIN");
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountProvisioningService.resetPassword(admin, "NewAdminPass#1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertFalse(captor.getValue().isMustChangePassword(),
                "ADMIN reset must not force the Student password-change flow");
        assertEquals("new-hash", captor.getValue().getPassword());
    }

    @Test
    void resetPassword_shortPassword_rejects400() {
        User user = userWithRole("STUDENT");

        AuthException ex = assertThrows(AuthException.class,
                () -> accountProvisioningService.resetPassword(user, "short"));

        assertEquals(400, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void provisionTemporaryLogin_setsMustChangePasswordTrue() {
        when(userRepository.existsByEmail("temp@dagacs.local")).thenReturn(false);
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(role("STUDENT")));
        when(passwordEncoder.encode("TempPass#1")).thenReturn("temp-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountProvisioningService.provisionTemporaryLogin(
                "temp@dagacs.local", "Temp Student", "STUDENT", "ACTIVE", "TempPass#1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().isMustChangePassword(),
                "Student temporary-login provisioning must keep forcing the password change");
    }

    @Test
    void provisionLogin_neverForcesPasswordChange() {
        when(userRepository.existsByEmail("plain@dagacs.local")).thenReturn(false);
        when(roleRepository.findByName("TEACHER")).thenReturn(Optional.of(role("TEACHER")));
        when(passwordEncoder.encode("PlainPass#1")).thenReturn("plain-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        accountProvisioningService.provisionLogin(
                "plain@dagacs.local", "Plain Teacher", "PlainPass#1", "TEACHER", "ACTIVE");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertFalse(captor.getValue().isMustChangePassword(),
                "Plain provisioning never forces the password change");
    }

    @Test
    void generateSecurePassword_isLongEnoughAndRandom() {
        String first = accountProvisioningService.generateSecurePassword();
        String second = accountProvisioningService.generateSecurePassword();

        assertTrue(first.length() >= 8);
        assertTrue(second.length() >= 8);
        assertTrue(!first.equals(second));
    }
}