package com.dagacs.service;

import com.dagacs.dto.TeacherSubjectReportDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.TeacherReportRepository;
import com.dagacs.security.AuthenticatedTeacherResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only teacher subject-wise attendance report (M7.1). Self-scope is always
 * derived from the JWT identity via {@link AuthenticatedTeacherResolver}; the
 * report is restricted to the teacher's own recorded sessions and own teaching
 * assignments (see {@link TeacherReportRepository}). Percentage uses the frozen
 * M4 semantics (private copy; M4 code is never modified).
 */
@Service
public class TeacherReportService {

    private final AuthenticatedTeacherResolver teacherResolver;
    private final TeacherReportRepository reportRepository;

    public TeacherReportService(AuthenticatedTeacherResolver teacherResolver,
                                TeacherReportRepository reportRepository) {
        this.teacherResolver = teacherResolver;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public List<TeacherSubjectReportDTO> getSubjectReport(LocalDate startDate, LocalDate endDate) {
        Teacher teacher = teacherResolver.resolve();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        List<TeacherSubjectReportDTO> sectionRows = reportRepository
                .findSubjectReportByTeacherAndDateRange(teacher.getId(), start, end).stream()
                .map(row -> TeacherSubjectReportDTO.builder()
                        .subjectId(row.getSubjectId())
                        .subjectCode(row.getSubjectCode())
                        .subjectName(row.getSubjectName())
                        .sectionId(row.getSectionId())
                        .sectionCode(row.getSectionCode())
                        .sectionName(row.getSectionName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();

        // Phase-2 additive batch twin, appended after the frozen section rows.
        List<TeacherSubjectReportDTO> batchRows = reportRepository
                .findBatchSubjectReportByTeacherAndDateRange(teacher.getId(), start, end).stream()
                .map(row -> TeacherSubjectReportDTO.builder()
                        .subjectId(row.getSubjectId())
                        .subjectCode(row.getSubjectCode())
                        .subjectName(row.getSubjectName())
                        .batchId(row.getBatchId())
                        .batchCode(row.getBatchCode())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();

        List<TeacherSubjectReportDTO> merged = new java.util.ArrayList<>(sectionRows);
        merged.addAll(batchRows);
        return merged;
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /**
     * Private duplication of the M4 percentage semantics — M4 code is not
     * modified and not extracted.
     */
    private static Double computePercentage(long presentCount, long totalRecorded) {
        if (totalRecorded <= 0) {
            return null;
        }
        return (double) presentCount / totalRecorded * 100.0;
    }
}