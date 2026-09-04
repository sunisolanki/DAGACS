package com.dagacs.service;

import com.dagacs.dto.SubjectDTO;
import com.dagacs.entity.Section;
import com.dagacs.entity.Subject;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private SubjectService subjectService;

    private Subject subject;

    @BeforeEach
    void setUp() {
        subject = Subject.builder()
                .id(1L).code("CS101").name("Data Structures")
                .description("Course on data structures")
                .creditHours("3").department("CSE").status("ACTIVE")
                .build();
    }

    private SubjectDTO validSubjectDTO() {
        SubjectDTO dto = new SubjectDTO();
        dto.setCode("CS101");
        dto.setName("Data Structures");
        dto.setDescription("Course on data structures");
        dto.setCreditHours("3");
        dto.setDepartment("CSE");
        dto.setStatus("ACTIVE");
        return dto;
    }

    @Test
    void saveSubject_valid_returnsDTO() {
        when(subjectRepository.existsByCode("CS101")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectDTO result = subjectService.saveSubject(validSubjectDTO());
        assertNotNull(result);
        assertEquals("CS101", result.getCode());
        assertEquals("Data Structures", result.getName());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void saveSubject_duplicateCode_returns409() {
        when(subjectRepository.existsByCode("CS101")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.saveSubject(validSubjectDTO()));
        assertEquals(409, ex.getStatus());
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void saveSubject_blankCode_returns400() {
        SubjectDTO dto = validSubjectDTO();
        dto.setCode(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.saveSubject(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSubject_blankName_returns400() {
        SubjectDTO dto = validSubjectDTO();
        dto.setName(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.saveSubject(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void getAllSubjects_returnsList() {
        when(subjectRepository.findAllByOrderByName()).thenReturn(List.of(subject));

        List<SubjectDTO> result = subjectService.getAllSubjects();
        assertEquals(1, result.size());
        assertEquals("Data Structures", result.get(0).getName());
    }

    @Test
    void getSubjectById_valid_returnsDTO() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));

        SubjectDTO result = subjectService.getSubjectById(1L);
        assertEquals("CS101", result.getCode());
    }

    @Test
    void getSubjectById_missing_returns404() {
        when(subjectRepository.findById(5L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.getSubjectById(5L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void updateSubject_valid_returnsDTO() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectDTO dto = validSubjectDTO();
        dto.setName("Advanced Data Structures");

        SubjectDTO result = subjectService.updateSubject(1L, dto);
        assertEquals("Advanced Data Structures", result.getName());
    }

    @Test
    void updateSubject_duplicateCodeOnOther_returns409() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(subjectRepository.existsByCodeAndIdNot("CS200", 1L)).thenReturn(true);

        SubjectDTO dto = validSubjectDTO();
        dto.setCode("CS200");

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.updateSubject(1L, dto));
        assertEquals(409, ex.getStatus());
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void updateSubject_missing_returns404() {
        when(subjectRepository.findById(7L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.updateSubject(7L, validSubjectDTO()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteSubject_missing_returns404() {
        when(subjectRepository.findById(3L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.deleteSubject(3L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteSubject_referencedBySection_returns409_notDeleted() {
        Section section = Section.builder().id(1L).name("A").subject(subject).build();
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findBySubject(subject)).thenReturn(List.of(section));

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectService.deleteSubject(1L));
        assertEquals(409, ex.getStatus());
        verify(subjectRepository, never()).delete(any());
    }

    @Test
    void deleteSubject_unreferenced_deletes() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findBySubject(subject)).thenReturn(List.of());

        subjectService.deleteSubject(1L);
        verify(subjectRepository).delete(subject);
    }
}
