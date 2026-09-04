package com.dagacs.service;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Program;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AcademicSessionRepository academicSessionRepository;

    @InjectMocks
    private ProgramService programService;

    private Program program;

    @BeforeEach
    void setUp() {
        program = Program.builder().id(1L).name("M.Tech CSE").code("MTCSE").build();
    }

    @Test
    void deleteProgram_missing_returns404() {
        when(programRepository.existsById(99L)).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class,
                () -> programService.deleteProgram(99L));
        assertEquals(404, ex.getStatus());
        verify(programRepository, never()).deleteById(any());
    }

    @Test
    void deleteProgram_withAcademicSessions_returns409_notDeleted() {
        when(programRepository.existsById(1L)).thenReturn(true);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.findByProgram(program)).thenReturn(List.of(
                AcademicSession.builder().id(1L).name("2026-27").program(program).build()));

        AuthException ex = assertThrows(AuthException.class,
                () -> programService.deleteProgram(1L));
        assertEquals(409, ex.getStatus());
        verify(programRepository, never()).deleteById(any());
    }

    @Test
    void deleteProgram_noSessions_deletes() {
        when(programRepository.existsById(1L)).thenReturn(true);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(academicSessionRepository.findByProgram(program)).thenReturn(List.of());

        programService.deleteProgram(1L);
        verify(programRepository).deleteById(1L);
    }
}
