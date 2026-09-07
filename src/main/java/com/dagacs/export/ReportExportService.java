package com.dagacs.export;

import com.dagacs.dto.HodLowAttendanceDTO;
import com.dagacs.dto.HodRollupDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.HodReportRepository;
import com.dagacs.repository.HodReportRepository.HodCoverageAggregation;
import com.dagacs.repository.HodReportRepository.HodDailyLectureAggregation;
import com.dagacs.repository.TeacherReportRepository;
import com.dagacs.repository.TeacherReportRepository.TeacherSubjectAggregation;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.security.AuthenticatedTeacherResolver;
import com.dagacs.service.HodAnalyticsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * M7.2 export orchestrator. This is a read-only ADDITIVE layer: it reuses the
 * frozen report/analytics data sources (M7.1 repository queries, M6.2 analytics,
 * JWT resolvers) and never modifies them or invents attendance business rules.
 *
 * <p><b>Full-data guarantee:</b> exports are file-generation operations, so they
 * must contain ALL matching rows for the selected date range regardless of the
 * M7.1 pagination/page-size limits:
 * <ul>
 *   <li>HOD daily-lecture / coverage: the frozen {@link HodReportRepository}
 *       queries are invoked with {@link Pageable#unpaged()} (the M7.1 service
 *       clamps page size to 200, which is irrelevant for a file export).</li>
 *   <li>Teacher subject-wise: the frozen
 *       {@code TeacherReportRepository.findSubjectReportByTeacherAndDateRange}
 *       takes no {@code Pageable}/{@code page}/{@code size} argument and returns a
 *       plain {@code List}, so this path is full-data by contract. It is routed
 *       through this additive M7.2 layer (JWT-resolved teacher identity + reusing
 *       the exact frozen query), preserving the M7.1 authorization and report
 *       semantics without touching frozen files.</li>
 *   <li>HOD monthly / quarterly / low-attendance: the frozen
 *       {@link HodAnalyticsService} already returns full unpaged lists.</li>
 * </ul>
 */
@Service
public class ReportExportService {

    private static final Set<String> SUPPORTED_HOD_TYPES = Set.of(
            "daily-lecture", "monthly", "quarterly", "low-attendance", "coverage");

    private final AuthenticatedHodResolver hodResolver;
    private final HodReportRepository hodReportRepository;
    private final HodAnalyticsService hodAnalyticsService;
    private final AuthenticatedTeacherResolver teacherResolver;
    private final TeacherReportRepository teacherReportRepository;
    private final ExcelReportGenerator excelGenerator;
    private final PdfReportGenerator pdfGenerator;

    public ReportExportService(AuthenticatedHodResolver hodResolver,
                               HodReportRepository hodReportRepository,
                               HodAnalyticsService hodAnalyticsService,
                               AuthenticatedTeacherResolver teacherResolver,
                               TeacherReportRepository teacherReportRepository,
                               ExcelReportGenerator excelGenerator,
                               PdfReportGenerator pdfGenerator) {
        this.hodResolver = hodResolver;
        this.hodReportRepository = hodReportRepository;
        this.hodAnalyticsService = hodAnalyticsService;
        this.teacherResolver = teacherResolver;
        this.teacherReportRepository = teacherReportRepository;
        this.excelGenerator = excelGenerator;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional(readOnly = true)
    public byte[] exportHod(String reportType, ExportFormat format,
                            LocalDate startDate, LocalDate endDate) {
        if (!SUPPORTED_HOD_TYPES.contains(reportType)) {
            // Preserves the frozen semester stance: semester is NOT derivable, so
            // "semester" is rejected here exactly like M6.2 rejects type=semester.
            throw new AuthException("Unsupported report type for export: " + reportType, 400);
        }
        ExportData data = buildHodExportData(reportType, startDate, endDate);
        return generate(data, format);
    }

    @Transactional(readOnly = true)
    public byte[] exportTeacher(ExportFormat format, LocalDate startDate, LocalDate endDate) {
        ExportData data = buildTeacherExportData(startDate, endDate);
        return generate(data, format);
    }

    private byte[] generate(ExportData data, ExportFormat format) {
        return switch (format) {
            case XLSX -> excelGenerator.generate(data);
            case PDF -> pdfGenerator.generate(data);
        };
    }

    private ExportData buildHodExportData(String reportType, LocalDate startDate, LocalDate endDate) {
        Teacher hod = hodResolver.resolve();
        Long deptId = hod.getDepartment().getId();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);
        String subtitle = "Department: " + hod.getDepartment().getName()
                + " | " + dateRangeText(startDate, endDate);

        return switch (reportType) {
            case "daily-lecture" -> buildDailyLecture(deptId, start, end, subtitle);
            case "coverage" -> buildCoverage(deptId, start, end, subtitle);
            case "monthly" -> buildRollup("monthly", startDate, endDate, subtitle);
            case "quarterly" -> buildRollup("quarterly", startDate, endDate, subtitle);
            case "low-attendance" -> buildLowAttendance(startDate, endDate, subtitle);
            default -> throw new AuthException("Unsupported report type for export: " + reportType, 400);
        };
    }

