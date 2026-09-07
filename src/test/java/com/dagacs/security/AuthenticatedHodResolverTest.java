package com.dagacs.security;

import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedHodResolverTest {

    private TeacherRepository teacherRepository;
    private AuthenticatedHodResolver resolver;

    private Department dept;
    private Teacher hod;

    @BeforeEach
    void setUp() {
        teacherRepository = mock(TeacherRepository.class);
        resolver = new AuthenticatedHodResolver(teacherRepository);
        dept = Department.builder().id(1L).name("Computer Science").code("CS").build();
        hod = Teacher.builder()
                .id(1L).email("hod@dagacs.local").fullName("HOD User")
                .designation("Professor").department(dept).isHod(true)
                .phone("").status("ACTIVE").avatarUrl("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private void setPrincipal(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, "pw"));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void validHod_resolves() {
        setPrincipal("hod@dagacs.local");
        when(teacherRepository.findByEmail("hod@dagacs.local")).thenReturn(Optional.of(hod));

        Teacher result = resolver.resolve();

        assertEquals(hod, result);
    }

    @Test
    void identityComesFromJwtPrincipal() {
        setPrincipal("hod@dagacs.local");
        when(teacherRepository.findByEmail("hod@dagacs.local")).thenReturn(Optional.of(hod));
        // The resolver must never accept a client-supplied teacherId/departmentId;
        // it keys exclusively on the JWT principal (email).
        resolver.resolve();
        assertEquals("hod@dagacs.local", hod.getEmail());
    }

    @Test
    void missingTeacher_returns401() {
        setPrincipal("nobody@dagacs.local");
        when(teacherRepository.findByEmail("nobody@dagacs.local")).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> resolver.resolve());

        assertEquals(401, ex.getStatus());
    }

    @Test
    void nonHodTeacher_returns401() {
        hod.setIsHod(false);
        setPrincipal("hod@dagacs.local");
        when(teacherRepository.findByEmail("hod@dagacs.local")).thenReturn(Optional.of(hod));

        AuthException ex = assertThrows(AuthException.class, () -> resolver.resolve());

        assertEquals(401, ex.getStatus());
    }

    @Test
    void hodWithoutDepartment_returns401() {
        hod.setDepartment(null);
        setPrincipal("hod@dagacs.local");
        when(teacherRepository.findByEmail("hod@dagacs.local")).thenReturn(Optional.of(hod));

        AuthException ex = assertThrows(AuthException.class, () -> resolver.resolve());

        assertEquals(401, ex.getStatus());
    }

    @Test
    void noSecurityContext_returns401() {
        SecurityContextHolder.clearContext();

        AuthException ex = assertThrows(AuthException.class, () -> resolver.resolve());

        assertEquals(401, ex.getStatus());
    }
}