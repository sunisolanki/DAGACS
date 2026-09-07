package com.dagacs.service;

import com.dagacs.dto.AttendancePercentageDTO;
import com.dagacs.entity.Student;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.repository.AttendanceRecordAggregationRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
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
