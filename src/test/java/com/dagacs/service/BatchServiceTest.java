package com.dagacs.service;

import com.dagacs.dto.BatchDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
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
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private AcademicSessionRepository academicSessionRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private BatchService batchService;

    private Program program;
    private AcademicSession academicSession;

    @BeforeEach
    void setUp() {
        program = Program.builder().id(1L).name("M.Tech CSE").code("MTCSE").build();
        academicSession = AcademicSession.builder()
                .id(1L).name("2026-27").code("2026-27").semester("Semester 1").program(program).build();
    }

    private BatchDTO validBatchDTO() {
        BatchDTO dto = new BatchDTO();
        dto.setBatchCode("B2026");
        dto.setName("2026 Batch");
        dto.setYear(2026);
        dto.setAcademicSessionId(1L);
        dto.setMaxCapacity(60);
        return dto;
    }

    private Batch buildBatch() {
        return Batch.builder()
                .id(1L).batchCode("B2026").name("2026 Batch").year(2026)
                .academicSession(academicSession).program("M.Tech CSE").maxCapacity(60)
                .build();
    }

    @Test
    void saveBatch_valid_returnsDTO() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(batchRepository.existsByBatchCode("B2026")).thenReturn(false);
        when(batchRepository.existsByAcademicSessionAndName(academicSession, "2026 Batch")).thenReturn(false);
        when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchDTO result = batchService.saveBatch(validBatchDTO());
        assertNotNull(result);
        assertEquals("2026 Batch", result.getName());
        assertEquals(1L, result.getAcademicSessionId());
        assertEquals("M.Tech CSE", result.getProgram());
    }

    @Test
    void saveBatch_derivesProgramFromSession_notUserInput() {
        BatchDTO dto = validBatchDTO();
        dto.setProgram("WRONG PROGRAM");
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(batchRepository.existsByBatchCode("B2026")).thenReturn(false);
        when(batchRepository.existsByAcademicSessionAndName(academicSession, "2026 Batch")).thenReturn(false);
        when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchDTO result = batchService.saveBatch(dto);
        assertEquals("M.Tech CSE", result.getProgram());
    }

    @Test
    void saveBatch_duplicateWithinSession_returns409() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(batchRepository.existsByBatchCode("B2026")).thenReturn(false);
        when(batchRepository.existsByAcademicSessionAndName(academicSession, "2026 Batch")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.saveBatch(validBatchDTO()));
        assertEquals(409, ex.getStatus());
        verify(batchRepository, never()).save(any());
    }

    @Test
    void saveBatch_duplicateBatchCode_returns409() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(batchRepository.existsByBatchCode("B2026")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.saveBatch(validBatchDTO()));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void saveBatch_invalidSession_returns404() {
        when(academicSessionRepository.findById(99L)).thenReturn(Optional.empty());
        BatchDTO dto = validBatchDTO();
        dto.setAcademicSessionId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.saveBatch(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveBatch_missingSession_returns400() {
        BatchDTO dto = validBatchDTO();
        dto.setAcademicSessionId(null);

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.saveBatch(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveBatch_blankName_returns400() {
        BatchDTO dto = validBatchDTO();
        dto.setName(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.saveBatch(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void getBatchById_missing_returns404() {
        when(batchRepository.findById(5L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.getBatchById(5L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getBatchesBySession_invalidSession_returns404() {
        when(academicSessionRepository.findById(9L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.getBatchesBySession(9L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getBatchesBySession_valid_returnsList() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(batchRepository.findByAcademicSession(academicSession)).thenReturn(List.of(buildBatch()));

        List<BatchDTO> result = batchService.getBatchesBySession(1L);
        assertEquals(1, result.size());
        assertEquals("2026 Batch", result.get(0).getName());
    }

    @Test
    void deleteBatch_missing_returns404() {
        when(batchRepository.findById(3L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.deleteBatch(3L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteBatch_withSections_returns409_notDeleted() {
        Batch batch = buildBatch();
        Section section = Section.builder().id(1L).name("A").batch(batch).build();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findByBatch(batch)).thenReturn(List.of(section));

        AuthException ex = assertThrows(AuthException.class,
                () -> batchService.deleteBatch(1L));
        assertEquals(409, ex.getStatus());
        verify(batchRepository, never()).delete(any());
    }

    @Test
    void deleteBatch_noSections_deletes() {
        Batch batch = buildBatch();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findByBatch(batch)).thenReturn(List.of());

        batchService.deleteBatch(1L);
        verify(batchRepository).delete(batch);
    }
}
