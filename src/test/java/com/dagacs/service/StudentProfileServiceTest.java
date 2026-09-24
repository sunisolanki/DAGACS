package com.dagacs.service;

import com.dagacs.dto.StudentProfileDTO;
import com.dagacs.dto.StudentProfileRequestDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.repository.StudentRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class StudentProfileServiceTest {

    private final AuthenticatedStudentResolver resolver = mock(AuthenticatedStudentResolver.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);

    private final StudentProfileService service = new StudentProfileService(resolver, studentRepository);

    private Student student() {
        Batch batch = mock(Batch.class);
        when(batch.getName()).thenReturn("B1");

        Program program = mock(Program.class);
        when(program.getName()).thenReturn("Computer Science");

        Section section = mock(Section.class);
        when(section.getName()).thenReturn("A");

        Student student = Student.builder()
                .id(1L)
                .rollNumber("2201CE001")
                .enrollmentNumber("ENR-2022-001")
                .name("Student User")
                .gender("M")
                .fatherName("Father")
                .motherName("Mother")
                .personalEmail("student@test.com")
                .photoUrl("url")
                .age(20)
                .admissionDate("2026-01-01")
                .status("ACTIVE")
                .batch(batch)
                .program(program)
                .section(section)
                .build();
        return student;
    }

    @Test
    void getMyProfile_returnsDetailsOfResolvedStudent() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileDTO dto = service.getMyProfile();

        assertEquals("2201CE001", dto.getRollNumber());
        assertEquals("ENR-2022-001", dto.getEnrollmentNumber());
        assertEquals("Student User", dto.getName());
        assertEquals("M", dto.getGender());
        assertEquals("Father", dto.getFatherName());
        assertEquals("Mother", dto.getMotherName());
        assertEquals("url", dto.getPhotoUrl());
        assertEquals(20, dto.getAge());
        assertEquals("2026-01-01", dto.getAdmissionDate());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("B1", dto.getBatchName());
        assertEquals("Computer Science", dto.getProgramName());
        assertEquals("A", dto.getSectionName());
        assertEquals("student@test.com", dto.getPersonalEmail());
    }

    @Test
    void updateMyProfile_updatesFatherName() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setFatherName("Updated Father");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertEquals("Updated Father", current.getFatherName());
    }

    @Test
    void updateMyProfile_updatesMotherName() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setMotherName("Updated Mother");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertEquals("Updated Mother", current.getMotherName());
    }

    @Test
    void updateMyProfile_updatesGender() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setGender("F");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertEquals("F", current.getGender());
    }

    @Test
    void updateMyProfile_preservesOtherFields() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setFatherName("New Father");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        assertEquals("New Father", current.getFatherName());
        assertEquals("Mother", current.getMotherName());
        assertEquals("M", current.getGender());
        assertEquals("Student User", current.getName());
        assertEquals("2201CE001", current.getRollNumber());
    }

    @Test
    void updateMyProfile_setsUpdatedAt() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setFatherName("New Father");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
    }

    @Test
    void updateMyProfile_updatesPersonalEmail() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setPersonalEmail("newemail@test.com");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertEquals("newemail@test.com", current.getPersonalEmail());
    }

    @Test
    void updateMyProfile_personalEmailInvalidIgnored() {
        Student current = student();
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setPersonalEmail("not-an-email");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertEquals("student@test.com", current.getPersonalEmail());
    }

    @Test
    void updateMyProfile_personalEmailEmptyClears() {
        Student current = student();
        current.setPersonalEmail("old@test.com");
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setPersonalEmail("");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertNull(current.getPersonalEmail());
    }

    @Test
    void updateMyProfile_preservesAuthEmail() {
        Student current = student();
        current.setEmail("auth@dagacs.local");
        when(resolver.resolve()).thenReturn(current);

        StudentProfileRequestDTO request = new StudentProfileRequestDTO();
        request.setPersonalEmail("newemail@test.com");

        assertDoesNotThrow(() -> service.updateMyProfile(request));

        verify(studentRepository).save(current);
        assertEquals("auth@dagacs.local", current.getEmail());
        assertEquals("newemail@test.com", current.getPersonalEmail());
    }
}
