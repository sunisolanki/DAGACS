package com.dagacs.service;

import com.dagacs.dto.HodCoverageReportDTO;
import com.dagacs.dto.HodDailyLectureReportDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.HodReportRepository;
import com.dagacs.security.AuthenticatedHodResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Read-only HOD report feeds (M7.1): daily-lecture and recording-coverage.
 * Department scope is always derived from the JWT identity via
 * {@link AuthenticatedHodResolver} — no department/section/subject/student ID is
 * ever accepted from a request. Both feeds are record-backed (see
 * {@link HodReportRepository}). Percentage uses the frozen M4 semantics (private
 * copy; M4 code is never modified).
 */
@Service
public class HodReportService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuthenticatedHodResolver hodResolver;
    private final HodReportRepository reportRepository;

    public HodReportService(AuthenticatedHodResolver hodResolver,
                            HodReportRepository reportRepository) {
        this.hodResolver = hodResolver;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public Page<HodDailyLectureReportDTO> getDailyLecture(int page, int size,
                                                          LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);
        Pageable pageable = pageRequest(page, size);

        return reportRepository.findDailyLectureByDepartmentAndDateRange(deptId, start, end, pageable)
                .map(row -> HodDailyLectureReportDTO.builder()
                        .date(row.getDate())
                        .lecturePeriod(row.getLecturePeriod())
                        .subjectCode(row.getSubjectCode())
                        .subjectName(row.getSubjectName())
                        .sectionCode(row.getSectionCode())
                        .sectionName(row.getSectionName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<HodCoverageReportDTO> getCoverage(int page, int size,
                                                  LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);
        Pageable pageable = pageRequest(page, size);

        return reportRepository.findCoverageByDepartmentAndDateRange(deptId, start, end, pageable)
                .map(row -> HodCoverageReportDTO.builder()
                        .subjectCode(row.getSubjectCode())
                        .subjectName(row.getSubjectName())
                        .sectionCode(row.getSectionCode())
                        .sectionName(row.getSectionName())
                        .recordedDateCount(row.getRecordedDateCount())
                        .sessionCount(row.getSessionCount())
                        .build());
    }

    private Long resolveDepartmentId() {
        Teacher hod = hodResolver.resolve();
        return hod.getDepartment().getId();
    }

    private static Pageable pageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        return PageRequest.of(safePage, safeSize);
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