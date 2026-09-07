package com.dagacs.service;

import com.dagacs.dto.StudentProfileDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentProfileServiceTest {

    private final AuthenticatedStudentResolver resolver = mock(AuthenticatedStudentResolver.class);

    private final StudentProfileService service = new StudentProfileService(resolver);

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
                .email("student@dagacs.local")
                .name("Student User")
                .gender("M")
                .fatherName("Father")
                .motherName("Mother")
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
        assertEquals("student@dagacs.local", dto.getEmail());
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
    }

    @Test
    void getMyProfile_nullableEmail_mapsToNull() {
        Student current = student();
        current.setEmail(null);
        when(resolver.resolve()).thenReturn(current);

        StudentProfileDTO dto = service.getMyProfile();

        assertNull(dto.getEmail());
    }
}