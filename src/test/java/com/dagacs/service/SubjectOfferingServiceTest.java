package com.dagacs.service;

import com.dagacs.dto.SubjectOfferingDTO;
import com.dagacs.dto.SubjectOfferingRequestDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
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
class SubjectOfferingServiceTest {

    @Mock
    private SubjectOfferingRepository subjectOfferingRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SemesterRepository semesterRepository;

    @Mock
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @InjectMocks
    private SubjectOfferingService subjectOfferingService;

    private Subject subject1;
    private Subject subject2;
    private Semester semester5;
    private Semester semester6;

    @BeforeEach
    void setUp() {
        subject1 = Subject.builder().id(1L).code("CS301").name("DBMS").build();
        subject2 = Subject.builder().id(2L).code("CS401").name("Networks").build();
        AcademicSession session = AcademicSession.builder().id(1L).name("2026-27").code("S1").build();
        semester5 = Semester.builder().id(5L).name("Semester 5").code("SEM5")
                .academicSession(session).build();
        semester6 = Semester.builder().id(6L).name("Semester 6").code("SEM6")
                .academicSession(session).build();
    }

    private SubjectOfferingRequestDTO request(Long subjectId, Long semesterId) {
        return SubjectOfferingRequestDTO.builder()
                .subjectId(subjectId)
                .semesterId(semesterId)
                .build();
    }

    private SubjectOffering buildOffering() {
        return SubjectOffering.builder()
                .id(1L)
                .subject(subject1)
                .semester(semester5)
                .build();
    }

