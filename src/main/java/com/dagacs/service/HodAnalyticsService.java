package com.dagacs.service;

import com.dagacs.dto.HodAuditLogEntryDTO;
import com.dagacs.dto.HodDashboardDTO;
import com.dagacs.dto.HodLowAttendanceDTO;
import com.dagacs.dto.HodRollupDTO;
import com.dagacs.dto.HodSectionAttendanceDTO;
import com.dagacs.dto.HodStudentAttendanceDTO;
import com.dagacs.dto.HodSubjectAttendanceDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.HodAnalyticsRepository;
import com.dagacs.repository.HodAnalyticsRepository.RollupAggregation;
import com.dagacs.repository.HodAnalyticsRepository.SectionAggregation;
import com.dagacs.repository.HodAnalyticsRepository.SectionStudentCount;
import com.dagacs.repository.HodAnalyticsRepository.StudentAggregation;
import com.dagacs.repository.HodAnalyticsRepository.SubjectAggregation;
import com.dagacs.security.AuthenticatedHodResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only department-scoped analytics for HODs (M6.2).
 * <p>
 * Every method resolves the authenticated HOD and derives the department scope
 * solely from the JWT identity via {@link AuthenticatedHodResolver} — no
 * department/section/subject/student ID is ever accepted from a request.
 * The percentage formula mirrors M4's semantics but is implemented privately
 * here; M4 code is never modified (see the M6.2 plan, section 3).
 * </p>
 */
@Service
public class HodAnalyticsService {

    private final AuthenticatedHodResolver hodResolver;
    private final HodAnalyticsRepository analyticsRepository;

