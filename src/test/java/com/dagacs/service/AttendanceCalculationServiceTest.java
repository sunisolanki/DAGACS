package com.dagacs.service;

import com.dagacs.dto.AttendancePercentageDTO;
import com.dagacs.dto.CalendarAttendanceDTO;
import com.dagacs.dto.SubjectAttendanceDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Student;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.repository.AttendanceRecordAggregationRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceCalculationServiceTest {

    @Mock
    private AuthenticatedStudentResolver studentResolver;

    @Mock
    private AttendanceRecordAggregationRepository aggregationRepository;

    @InjectMocks
    private AttendanceCalculationService calculationService;

    private Student student;
    private Subject subject;

    @BeforeEach
    void setUp() {
        student = new Student();
        student.setId(1L);
        student.setEmail("student@dagacs.local");

        subject = new Subject();
        subject.setId(5L);
        subject.setCode("CS101");
        subject.setName("Database Systems");
    }

    private AttendanceRecord record(Long id, boolean isPresent) {
        AttendanceRecord r = mock(AttendanceRecord.class);
        AttendanceSession session = mock(AttendanceSession.class);
        when(session.getId()).thenReturn(10L);
        when(r.getId()).thenReturn(id);
        when(r.getStudent()).thenReturn(student);
        when(r.getSubject()).thenReturn(subject);
        when(r.getStatus()).thenReturn(isPresent ? "PRESENT" : "ABSENT");
        when(r.getIsPresent()).thenReturn(isPresent);
        when(r.getSession()).thenReturn(session);
        when(r.getLecturePeriod()).thenReturn("1st");
        when(r.getDate()).thenReturn("2026-09-04");
        return r;
    }

    @Test
    void subjectCalculation_10Present_returns100() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(10L);
        when(aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L)).thenReturn(10L);

        AttendancePercentageDTO result = calculationService.getSubjectCalculation(5L);

        assertEquals(10L, result.getPresentCount());
        assertEquals(10L, result.getTotalRecordedCount());
        assertEquals(100.0, result.getPercentage());
    }

    @Test
    void subjectCalculation_10Absent_returns0() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(10L);
        when(aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L)).thenReturn(0L);

        AttendancePercentageDTO result = calculationService.getSubjectCalculation(5L);

        assertEquals(0L, result.getPresentCount());
        assertEquals(10L, result.getTotalRecordedCount());
        assertEquals(0.0, result.getPercentage());
    }

    @Test
    void subjectCalculation_7PresentAnd3Absent_returns70() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(10L);
        when(aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L)).thenReturn(7L);

        AttendancePercentageDTO result = calculationService.getSubjectCalculation(5L);

        assertEquals(7L, result.getPresentCount());
        assertEquals(10L, result.getTotalRecordedCount());
        assertEquals(70.0, result.getPercentage());
    }

    @Test
    void subjectCalculation_zeroRecords_returnsNullPercentage() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(0L);
        when(aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L)).thenReturn(0L);

        AttendancePercentageDTO result = calculationService.getSubjectCalculation(5L);

        assertEquals(0L, result.getPresentCount());
        assertEquals(0L, result.getTotalRecordedCount());
        assertNull(result.getPercentage());
    }

    @Test
    void overallCalculation_usesCountsNotAverage() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentId(1L)).thenReturn(20L);
        when(aggregationRepository.countByStudentIdAndIsPresentTrue(1L)).thenReturn(11L);

        AttendancePercentageDTO result = calculationService.getOverallCalculation();

        assertEquals(11L, result.getPresentCount());
        assertEquals(20L, result.getTotalRecordedCount());
        assertEquals(55.0, result.getPercentage(), 0.0001);
    }

    @Test
    void overallCalculation_zeroRecords_returnsNullPercentage() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentId(1L)).thenReturn(0L);
        when(aggregationRepository.countByStudentIdAndIsPresentTrue(1L)).thenReturn(0L);

        AttendancePercentageDTO result = calculationService.getOverallCalculation();

        assertEquals(0L, result.getPresentCount());
        assertEquals(0L, result.getTotalRecordedCount());
        assertNull(result.getPercentage());
    }

    @Test
    void subjectCalculation_usesAuthenticatedStudentOnly() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(5L);
        when(aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L)).thenReturn(3L);

        calculationService.getSubjectCalculation(5L);

        verify(aggregationRepository).countByStudentIdAndSubjectId(1L, 5L);
        verify(aggregationRepository).countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L);
    }

    @Test
    void subjectCalculation_studentCannotAccessAnotherStudentsData() {
        Student otherStudent = new Student();
        otherStudent.setId(2L);
        otherStudent.setEmail("other@dagacs.local");

        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(5L);

        calculationService.getSubjectCalculation(5L);

        verify(aggregationRepository).countByStudentIdAndSubjectId(1L, 5L);
        verify(aggregationRepository, never()).countByStudentIdAndSubjectId(2L, 5L);
    }

    @Test
    void absentContributesToDenominatorOnly() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countByStudentIdAndSubjectId(1L, 5L)).thenReturn(10L);
        when(aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L)).thenReturn(3L);

        AttendancePercentageDTO result = calculationService.getSubjectCalculation(5L);

        assertEquals(3L, result.getPresentCount());
        assertEquals(10L, result.getTotalRecordedCount());
        assertEquals(30.0, result.getPercentage());
        verify(aggregationRepository).countByStudentIdAndSubjectId(1L, 5L);
        verify(aggregationRepository).countByStudentIdAndSubjectIdAndIsPresentTrue(1L, 5L);
    }

    @Test
    void overallCalculation_withDateRange_usesDateAwareCounts() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countRecordedByStudentAndDateRange(1L, "2026-01-01", "2026-01-31")).thenReturn(10L);
        when(aggregationRepository.countPresentByStudentAndDateRange(1L, "2026-01-01", "2026-01-31")).thenReturn(7L);

        AttendancePercentageDTO result =
                calculationService.getOverallCalculation(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(7L, result.getPresentCount());
        assertEquals(10L, result.getTotalRecordedCount());
        assertEquals(70.0, result.getPercentage());
        verify(aggregationRepository).countRecordedByStudentAndDateRange(1L, "2026-01-01", "2026-01-31");
        verify(aggregationRepository).countPresentByStudentAndDateRange(1L, "2026-01-01", "2026-01-31");
    }

    @Test
    void overallCalculation_withStartDateOnly_passesNullEndDate() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countRecordedByStudentAndDateRange(1L, "2026-01-01", null)).thenReturn(5L);
        when(aggregationRepository.countPresentByStudentAndDateRange(1L, "2026-01-01", null)).thenReturn(5L);

        AttendancePercentageDTO result =
                calculationService.getOverallCalculation(LocalDate.of(2026, 1, 1), null);

        assertEquals(100.0, result.getPercentage());
        verify(aggregationRepository).countRecordedByStudentAndDateRange(1L, "2026-01-01", null);
        verify(aggregationRepository).countPresentByStudentAndDateRange(1L, "2026-01-01", null);
    }

    @Test
    void overallCalculation_withEndDateOnly_passesNullStartDate() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countRecordedByStudentAndDateRange(1L, null, "2026-01-31")).thenReturn(4L);
        when(aggregationRepository.countPresentByStudentAndDateRange(1L, null, "2026-01-31")).thenReturn(3L);

        AttendancePercentageDTO result =
                calculationService.getOverallCalculation(null, LocalDate.of(2026, 1, 31));

        assertEquals(3L, result.getPresentCount());
        assertEquals(4L, result.getTotalRecordedCount());
        assertEquals(75.0, result.getPercentage());
        verify(aggregationRepository).countRecordedByStudentAndDateRange(1L, null, "2026-01-31");
        verify(aggregationRepository).countPresentByStudentAndDateRange(1L, null, "2026-01-31");
    }

    @Test
    void overallCalculation_withDateRange_emptyRange_returnsNullPercentage() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countRecordedByStudentAndDateRange(1L, "2026-01-01", "2026-01-31")).thenReturn(0L);
        when(aggregationRepository.countPresentByStudentAndDateRange(1L, "2026-01-01", "2026-01-31")).thenReturn(0L);

        AttendancePercentageDTO result =
                calculationService.getOverallCalculation(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(0L, result.getPresentCount());
        assertEquals(0L, result.getTotalRecordedCount());
        assertNull(result.getPercentage());
    }

    @Test
    void subjectCalculation_withDateRange_usesDateAwareSubjectCounts() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countRecordedByStudentAndSubjectAndDateRange(1L, 5L, "2026-01-01", "2026-01-31")).thenReturn(10L);
        when(aggregationRepository.countPresentByStudentAndSubjectAndDateRange(1L, 5L, "2026-01-01", "2026-01-31")).thenReturn(9L);

        AttendancePercentageDTO result =
                calculationService.getSubjectCalculation(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(9L, result.getPresentCount());
        assertEquals(10L, result.getTotalRecordedCount());
        assertEquals(90.0, result.getPercentage());
        verify(aggregationRepository).countRecordedByStudentAndSubjectAndDateRange(1L, 5L, "2026-01-01", "2026-01-31");
        verify(aggregationRepository).countPresentByStudentAndSubjectAndDateRange(1L, 5L, "2026-01-01", "2026-01-31");
    }

    @Test
    void overallCalculation_startAfterEnd_throwsInvalidDateRange() {
        assertThrows(InvalidDateRangeException.class,
                () -> calculationService.getOverallCalculation(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        verify(aggregationRepository, never()).countRecordedByStudentAndDateRange(any(), any(), any());
    }

    @Test
    void subjectCalculation_startAfterEnd_throwsInvalidDateRange() {
        assertThrows(InvalidDateRangeException.class,
                () -> calculationService.getSubjectCalculation(5L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        verify(aggregationRepository, never()).countRecordedByStudentAndSubjectAndDateRange(any(), any(), any(), any());
    }

    @Test
    void overallCalculation_withNullNull_usesDateAwareCountsWithNulls() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countRecordedByStudentAndDateRange(1L, null, null)).thenReturn(2L);
        when(aggregationRepository.countPresentByStudentAndDateRange(1L, null, null)).thenReturn(1L);

        AttendancePercentageDTO result = calculationService.getOverallCalculation(null, null);

        assertEquals(1L, result.getPresentCount());
        assertEquals(2L, result.getTotalRecordedCount());
        assertEquals(50.0, result.getPercentage());
    }

    @Test
    void getSubjectSummaries_returnsAllSubjects() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.summarizeByStudentAndSubjectGrouped(1L, null, null))
                .thenReturn(Arrays.asList(
                        new Object[]{5L, "Database Systems", 8L, 10L},
                        new Object[]{6L, "Operating Systems", 7L, 10L}
                ));

        List<SubjectAttendanceDTO> result = calculationService.getSubjectSummaries(null, null);

        assertEquals(2, result.size());
        assertEquals(5L, result.get(0).getSubjectId());
        assertEquals("Database Systems", result.get(0).getSubjectName());
        assertEquals(8L, result.get(0).getPresentCount());
        assertEquals(10L, result.get(0).getTotalRecordedCount());
        assertEquals(80.0, result.get(0).getPercentage());
        assertEquals(6L, result.get(1).getSubjectId());
        assertEquals(70.0, result.get(1).getPercentage());
    }

    @Test
    void getSubjectSummaries_zeroRecords_returnsEmptyList() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.summarizeByStudentAndSubjectGrouped(1L, null, null))
                .thenReturn(Collections.emptyList());

        List<SubjectAttendanceDTO> result = calculationService.getSubjectSummaries(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getSubjectSummaries_usesAuthenticatedStudentOnly() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.summarizeByStudentAndSubjectGrouped(1L, null, null))
                .thenReturn(Collections.emptyList());

        calculationService.getSubjectSummaries(null, null);

        verify(aggregationRepository).summarizeByStudentAndSubjectGrouped(1L, null, null);
        verify(aggregationRepository, never()).summarizeByStudentAndSubjectGrouped(2L, null, null);
    }

    @Test
    void getSubjectSummaries_subjectNameNull_defaultsToEmptyString() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.summarizeByStudentAndSubjectGrouped(1L, null, null))
                .thenReturn(Collections.singletonList(new Object[]{5L, null, 8L, 10L}));

        List<SubjectAttendanceDTO> result = calculationService.getSubjectSummaries(null, null);

        assertEquals(1, result.size());
        assertEquals("", result.get(0).getSubjectName());
    }

    @Test
    void getCalendarSummary_multipleDates() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, null, null))
                .thenReturn(Arrays.asList(
                        new Object[]{"2026-09-01", 5L, 0L, 5L},
                        new Object[]{"2026-09-04", 3L, 2L, 5L}
                ));

        List<CalendarAttendanceDTO> result = calculationService.getCalendarSummary(null, null);

        assertEquals(2, result.size());
        assertEquals("2026-09-01", result.get(0).getDate());
        assertEquals(5L, result.get(0).getPresentCount());
        assertEquals(0L, result.get(0).getAbsentCount());
        assertEquals(5L, result.get(0).getTotalRecordedCount());
        assertEquals(100.0, result.get(0).getPercentage());
        assertEquals("2026-09-04", result.get(1).getDate());
        assertEquals(3L, result.get(1).getPresentCount());
        assertEquals(2L, result.get(1).getAbsentCount());
        assertEquals(60.0, result.get(1).getPercentage());
    }

    @Test
    void getCalendarSummary_includesAbsentCounts() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, null, null))
                .thenReturn(Collections.singletonList(new Object[]{"2026-09-04", 3L, 2L, 5L}));

        List<CalendarAttendanceDTO> result = calculationService.getCalendarSummary(null, null);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getPresentCount());
        assertEquals(2L, result.get(0).getAbsentCount());
        assertEquals(5L, result.get(0).getTotalRecordedCount());
    }

    @Test
    void getCalendarSummary_dateRangeFilter() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, "2026-09-01", "2026-09-30"))
                .thenReturn(Collections.singletonList(new Object[]{"2026-09-04", 3L, 1L, 4L}));

        List<CalendarAttendanceDTO> result = calculationService.getCalendarSummary(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertEquals(1, result.size());
        assertEquals("2026-09-04", result.get(0).getDate());
        verify(aggregationRepository).countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, "2026-09-01", "2026-09-30");
    }

    @Test
    void getCalendarSummary_zeroRecords_returnsEmptyList() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, null, null))
                .thenReturn(Collections.emptyList());

        List<CalendarAttendanceDTO> result = calculationService.getCalendarSummary(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getCalendarSummary_startAfterEnd_throwsInvalidDateRange() {
        assertThrows(InvalidDateRangeException.class,
                () -> calculationService.getCalendarSummary(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        verify(aggregationRepository, never()).countPresentAbsentTotalByStudentAndDateRangeGrouped(any(), any(), any());
    }

    @Test
    void getCalendarSummary_withNullNull_returnsAll() {
        when(studentResolver.resolve()).thenReturn(student);
        when(aggregationRepository.countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, null, null))
                .thenReturn(Collections.singletonList(new Object[]{"2026-09-04", 3L, 1L, 4L}));

        List<CalendarAttendanceDTO> result = calculationService.getCalendarSummary(null, null);

        assertEquals(1, result.size());
        verify(aggregationRepository).countPresentAbsentTotalByStudentAndDateRangeGrouped(1L, null, null);
    }
}
