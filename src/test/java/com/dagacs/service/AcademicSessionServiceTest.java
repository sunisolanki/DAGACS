package com.dagacs.service;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Program;
import com.dagacs.entity.Semester;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SemesterRepository;
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

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class AcademicSessionServiceTest {

    @Mock
    private AcademicSessionRepository academicSessionRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private SemesterRepository semesterRepository;

    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private AcademicSessionService academicSessionService;

    private Program program;

    @BeforeEach
    void setUp() {
        program = Program.builder().id(1L).name("M.Tech CSE").code("MTCSE").build();
    }

    private AcademicSessionDTO validSessionDTO() {
        AcademicSessionDTO dto = new AcademicSessionDTO();
        dto.setName("2026-27");
        dto.setCode("2026-27");
        dto.setProgramId(1L);
        return dto;
    }

    private AcademicSession buildSession() {
        return AcademicSession.builder()
                .id(1L)
                .name("2026-27")
                .code("2026-27")
                .program(program)
                .build();
    }

    @Test
    void saveSession_duplicate_returns409() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.existsByNameAndProgram("2026-27", program)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.saveSession(validSessionDTO()));
        assertEquals(409, ex.getStatus());
        verify(academicSessionRepository, never()).save(any());
    }

    @Test
    void saveSession_invalidProgram_returns404() {
        when(programRepository.findById(99L)).thenReturn(Optional.empty());
        AcademicSessionDTO dto = validSessionDTO();
        dto.setProgramId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.saveSession(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveSession_missingProgram_returns400() {
        AcademicSessionDTO dto = validSessionDTO();
        dto.setProgramId(null);

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.saveSession(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSession_blankName_returns400() {
        AcademicSessionDTO dto = validSessionDTO();
        dto.setName(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.saveSession(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSession_blankCode_returns400() {
        AcademicSessionDTO dto = validSessionDTO();
        dto.setCode(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.saveSession(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSession_missingCode_returns400() {
        AcademicSessionDTO dto = validSessionDTO();
        dto.setCode(null);

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.saveSession(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSession_valid_returnsDTO() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.existsByNameAndProgram("2026-27", program)).thenReturn(false);
        when(academicSessionRepository.save(any(AcademicSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicSessionDTO result = academicSessionService.saveSession(validSessionDTO());
        assertNotNull(result);
        assertEquals("2026-27", result.getName());
        assertEquals(1L, result.getProgramId());
    }

    @Test
    void saveSession_blankDescription_defaultsToEmptyString() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.existsByNameAndProgram("2026-27", program)).thenReturn(false);
        when(academicSessionRepository.save(any(AcademicSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicSessionDTO result = academicSessionService.saveSession(validSessionDTO());

        ArgumentCaptor<AcademicSession> captor = ArgumentCaptor.forClass(AcademicSession.class);
        verify(academicSessionRepository).save(captor.capture());
        assertEquals("", captor.getValue().getDescription());
        assertEquals(program, captor.getValue().getProgram());
        assertEquals("", result.getDescription());
    }

    @Test
    void saveSession_validDescription_isPreserved() {
        AcademicSessionDTO dto = validSessionDTO();
        dto.setDescription("First academic year");
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.existsByNameAndProgram("2026-27", program)).thenReturn(false);
        when(academicSessionRepository.save(any(AcademicSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicSessionDTO result = academicSessionService.saveSession(dto);

        ArgumentCaptor<AcademicSession> captor = ArgumentCaptor.forClass(AcademicSession.class);
        verify(academicSessionRepository).save(captor.capture());
        assertEquals("First academic year", captor.getValue().getDescription());
        assertEquals("First academic year", result.getDescription());
    }

    @Test
    void updateSession_blankDescription_defaultsToEmptyString() {
        AcademicSession existing = AcademicSession.builder()
                .id(1L)
                .name("2026-27")
                .code("2026-27")
                .description("old")
                .program(program)
                .build();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.save(any(AcademicSession.class))).thenAnswer(inv -> inv.getArgument(0));

        academicSessionService.updateSession(1L, validSessionDTO());

        ArgumentCaptor<AcademicSession> captor = ArgumentCaptor.forClass(AcademicSession.class);
        verify(academicSessionRepository).save(captor.capture());
        assertEquals("", captor.getValue().getDescription());
    }

    @Test
    void getSessionById_missing_returns404() {
        when(academicSessionRepository.findById(5L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.getSessionById(5L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getSessionsByProgram_invalidProgram_returns404() {
        when(programRepository.findById(9L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.getSessionsByProgram(9L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getSessionsByProgram_valid_returnsList() {
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.findByProgram(program)).thenReturn(List.of(buildSession()));

        List<AcademicSessionDTO> result = academicSessionService.getSessionsByProgram(1L);
        assertEquals(1, result.size());
        assertEquals("2026-27", result.get(0).getName());
    }

    @Test
    void deleteSession_missing_returns404() {
        when(academicSessionRepository.findById(3L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.deleteSession(3L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteSession_withSemesters_returns409_notDeleted() {
        AcademicSession session = buildSession();
        Semester semester = Semester.builder().id(1L).name("Semester 1").academicSession(session).build();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(semesterRepository.findByAcademicSession(session)).thenReturn(List.of(semester));

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.deleteSession(1L));
        assertEquals(409, ex.getStatus());
        verify(academicSessionRepository, never()).delete(any());
    }

    @Test
    void deleteSession_withBatches_returns409_notDeleted() {
        AcademicSession session = buildSession();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(semesterRepository.findByAcademicSession(session)).thenReturn(List.of());
        when(batchRepository.findByAcademicSession(session)).thenReturn(List.of(
                new com.dagacs.entity.Batch()));

        AuthException ex = assertThrows(AuthException.class,
                () -> academicSessionService.deleteSession(1L));
        assertEquals(409, ex.getStatus());
        verify(academicSessionRepository, never()).delete(any());
    }

    @Test
    void deleteSession_noDependents_deletes() {
        AcademicSession session = buildSession();
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(semesterRepository.findByAcademicSession(session)).thenReturn(List.of());
        when(batchRepository.findByAcademicSession(session)).thenReturn(List.of());

        academicSessionService.deleteSession(1L);
        verify(academicSessionRepository).delete(session);
    }
}