    public HodAnalyticsService(AuthenticatedHodResolver hodResolver,
                               HodAnalyticsRepository analyticsRepository) {
        this.hodResolver = hodResolver;
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional(readOnly = true)
    public HodDashboardDTO getDashboard(LocalDate startDate, LocalDate endDate) {
        Teacher hod = resolveHod();
        Long deptId = hod.getDepartment().getId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        long recorded = analyticsRepository.countRecordedByDepartmentAndDateRange(deptId, start, end)
                + analyticsRepository.countRecordedByDepartmentAndDateRangeBatch(deptId, start, end);
        long present = analyticsRepository.countPresentByDepartmentAndDateRange(deptId, start, end)
                + analyticsRepository.countPresentByDepartmentAndDateRangeBatch(deptId, start, end);

        return HodDashboardDTO.builder()
                .departmentName(hod.getDepartment().getName())
                .departmentCode(hod.getDepartment().getCode())
                .programCount(analyticsRepository.countProgramsByDepartment(deptId))
                .batchCount(analyticsRepository.countBatchesByDepartment(deptId))
                .sectionCount(analyticsRepository.countSectionsByDepartment(deptId))
                .studentCount(analyticsRepository.countStudentsByDepartment(deptId))
                .totalRecordedCount(recorded)
                .presentCount(present)
                .overallPercentage(computePercentage(present, recorded))
                .build();
    }

    @Transactional(readOnly = true)
    public List<HodSectionAttendanceDTO> getSectionAttendance(LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        Map<Long, Long> studentCounts = analyticsRepository.countStudentsBySection(deptId).stream()
                .collect(Collectors.toMap(
                        SectionStudentCount::getSectionId,
                        SectionStudentCount::getStudentCount,
                        (a, b) -> a));

        return analyticsRepository.aggregateAttendanceBySection(deptId, start, end).stream()
                .map(row -> HodSectionAttendanceDTO.builder()
                        .sectionName(row.getSectionName())
                        .sectionCode(row.getSectionCode())
                        .studentCount(studentCounts.getOrDefault(row.getSectionId(), 0L))
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HodSubjectAttendanceDTO> getSubjectAttendance(LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        java.util.List<HodSubjectAttendanceDTO> sectionRows = analyticsRepository
                .aggregateAttendanceBySubject(deptId, start, end).stream()
                .map(row -> HodSubjectAttendanceDTO.builder()
                        .subjectCode(row.getSubjectCode())
                        .subjectName(row.getSubjectName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
        // Phase-2 additive batch twin.
        java.util.List<HodSubjectAttendanceDTO> batchRows = analyticsRepository
                .aggregateAttendanceBySubjectBatch(deptId, start, end).stream()
                .map(row -> HodSubjectAttendanceDTO.builder()
                        .subjectCode(row.getSubjectCode())
                        .subjectName(row.getSubjectName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
        java.util.List<HodSubjectAttendanceDTO> merged = new java.util.ArrayList<>(sectionRows);
        merged.addAll(batchRows);
        return merged;
    }

    @Transactional(readOnly = true)
    public List<HodStudentAttendanceDTO> getStudentAttendance(LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        java.util.List<HodStudentAttendanceDTO> sectionRows = analyticsRepository
                .aggregateAttendanceByStudent(deptId, start, end).stream()
                .map(row -> HodStudentAttendanceDTO.builder()
                        .rollNumber(row.getRollNumber())
                        .studentName(row.getStudentName())
                        .sectionName(row.getSectionName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
        // Phase-2 additive batch twin (section alias holds the batch code).
        java.util.List<HodStudentAttendanceDTO> batchRows = analyticsRepository
                .aggregateAttendanceByStudentBatch(deptId, start, end).stream()
                .map(row -> HodStudentAttendanceDTO.builder()
                        .rollNumber(row.getRollNumber())
                        .studentName(row.getStudentName())
                        .batchName(row.getSectionName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
        java.util.List<HodStudentAttendanceDTO> merged = new java.util.ArrayList<>(sectionRows);
        merged.addAll(batchRows);
        return merged;
    }

    @Transactional(readOnly = true)
    public List<HodLowAttendanceDTO> getLowAttendance(LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        java.util.List<HodLowAttendanceDTO> sectionRows = analyticsRepository
                .aggregateLowAttendanceStudents(deptId, start, end).stream()
                .map(row -> HodLowAttendanceDTO.builder()
                        .rollNumber(row.getRollNumber())
                        .studentName(row.getStudentName())
                        .sectionName(row.getSectionName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
        // Phase-2 additive batch twin (section alias holds the batch code).
        java.util.List<HodLowAttendanceDTO> batchRows = analyticsRepository
                .aggregateLowAttendanceStudentsBatch(deptId, start, end).stream()
                .map(row -> HodLowAttendanceDTO.builder()
                        .rollNumber(row.getRollNumber())
                        .studentName(row.getStudentName())
                        .batchName(row.getSectionName())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
        java.util.List<HodLowAttendanceDTO> merged = new java.util.ArrayList<>(sectionRows);
        merged.addAll(batchRows);
        return merged;
    }

    @Transactional(readOnly = true)
    public List<HodRollupDTO> getRollups(String type, LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        List<RollupAggregation> rows;
        if ("monthly".equalsIgnoreCase(type)) {
            rows = analyticsRepository.aggregateMonthlyRollup(deptId, start, end);
            rows = mergeRollup(rows, analyticsRepository.aggregateMonthlyRollupBatch(deptId, start, end));
        } else if ("quarterly".equalsIgnoreCase(type)) {
            rows = analyticsRepository.aggregateQuarterlyRollup(deptId, start, end);
            rows = mergeRollup(rows, analyticsRepository.aggregateQuarterlyRollupBatch(deptId, start, end));
        } else {
            throw new AuthException("type must be either monthly or quarterly", 400);
        }

        return rows.stream()
                .map(row -> HodRollupDTO.builder()
                        .period(row.getPeriod())
                        .presentCount(row.getPresentCount())
                        .totalRecordedCount(row.getTotalRecorded())
                        .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HodAuditLogEntryDTO> getAuditLogs(LocalDate startDate, LocalDate endDate) {
        Long deptId = resolveDepartmentId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        java.util.List<HodAuditLogEntryDTO> sectionRows = analyticsRepository
                .findAuditLogsByDepartmentAndDateRange(deptId, start, end).stream()
                .map(al -> HodAuditLogEntryDTO.builder()
                        .studentName(al.getStudentName())
                        .rollNo(al.getRollNo())
                        .subjectName(al.getSubjectName())
                        .sectionName(al.getSectionName())
                        .date(al.getDate())
                        .previousStatus(al.getPreviousStatus())
                        .newStatus(al.getNewStatus())
                        .updatedBy(al.getUpdatedBy())
                        .updatedAt(al.getUpdatedAt())
                        .reason(al.getReason())
                        .build())
                .toList();
        // Phase-2 additive batch twin.
        java.util.List<HodAuditLogEntryDTO> batchRows = analyticsRepository
                .findAuditLogsByDepartmentAndDateRangeBatch(deptId, start, end).stream()
                .map(al -> HodAuditLogEntryDTO.builder()
                        .studentName(al.getStudentName())
                        .rollNo(al.getRollNo())
                        .subjectName(al.getSubjectName())
                        .batchName(al.getBatchName())
                        .date(al.getDate())
                        .previousStatus(al.getPreviousStatus())
                        .newStatus(al.getNewStatus())
                        .updatedBy(al.getUpdatedBy())
                        .updatedAt(al.getUpdatedAt())
                        .reason(al.getReason())
                        .build())
                .toList();
        java.util.List<HodAuditLogEntryDTO> merged = new java.util.ArrayList<>(sectionRows);
        merged.addAll(batchRows);
        merged.sort(java.util.Comparator.comparing(HodAuditLogEntryDTO::getUpdatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return merged;
    }

    private Teacher resolveHod() {
        return hodResolver.resolve();
    }

    /**
     * Phase-2 additive merge: concat the frozen (section) rollup rows with the
     * batch twin rows.
     */
    private static List<RollupAggregation> mergeRollup(List<RollupAggregation> sectionRows,
                                                       List<RollupAggregation> batchRows) {
        java.util.List<RollupAggregation> merged = new java.util.ArrayList<>(sectionRows);
        merged.addAll(batchRows);
        return merged;
    }

    private Long resolveDepartmentId() {
        Teacher hod = hodResolver.resolve();
        return hod.getDepartment().getId();
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