    @Test
    void saveSubjectOffering_valid_returnsDTO() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(5L)).thenReturn(Optional.of(semester5));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester5)).thenReturn(false);
        when(subjectOfferingRepository.save(any(SubjectOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectOfferingDTO result = subjectOfferingService.saveSubjectOffering(request(1L, 5L));
        assertNotNull(result);
        assertEquals(1L, result.getSubjectId());
        assertEquals("CS301", result.getSubject().getCode());
        assertEquals(5L, result.getSemesterId());
        assertEquals(1L, result.getSemester().getAcademicSessionId());
        assertEquals("2026-27", result.getSemester().getAcademicSession().getName());
    }

    @Test
    void saveSubjectOffering_missingSubject_returns404() {
        when(subjectRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.saveSubjectOffering(request(99L, 5L)));
        assertEquals(404, ex.getStatus());
        verify(subjectOfferingRepository, never()).save(any());
    }

    @Test
    void saveSubjectOffering_missingSemester_returns404() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.saveSubjectOffering(request(1L, 99L)));
        assertEquals(404, ex.getStatus());
        verify(subjectOfferingRepository, never()).save(any());
    }

    @Test
    void saveSubjectOffering_missingSubjectId_returns400() {
        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.saveSubjectOffering(request(null, 5L)));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSubjectOffering_missingSemesterId_returns400() {
        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.saveSubjectOffering(request(1L, null)));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void saveSubjectOffering_duplicateSameSemester_returns409() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(5L)).thenReturn(Optional.of(semester5));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester5)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.saveSubjectOffering(request(1L, 5L)));
        assertEquals(409, ex.getStatus());
        verify(subjectOfferingRepository, never()).save(any());
    }

    @Test
    void saveSubjectOffering_sameSubjectDifferentSemester_succeeds() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(5L)).thenReturn(Optional.of(semester5));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester5)).thenReturn(false);
        when(subjectOfferingRepository.save(any(SubjectOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        subjectOfferingService.saveSubjectOffering(request(1L, 5L));

        when(semesterRepository.findById(6L)).thenReturn(Optional.of(semester6));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester6)).thenReturn(false);

        SubjectOfferingDTO second = subjectOfferingService.saveSubjectOffering(request(1L, 6L));
        assertEquals(6L, second.getSemesterId());
        assertEquals(1L, second.getSubjectId());
        verify(subjectOfferingRepository, times(2)).save(any(SubjectOffering.class));
    }

    @Test
    void getAllSubjectOfferings_returnsList() {
        when(subjectOfferingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(buildOffering()));

        List<SubjectOfferingDTO> result = subjectOfferingService.getAllSubjectOfferings();
        assertEquals(1, result.size());
        assertEquals("CS301", result.get(0).getSubject().getCode());
        assertEquals("2026-27", result.get(0).getSemester().getAcademicSession().getName());
    }

    @Test
    void getSubjectOfferingById_valid_returnsDTO() {
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(buildOffering()));

        SubjectOfferingDTO result = subjectOfferingService.getSubjectOfferingById(1L);
        assertEquals(5L, result.getSemesterId());
        assertEquals("Semester 5", result.getSemester().getName());
    }

    @Test
    void getSubjectOfferingById_missing_returns404() {
        when(subjectOfferingRepository.findById(9L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.getSubjectOfferingById(9L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void updateSubjectOffering_missing_returns404() {
        when(subjectOfferingRepository.findById(9L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.updateSubjectOffering(9L, request(1L, 5L)));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void updateSubjectOffering_duplicateAfterMove_returns409() {
        SubjectOffering offering = buildOffering();
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(6L)).thenReturn(Optional.of(semester6));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester6)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.updateSubjectOffering(1L, request(1L, 6L)));
        assertEquals(409, ex.getStatus());
        verify(subjectOfferingRepository, never()).save(any());
    }

    @Test
    void updateSubjectOffering_valid_movesSemester() {
        SubjectOffering offering = buildOffering();
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(6L)).thenReturn(Optional.of(semester6));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester6)).thenReturn(false);
        when(assignmentRepository.existsBySubjectOfferingId(1L)).thenReturn(false);
        when(subjectOfferingRepository.save(any(SubjectOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectOfferingDTO result = subjectOfferingService.updateSubjectOffering(1L, request(1L, 6L));
        assertEquals(6L, result.getSemesterId());
        assertEquals(semester6, offering.getSemester());
    }

    @Test
    void updateSubjectOffering_semesterChange_withTeacherAssignment_returns409_noMutation() {
        SubjectOffering offering = buildOffering();
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(6L)).thenReturn(Optional.of(semester6));
        when(subjectOfferingRepository.existsBySubjectAndSemester(subject1, semester6)).thenReturn(false);
        when(assignmentRepository.existsBySubjectOfferingId(1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.updateSubjectOffering(1L, request(1L, 6L)));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot move subject offering to a different semester while teacher assignments exist",
                ex.getMessage());
        verify(subjectOfferingRepository, never()).save(any());
    }

    @Test
    void updateSubjectOffering_unchangedSemester_skipsReassignmentGuard() {
        SubjectOffering offering = buildOffering();
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject1));
        when(semesterRepository.findById(5L)).thenReturn(Optional.of(semester5));
        when(subjectOfferingRepository.save(any(SubjectOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        SubjectOfferingDTO result = subjectOfferingService.updateSubjectOffering(1L, request(1L, 5L));
        assertEquals(5L, result.getSemesterId());
        verify(assignmentRepository, never()).existsBySubjectOfferingId(anyLong());
    }

    @Test
    void deleteSubjectOffering_missing_returns404() {
        when(subjectOfferingRepository.findById(3L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.deleteSubjectOffering(3L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteSubjectOffering_valid_deletes() {
        SubjectOffering offering = buildOffering();
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(assignmentRepository.existsBySubjectOfferingId(1L)).thenReturn(false);

        subjectOfferingService.deleteSubjectOffering(1L);
        verify(subjectOfferingRepository).delete(offering);
    }

    @Test
    void deleteSubjectOffering_hasAssignments_returns409() {
        SubjectOffering offering = buildOffering();
        when(subjectOfferingRepository.findById(1L)).thenReturn(Optional.of(offering));
        when(assignmentRepository.existsBySubjectOfferingId(1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> subjectOfferingService.deleteSubjectOffering(1L));
        assertEquals(409, ex.getStatus());
        verify(subjectOfferingRepository, never()).delete(any());
    }
}