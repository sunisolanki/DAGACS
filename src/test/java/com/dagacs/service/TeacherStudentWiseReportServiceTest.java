package com.dagacs.service;

import com.dagacs.dto.StudentWiseReportDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherStudentWiseReportRepository;
import com.dagacs.repository.TeacherStudentWiseReportRepository.StudentWiseRecordAggregation;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.dagacs.security.AuthenticatedTeacherResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherStudentWiseReportServiceTest {

    @Mock
    private AuthenticatedTeacherResolver teacherResolver;

    @Mock
    private TeacherStudentWiseReportRepository reportRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @InjectMocks
    private TeacherStudentWiseReportService service;

    private Teacher teacher;
    private Subject subject;
    private Section section;
    private Batch batch;

    @BeforeEach
    void setUp() {
        teacher = Teacher.builder().id(7L).email("t@dagacs.local").fullName("T").build();
        subject = Subject.builder().id(1L).name("DBMS").build();
        section = Section.builder().id(2L).name("CSE-A").build();
        batch = Batch.builder().id(3L).batchCode("B3").build();
    }

    private StudentWiseRecordAggregation aggregation(Long studentId, Long sessionId,
                                                     String enrollment, String studentName,
                                                     String date, String period,
                                                     boolean present) {
        StudentWiseRecordAggregation r = mock(StudentWiseRecordAggregation.class);
        lenient().when(r.getStudentId()).thenReturn(studentId);
        lenient().when(r.getSessionId()).thenReturn(sessionId);
        lenient().when(r.getEnrollmentNumber()).thenReturn(enrollment);
        lenient().when(r.getStudentName()).thenReturn(studentName);
        lenient().when(r.getDate()).thenReturn(date);
        lenient().when(r.getLecturePeriod()).thenReturn(period);
        lenient().when(r.getIsPresent()).thenReturn(present);
        lenient().when(r.getStatus()).thenReturn(present ? "PRESENT" : "ABSENT");
        lenient().when(r.getSubjectId()).thenReturn(1L);
        lenient().when(r.getSubjectName()).thenReturn("DBMS");
        lenient().when(r.getSectionId()).thenReturn(2L);
        lenient().when(r.getSectionCode()).thenReturn("CSE-A");
        lenient().when(r.getSectionName()).thenReturn("CSE-A");
        lenient().when(r.getBatchId()).thenReturn(null);
        lenient().when(r.getBatchCode()).thenReturn(null);
        return r;
    }

    private void stubSectionContext() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(
                eq(7L), eq(2L), eq(1L))).thenReturn(true);
    }

    private void stubBatchContext() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(batchRepository.findById(3L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findByBatch(batch)).thenReturn(Collections.emptyList());
        when(assignmentRepository.existsByTeacherIdAndBatchIdAndSubjectOfferingSubjectId(
                eq(7L), eq(3L), eq(1L))).thenReturn(true);
    }

    @Test
    void missingSubject_throwsAuthException() {
        assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, null, 2L, null));
        verifyNoInteractions(reportRepository);
    }

    @Test
    void bothSectionAndBatch_throwsAuthException() {
        assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, 2L, 3L));
        verifyNoInteractions(reportRepository);
    }

    @Test
    void neitherSectionNorBatch_throwsAuthException() {
        assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, null, null));
        verifyNoInteractions(reportRepository);
    }

    @Test
    void nonexistentSubject_throwsAuthException404() {
        when(subjectRepository.findById(99L)).thenReturn(Optional.empty());
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 99L, 2L, null));
        assertEquals(404, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void nonexistentSection_throwsAuthException404() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(98L)).thenReturn(Optional.empty());
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, 98L, null));
        assertEquals(404, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void nonexistentBatch_throwsAuthException404() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(batchRepository.findById(97L)).thenReturn(Optional.empty());
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, null, 97L));
        assertEquals(404, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void batchWithSections_throwsAuthException400() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(batchRepository.findById(3L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findByBatch(batch)).thenReturn(List.of(section));
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, null, 3L));
        assertEquals(400, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void sectionNotAssigned_throwsAuthException403() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(
                eq(7L), eq(2L), eq(1L))).thenReturn(false);
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, 2L, null));
        assertEquals(403, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void batchNotAssigned_throwsAuthException403() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(batchRepository.findById(3L)).thenReturn(Optional.of(batch));
        when(sectionRepository.findByBatch(batch)).thenReturn(Collections.emptyList());
        when(assignmentRepository.existsByTeacherIdAndBatchIdAndSubjectOfferingSubjectId(
                eq(7L), eq(3L), eq(1L))).thenReturn(false);
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, null, 3L));
        assertEquals(403, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }

    @Test
    void validSectionContext_twoSessionsSameDate_distinctColumns() {
        stubSectionContext();
        when(teacherResolver.resolve()).thenReturn(teacher);
        StudentWiseRecordAggregation r1 = aggregation(10L, 100L, "ENR-001", "A", "2026-01-10", "LP1", true);
        StudentWiseRecordAggregation r2 = aggregation(11L, 100L, "ENR-002", "B", "2026-01-10", "LP1", false);
        StudentWiseRecordAggregation r3 = aggregation(10L, 101L, "ENR-001", "A", "2026-01-10", "LP2", false);
        StudentWiseRecordAggregation r4 = aggregation(11L, 101L, "ENR-002", "B", "2026-01-10", "LP2", true);
        when(reportRepository.findStudentWiseByTeacherAndDateRange(
                eq(7L), isNull(), isNull(), eq(1L), eq(2L), isNull()))
                .thenReturn(List.of(r1, r2, r3, r4));

        StudentWiseReportDTO dto = service.getStudentWiseReport(null, null, 1L, 2L, null);

        assertEquals(2, dto.getColumns().size());
        assertEquals(100L, dto.getColumns().get(0).getSessionId());
        assertEquals("LP1", dto.getColumns().get(0).getLecturePeriod());
        assertEquals(101L, dto.getColumns().get(1).getSessionId());
        assertEquals("LP2", dto.getColumns().get(1).getLecturePeriod());

        assertEquals(2, dto.getRows().size());
        assertEquals("ENR-001", dto.getRows().get(0).getEnrollmentNumber());
        assertEquals(2, dto.getRows().get(0).getCells().size());
        assertEquals(1, dto.getRows().get(0).getPresentCount());
        assertEquals(2, dto.getRows().get(0).getTotalRecordedCount());
        assertEquals(50.0, dto.getRows().get(0).getPercentage(), 0.001);
        assertEquals("ENR-002", dto.getRows().get(1).getEnrollmentNumber());
        assertEquals(1, dto.getRows().get(1).getPresentCount());
        assertEquals(50.0, dto.getRows().get(1).getPercentage(), 0.001);

        assertEquals("DBMS", dto.getSubjectName());
        assertEquals("CSE-A", dto.getSectionName());
    }

    @Test
    void validSectionContext_dateRangeIsPassedThrough() {
        stubSectionContext();
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(reportRepository.findStudentWiseByTeacherAndDateRange(
                eq(7L), eq("2026-01-01"), eq("2026-01-31"), eq(1L), eq(2L), isNull()))
                .thenReturn(List.of());

        StudentWiseReportDTO dto = service.getStudentWiseReport(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 1L, 2L, null);

        assertEquals(1L, dto.getSubjectId());
        assertEquals("DBMS", dto.getSubjectName());
        assertEquals(2L, dto.getSectionId());
        assertEquals("CSE-A", dto.getSectionName());
        assertTrue(dto.getColumns().isEmpty());
        assertTrue(dto.getRows().isEmpty());
    }

    @Test
    void validBatchContext_emptyRecords_returnsEmptyDtoWithBatchLabels() {
        stubBatchContext();
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(reportRepository.findStudentWiseByTeacherAndDateRange(
                eq(7L), isNull(), isNull(), eq(1L), isNull(), eq(3L)))
                .thenReturn(List.of());

        StudentWiseReportDTO dto = service.getStudentWiseReport(null, null, 1L, null, 3L);

        assertEquals(1L, dto.getSubjectId());
        assertEquals("DBMS", dto.getSubjectName());
        assertEquals(3L, dto.getBatchId());
        assertEquals("B3", dto.getBatchCode());
        assertTrue(dto.getColumns().isEmpty());
        assertTrue(dto.getRows().isEmpty());
    }

    @Test
    void unlinkedTeacher_throwsAuthException401() {
        when(teacherResolver.resolve())
                .thenThrow(new AuthException("No teacher account is linked to the authenticated user", 401));
        AuthException ex = assertThrows(AuthException.class,
                () -> service.getStudentWiseReport(null, null, 1L, 2L, null));
        assertEquals(401, ex.getStatus());
        verifyNoInteractions(reportRepository);
    }
}