    private ExportData buildDailyLecture(Long deptId, String start, String end, String subtitle) {
        Page<HodDailyLectureAggregation> page = hodReportRepository
                .findDailyLectureByDepartmentAndDateRange(deptId, start, end, Pageable.unpaged());
        List<String> headers = List.of("Date", "Lecture Period", "Subject Code", "Subject Name",
                "Section Code", "Section Name", "Present", "Total Recorded", "Percentage (%)");
        List<List<Object>> rows = new ArrayList<>();
        for (HodDailyLectureAggregation row : page.getContent()) {
            rows.add(List.of(
                    row.getDate(),
                    row.getLecturePeriod(),
                    row.getSubjectCode(),
                    row.getSubjectName(),
                    row.getSectionCode(),
                    row.getSectionName(),
                    row.getPresentCount(),
                    row.getTotalRecorded(),
                    computePercentage(row.getPresentCount(), row.getTotalRecorded())));
        }
        return new ExportData("Daily-Lecture Attendance Report", subtitle,
                "Daily Lecture", headers, rows);
    }

    private ExportData buildCoverage(Long deptId, String start, String end, String subtitle) {
        Page<HodCoverageAggregation> page = hodReportRepository
                .findCoverageByDepartmentAndDateRange(deptId, start, end, Pageable.unpaged());
        List<String> headers = List.of("Subject Code", "Subject Name", "Section Code",
                "Section Name", "Recorded Date Count", "Session Count");
        List<List<Object>> rows = new ArrayList<>();
        for (HodCoverageAggregation row : page.getContent()) {
            rows.add(List.of(
                    row.getSubjectCode(),
                    row.getSubjectName(),
                    row.getSectionCode(),
                    row.getSectionName(),
                    row.getRecordedDateCount(),
                    row.getSessionCount()));
        }
        return new ExportData("Recording-Coverage Report", subtitle, "Coverage", headers, rows);
    }

    private ExportData buildRollup(String type, LocalDate startDate, LocalDate endDate, String subtitle) {
        List<HodRollupDTO> rollups = hodAnalyticsService.getRollups(type, startDate, endDate);
        List<String> headers = List.of("Period", "Present", "Total Recorded", "Percentage (%)");
        List<List<Object>> rows = new ArrayList<>();
        for (HodRollupDTO rollup : rollups) {
            rows.add(List.of(rollup.getPeriod(), rollup.getPresentCount(),
                    rollup.getTotalRecordedCount(), rollup.getPercentage()));
        }
        String title = "monthly".equals(type)
                ? "Monthly Attendance Rollup"
                : "Quarterly Attendance Rollup";
        return new ExportData(title, subtitle,
                "monthly".equals(type) ? "Monthly" : "Quarterly", headers, rows);
    }

    private ExportData buildLowAttendance(LocalDate startDate, LocalDate endDate, String subtitle) {
        List<HodLowAttendanceDTO> low = hodAnalyticsService.getLowAttendance(startDate, endDate);
        List<String> headers = List.of("Roll Number", "Student Name", "Section",
                "Present", "Total Recorded", "Percentage (%)");
        List<List<Object>> rows = new ArrayList<>();
        for (HodLowAttendanceDTO row : low) {
            rows.add(List.of(row.getRollNumber(), row.getStudentName(), row.getSectionName(),
                    row.getPresentCount(), row.getTotalRecordedCount(), row.getPercentage()));
        }
        return new ExportData("Low-Attendance Report", subtitle, "Low Attendance", headers, rows);
    }

    private ExportData buildTeacherExportData(LocalDate startDate, LocalDate endDate) {
        Teacher teacher = teacherResolver.resolve();
        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);
        String subtitle = "Teacher: " + teacher.getFullName() + " | " + dateRangeText(startDate, endDate);

        List<TeacherSubjectAggregation> reportRows = teacherReportRepository
                .findSubjectReportByTeacherAndDateRange(teacher.getId(), start, end);
        List<String> headers = List.of("Subject Code", "Subject Name", "Section Code",
                "Section Name", "Present", "Total Recorded", "Percentage (%)");
        List<List<Object>> rows = new ArrayList<>();
        for (TeacherSubjectAggregation row : reportRows) {
            rows.add(List.of(row.getSubjectCode(), row.getSubjectName(),
                    row.getSectionCode(), row.getSectionName(),
                    row.getPresentCount(), row.getTotalRecorded(),
                    computePercentage(row.getPresentCount(), row.getTotalRecorded())));
        }
        return new ExportData("Teacher Subject-Wise Attendance Report", subtitle,
                "Subject-wise", headers, rows);
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String dateRangeText(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return startDate + " to " + endDate;
        }
        if (startDate != null) {
            return "from " + startDate;
        }
        if (endDate != null) {
            return "until " + endDate;
        }
        return "all dates";
    }

    /**
     * Private duplication of the M4 percentage semantics (present/total*100,
     * null when total is 0). M4 code is never modified and never extracted.
     */
    private static Double computePercentage(long presentCount, long totalRecorded) {
        if (totalRecorded <= 0) {
            return null;
        }
        return (double) presentCount / totalRecorded * 100.0;
    }
}