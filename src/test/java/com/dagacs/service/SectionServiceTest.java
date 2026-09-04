package com.dagacs.service;

import com.dagacs.dto.SectionDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.SectionRepository;
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
class SectionServiceTest {

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private SectionService sectionService;

    private Batch batch;

    @BeforeEach
    void setUp() {
        Program program = Program.builder().id(1L).name("M.Tech CSE").code("MTCSE").build();
        AcademicSession session = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27").semester("Semester 1").program(program).build();
        batch = Batch.builder()
                .id(1L).batchCode("B2026").name("2026 Batch").year(2026)
                .academicSession(session).program("M.Tech CSE").maxCapacity(60)
                .build();
    }

    private SectionDTO validSectionDTO() {
        SectionDTO dto = new SectionDTO();
        dto.setSectionCode("SEC-A");
        dto.setName("A");
        dto.setMaxCapacity(30);
        dto.setBatchId(1L);
        return dto;
    }

    private Section buildSection() {
        return Section.builder()
                .id(1L).sectionCode("SEC-A").name("A").maxCapacity(30).batch(batch)
                .build();
    }

    @Test
    void saveSection_valid_returnsDTO() {
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.existsBySectionCode("SEC-A")).thenReturn(false);
        when(sectionRepository.existsByNameAndBatch("A", batch)).thenReturn(false);
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        SectionDTO result = sectionService.saveSection(validSectionDTO());
        assertNotNull(result);
        assertEquals("A", result.getName());
        assertEquals(1L, result.getBatchId());
    }

    @Test
    void saveSection_duplicateWithinBatch_returns409() {
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.existsBySectionCode("SEC-A")).thenReturn(false);
        when(sectionRepository.existsByNameAndBatch("A", batch)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.saveSection(validSectionDTO()));
        assertEquals(409, ex.getStatus());
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void saveSection_invalidBatch_returns404() {
        when(batchRepository.findById(99L)).thenReturn(Optional.empty());
        SectionDTO dto = validSectionDTO();
        dto.setBatchId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.saveSection(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveSection_missingBatch_returns400() {
        SectionDTO dto = validSectionDTO();
        dto.setBatchId(null);

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.saveSection(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSection_blankName_returns400() {
        SectionDTO dto = validSectionDTO();
        dto.setName(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.saveSection(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void getSectionById_missing_returns404() {
        when(sectionRepository.findById(5L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.getSectionById(5L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getSectionsByBatch_invalidBatch_returns404() {
        when(batchRepository.findById(9L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.getSectionsByBatch(9L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getSectionsByBatch_valid_returnsList() {
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findByBatch(batch)).thenReturn(List.of(buildSection()));

        List<SectionDTO> result = sectionService.getSectionsByBatch(1L);
        assertEquals(1, result.size());
        assertEquals("A", result.get(0).getName());
    }

    @Test
    void deleteSection_missing_returns404() {
        when(sectionRepository.findById(3L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> sectionService.deleteSection(3L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteSection_valid_deletes() {
        Section section = buildSection();
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));

        sectionService.deleteSection(1L);
        verify(sectionRepository).delete(section);
    }
}
