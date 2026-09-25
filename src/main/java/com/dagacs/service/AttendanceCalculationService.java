package com.dagacs.service;

import com.dagacs.dto.AttendancePercentageDTO;
import com.dagacs.dto.CalendarAttendanceDTO;
import com.dagacs.dto.SubjectAttendanceDTO;
import com.dagacs.entity.Student;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.repository.AttendanceRecordAggregationRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceCalculationService {

    private final AuthenticatedStudentResolver studentResolver;
    private final AttendanceRecordAggregationRepository aggregationRepository;

    public AttendanceCalculationService(AuthenticatedStudentResolver studentResolver,
                                         AttendanceRecordAggregationRepository aggregationRepository) {
        this.studentResolver = studentResolver;
        this.aggregationRepository = aggregationRepository;
    }

    @Transactional(readOnly = true)
    public AttendancePercentageDTO getSubjectCalculation(Long subjectId) {
        Student student = studentResolver.resolve();
        Long studentId = student.getId();

        long totalRecorded = aggregationRepository.countByStudentIdAndSubjectId(studentId, subjectId);
        long presentCount = aggregationRepository.countByStudentIdAndSubjectIdAndIsPresentTrue(studentId, subjectId);

        return buildDTO(presentCount, totalRecorded);
    }

    @Transactional(readOnly = true)
    public AttendancePercentageDTO getOverallCalculation() {
        Student student = studentResolver.resolve();
        Long studentId = student.getId();

        long totalRecorded = aggregationRepository.countByStudentId(studentId);
        long presentCount = aggregationRepository.countByStudentIdAndIsPresentTrue(studentId);

        return buildDTO(presentCount, totalRecorded);
    }

    @Transactional(readOnly = true)
    public AttendancePercentageDTO getSubjectCalculation(Long subjectId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Student student = studentResolver.resolve();
        Long studentId = student.getId();

        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        long totalRecorded = aggregationRepository.countRecordedByStudentAndSubjectAndDateRange(studentId, subjectId, start, end);
        long presentCount = aggregationRepository.countPresentByStudentAndSubjectAndDateRange(studentId, subjectId, start, end);

        return buildDTO(presentCount, totalRecorded);
    }

    @Transactional(readOnly = true)
    public AttendancePercentageDTO getOverallCalculation(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Student student = studentResolver.resolve();
        Long studentId = student.getId();

        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        long totalRecorded = aggregationRepository.countRecordedByStudentAndDateRange(studentId, start, end);
        long presentCount = aggregationRepository.countPresentByStudentAndDateRange(studentId, start, end);

        return buildDTO(presentCount, totalRecorded);
    }

    @Transactional(readOnly = true)
    public List<SubjectAttendanceDTO> getSubjectSummaries(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Student student = studentResolver.resolve();
        Long studentId = student.getId();

        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        List<Object[]> results = aggregationRepository.summarizeByStudentAndSubjectGrouped(studentId, start, end);
        return results.stream().map(this::buildSubjectDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CalendarAttendanceDTO> getCalendarSummary(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Student student = studentResolver.resolve();
        Long studentId = student.getId();

        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        List<Object[]> results = aggregationRepository.countPresentAbsentTotalByStudentAndDateRangeGrouped(studentId, start, end);
        return results.stream().map(this::buildCalendarDTO).collect(Collectors.toList());
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }

    private SubjectAttendanceDTO buildSubjectDTO(Object[] result) {
        Long subjectId = (Long) result[0];
        String subjectName = result[1] != null ? result[1].toString() : "";
        Long presentCount = ((Number) result[2]).longValue();
        Long totalRecordedCount = ((Number) result[3]).longValue();
        Double percentage = null;
        if (totalRecordedCount > 0) {
            percentage = (double) presentCount / totalRecordedCount * 100.0;
        }
        return SubjectAttendanceDTO.builder()
                .subjectId(subjectId)
                .subjectName(subjectName)
                .presentCount(presentCount)
                .totalRecordedCount(totalRecordedCount)
                .percentage(percentage)
                .build();
    }

    private CalendarAttendanceDTO buildCalendarDTO(Object[] result) {
        String date = result[0] != null ? result[0].toString() : "";
        Long presentCount = ((Number) result[1]).longValue();
        Long absentCount = ((Number) result[2]).longValue();
        Long totalRecordedCount = ((Number) result[3]).longValue();
        Double percentage = null;
        if (totalRecordedCount > 0) {
            percentage = (double) presentCount / totalRecordedCount * 100.0;
        }
        return CalendarAttendanceDTO.builder()
                .date(date)
                .presentCount(presentCount)
                .absentCount(absentCount)
                .totalRecordedCount(totalRecordedCount)
                .percentage(percentage)
                .build();
    }

    private AttendancePercentageDTO buildDTO(long presentCount, long totalRecorded) {
        Double percentage = null;
        if (totalRecorded > 0) {
            percentage = (double) presentCount / totalRecorded * 100.0;
        }
        return AttendancePercentageDTO.builder()
                .presentCount(presentCount)
                .totalRecordedCount(totalRecorded)
                .percentage(percentage)
                .build();
    }
}
