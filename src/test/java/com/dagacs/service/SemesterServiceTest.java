package com.dagacs.service;

import com.dagacs.dto.SemesterDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Semester;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.SubjectOfferingRepository;
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
class SemesterServiceTest {

    @Mock
    private SemesterRepository semesterRepository;

    @Mock
    private AcademicSessionRepository academicSessionRepository;

    @Mock
    private SubjectOfferingRepository subjectOfferingRepository;

    @InjectMocks
    private SemesterService semesterService;

    private AcademicSession academicSession;

    @BeforeEach
    void setUp() {
        academicSession = AcademicSession.builder().id(1L).name("2026-27").code("2026-27").build();
    }

    private SemesterDTO validSemesterDTO() {
        SemesterDTO dto = new SemesterDTO();
        dto.setName("Semester 1");
        dto.setCode("SEM1");
        dto.setYear(1);
        dto.setAcademicSessionId(1L);
        return dto;
    }

    private Semester buildSemester() {
        return Semester.builder()
                .id(1L)
                .name("Semester 1")
                .code("SEM1")
                .year(1)
                .academicSession(academicSession)
                .build();
    }

    @Test
    void saveSemester_duplicateWithinSession_returns409() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(semesterRepository.existsByNameAndAcademicSession("Semester 1", academicSession)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.saveSemester(validSemesterDTO()));
        assertEquals(409, ex.getStatus());
        verify(semesterRepository, never()).save(any());
    }

    @Test
    void saveSemester_invalidAcademicSession_returns404() {
        when(academicSessionRepository.findById(99L)).thenReturn(Optional.empty());
        SemesterDTO dto = validSemesterDTO();
        dto.setAcademicSessionId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.saveSemester(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveSemester_missingAcademicSession_returns400() {
        SemesterDTO dto = validSemesterDTO();
        dto.setAcademicSessionId(null);

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.saveSemester(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSemester_blankName_returns400() {
        SemesterDTO dto = validSemesterDTO();
        dto.setName(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.saveSemester(dto));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSemester_valid_returnsDTO() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(semesterRepository.existsByNameAndAcademicSession("Semester 1", academicSession)).thenReturn(false);
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        SemesterDTO result = semesterService.saveSemester(validSemesterDTO());
        assertNotNull(result);
        assertEquals("Semester 1", result.getName());
        assertEquals(1L, result.getAcademicSessionId());
    }

    @Test
    void getSemesterById_missing_returns404() {
        when(semesterRepository.findById(5L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.getSemesterById(5L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getSemestersBySession_invalidSession_returns404() {
        when(academicSessionRepository.findById(9L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.getSemestersBySession(9L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getSemestersBySession_valid_returnsList() {
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(semesterRepository.findByAcademicSession(academicSession)).thenReturn(List.of(buildSemester()));

        List<SemesterDTO> result = semesterService.getSemestersBySession(1L);
        assertEquals(1, result.size());
        assertEquals("Semester 1", result.get(0).getName());
    }

    @Test
    void updateSemester_differentSessionWithOfferings_returns409_notSaved() {
        Semester semester = buildSemester();
        AcademicSession other = AcademicSession.builder().id(2L).name("2027-28").code("2027-28").build();
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(semester));
        when(academicSessionRepository.findById(2L)).thenReturn(Optional.of(other));
        when(subjectOfferingRepository.existsBySemesterId(1L)).thenReturn(true);

        SemesterDTO dto = validSemesterDTO();
        dto.setAcademicSessionId(2L);

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.updateSemester(1L, dto));
        assertEquals(409, ex.getStatus());
        verify(semesterRepository, never()).save(any());
        assertEquals(academicSession, semester.getAcademicSession());
    }

    @Test
    void updateSemester_sameSessionWithOfferings_allowed() {
        Semester semester = buildSemester();
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(semester));
        when(academicSessionRepository.findById(1L)).thenReturn(Optional.of(academicSession));
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        SemesterDTO dto = validSemesterDTO();

        SemesterDTO result = semesterService.updateSemester(1L, dto);

        assertEquals(1L, result.getAcademicSessionId());
        verify(semesterRepository).save(any(Semester.class));
        verify(subjectOfferingRepository, never()).existsBySemesterId(any());
    }

    @Test
    void updateSemester_differentSessionNoOfferings_allowed() {
        Semester semester = buildSemester();
        AcademicSession other = AcademicSession.builder().id(2L).name("2027-28").code("2027-28").build();
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(semester));
        when(academicSessionRepository.findById(2L)).thenReturn(Optional.of(other));
        when(subjectOfferingRepository.existsBySemesterId(1L)).thenReturn(false);
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        SemesterDTO dto = validSemesterDTO();
        dto.setAcademicSessionId(2L);

        SemesterDTO result = semesterService.updateSemester(1L, dto);

        assertEquals(2L, result.getAcademicSessionId());
        assertEquals(other, semester.getAcademicSession());
        verify(semesterRepository).save(any(Semester.class));
    }

    @Test
    void deleteSemester_withOfferings_returns409_notDeleted() {
        Semester semester = buildSemester();
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(semester));
        when(subjectOfferingRepository.existsBySemesterId(1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.deleteSemester(1L));
        assertEquals(409, ex.getStatus());
        verify(semesterRepository, never()).delete(any());
    }

    @Test
    void deleteSemester_missing_returns404() {
        when(semesterRepository.findById(3L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> semesterService.deleteSemester(3L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteSemester_valid_deletes() {
        Semester semester = buildSemester();
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(semester));

        semesterService.deleteSemester(1L);
        verify(semesterRepository).delete(semester);
    }
}
