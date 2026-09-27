package com.dagacs.export;

import com.dagacs.dto.HodLowAttendanceDTO;
import com.dagacs.dto.HodRollupDTO;
import com.dagacs.dto.StudentWiseReportDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.HodReportRepository;
import com.dagacs.repository.HodReportRepository.HodCoverageAggregation;
import com.dagacs.repository.HodReportRepository.HodCoverageBatchAggregation;
import com.dagacs.repository.HodReportRepository.HodDailyLectureAggregation;
import com.dagacs.repository.HodReportRepository.HodDailyLectureBatchAggregation;
import com.dagacs.repository.TeacherReportRepository;
import com.dagacs.repository.TeacherReportRepository.TeacherBatchAggregation;
import com.dagacs.repository.TeacherReportRepository.TeacherSubjectAggregation;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.security.AuthenticatedTeacherResolver;
import com.dagacs.service.HodAnalyticsService;
import com.dagacs.service.TeacherStudentWiseReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final TeacherStudentWiseReportService studentWiseReportService;
    private final ExcelReportGenerator excelGenerator;
    private final PdfReportGenerator pdfGenerator;

    public ReportExportService(AuthenticatedHodResolver hodResolver,
                               HodReportRepository hodReportRepository,
                               HodAnalyticsService hodAnalyticsService,
                               AuthenticatedTeacherResolver teacherResolver,
                               TeacherReportRepository teacherReportRepository,
                               TeacherStudentWiseReportService studentWiseReportService,
                               ExcelReportGenerator excelGenerator,
                               PdfReportGenerator pdfGenerator) {
        this.hodResolver = hodResolver;
        this.hodReportRepository = hodReportRepository;
        this.hodAnalyticsService = hodAnalyticsService;
        this.teacherResolver = teacherResolver;
        this.teacherReportRepository = teacherReportRepository;
        this.studentWiseReportService = studentWiseReportService;
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

    /**
     * Additive student-wise matrix export. Unlike the subject-wise export this
     * matrix describes one class, so the sectionId/batchId XOR selection is
     * required to keep the generated table unambiguous.
     */
    @Transactional(readOnly = true)
    public byte[] exportTeacherStudentWise(ExportFormat format, LocalDate startDate, LocalDate endDate,
                                           Long subjectId, Long sectionId, Long batchId) {
        if (subjectId == null) {
            throw new AuthException("Subject ID is required for the student-wise export", 400);
        }
        if ((sectionId == null) == (batchId == null)) {
            throw new AuthException("Provide exactly one of sectionId or batchId.", 400);
        }
        ExportData data = (format == ExportFormat.PDF)
                ? buildTeacherStudentWiseSummaryData(startDate, endDate,
                        subjectId, sectionId, batchId)
                : buildTeacherStudentWiseRegisterData(startDate, endDate,
                        subjectId, sectionId, batchId);
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
                "Section Code", "Section Name", "Present", "Total Recorded", "Percentage (%)", "Batch Code");
        List<List<Object>> rows = new ArrayList<>();
        for (HodDailyLectureAggregation row : page.getContent()) {
            rows.add(java.util.Arrays.asList(
                    row.getDate(),
                    row.getLecturePeriod(),
                    row.getSubjectCode(),
                    row.getSubjectName(),
                    row.getSectionCode(),
                    row.getSectionName(),
                    row.getPresentCount(),
                    row.getTotalRecorded(),
                    computePercentage(row.getPresentCount(), row.getTotalRecorded()),
                    null));
        }
        // Phase-2 additive batch twin.
        Page<HodDailyLectureBatchAggregation> batchPage = hodReportRepository
                .findDailyLectureByDepartmentAndDateRangeBatch(deptId, start, end, Pageable.unpaged());
        for (HodDailyLectureBatchAggregation row : batchPage.getContent()) {
            rows.add(java.util.Arrays.asList(
                    row.getDate(),
                    row.getLecturePeriod(),
                    row.getSubjectCode(),
                    row.getSubjectName(),
                    null,
                    null,
                    row.getPresentCount(),
                    row.getTotalRecorded(),
                    computePercentage(row.getPresentCount(), row.getTotalRecorded()),
                    row.getBatchCode()));
        }
        return new ExportData("Daily-Lecture Attendance Report", subtitle,
                "Daily Lecture", headers, rows);
    }

    private ExportData buildCoverage(Long deptId, String start, String end, String subtitle) {
        Page<HodCoverageAggregation> page = hodReportRepository
                .findCoverageByDepartmentAndDateRange(deptId, start, end, Pageable.unpaged());
        List<String> headers = List.of("Subject Code", "Subject Name", "Section Code",
                "Section Name", "Recorded Date Count", "Session Count", "Batch Code");
        List<List<Object>> rows = new ArrayList<>();
        for (HodCoverageAggregation row : page.getContent()) {
            rows.add(java.util.Arrays.asList(
                    row.getSubjectCode(),
                    row.getSubjectName(),
                    row.getSectionCode(),
                    row.getSectionName(),
                    row.getRecordedDateCount(),
                    row.getSessionCount(),
                    null));
        }
        // Phase-2 additive batch twin.
        Page<HodCoverageBatchAggregation> batchPage = hodReportRepository
                .findCoverageByDepartmentAndDateRangeBatch(deptId, start, end, Pageable.unpaged());
        for (HodCoverageBatchAggregation row : batchPage.getContent()) {
            rows.add(java.util.Arrays.asList(
                    row.getSubjectCode(),
                    row.getSubjectName(),
                    null,
                    null,
                    row.getRecordedDateCount(),
                    row.getSessionCount(),
                    row.getBatchCode()));
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
                "Present", "Total Recorded", "Percentage (%)", "Batch");
        List<List<Object>> rows = new ArrayList<>();
        for (HodLowAttendanceDTO row : low) {
            rows.add(java.util.Arrays.asList(row.getRollNumber(), row.getStudentName(), row.getSectionName(),
                    row.getPresentCount(), row.getTotalRecordedCount(), row.getPercentage(),
                    row.getBatchName()));
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
                "Section Name", "Present", "Total Recorded", "Percentage (%)", "Batch Code");
        List<List<Object>> rows = new ArrayList<>();
        for (TeacherSubjectAggregation row : reportRows) {
            rows.add(java.util.Arrays.asList(row.getSubjectCode(), row.getSubjectName(),
                    row.getSectionCode(), row.getSectionName(),
                    row.getPresentCount(), row.getTotalRecorded(),
                    computePercentage(row.getPresentCount(), row.getTotalRecorded()), null));
        }
        // Phase-2 additive batch twin.
        List<TeacherBatchAggregation> batchRows = teacherReportRepository
                .findBatchSubjectReportByTeacherAndDateRange(teacher.getId(), start, end);
        for (TeacherBatchAggregation row : batchRows) {
            rows.add(java.util.Arrays.asList(row.getSubjectCode(), row.getSubjectName(),
                    null, null,
                    row.getPresentCount(), row.getTotalRecorded(),
                    computePercentage(row.getPresentCount(), row.getTotalRecorded()),
                    row.getBatchCode()));
        }
        return new ExportData("Teacher Subject-Wise Attendance Report", subtitle,
                "Subject-wise", headers, rows);
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /**
     * Detailed XLSX attendance register for one class.
     *
     * <p>Logical column order is fixed:
     * {@code Enrollment No. | Student Name | <one column per CONDUCTED session date>
     * | Present | Total Classes | Percentage}.
     *
     * <p>Roll number is deliberately NOT exported: the register identifies a
     * student by enrollment number and name only. {@code P}/{@code A} are the
     * values <i>inside</i> the date columns - there is no separate status
     * column. The date columns come from the authoritative CONDUCTED-session set
     * carried by the report, and {@code Total Classes} is the same shared
     * denominator for every student.
     */
    private ExportData buildTeacherStudentWiseRegisterData(LocalDate startDate,
                                                           LocalDate endDate, Long subjectId,
                                                           Long sectionId, Long batchId) {
        Teacher teacher = teacherResolver.resolve();
        StudentWiseReportDTO report = studentWiseReportService.getStudentWiseReport(
                startDate, endDate, subjectId, sectionId, batchId);

        List<String> headers = new ArrayList<>(List.of("Enrollment No.", "Student Name"));
        for (com.dagacs.dto.StudentWiseColumnDTO col : report.getColumns()) {
            headers.add(col.getDate() + " | " + col.getLecturePeriod());
        }
        headers.addAll(List.of("Present", "Total Classes", "Percentage"));

        List<List<Object>> rows = new ArrayList<>();
        for (com.dagacs.dto.StudentWiseRowDTO row : report.getRows()) {
            List<Object> excelRow = new ArrayList<>();
            excelRow.add(row.getEnrollmentNumber());
            excelRow.add(row.getName());
            for (com.dagacs.dto.StudentWiseColumnDTO col : report.getColumns()) {
                excelRow.add(markSymbol(cellStatusFor(row, col.getSessionId())));
            }
            excelRow.add(row.getPresentCount());
            excelRow.add(row.getTotalRecordedCount());
            excelRow.add(percentageText(row.getPercentage()));
            rows.add(excelRow);
        }

        // Legend below the table, padded to the header width so the sheet stays
        // rectangular and every row maps one-to-one onto the columns.
        List<List<Object>> withLegend = new ArrayList<>(rows);
        if (!withLegend.isEmpty()) {
            List<Object> legend = new ArrayList<>();
            legend.add("Legend");
            legend.add("P = Present, A = Absent");
            while (legend.size() < headers.size()) {
                legend.add("");
            }
            withLegend.add(legend);
        }

        return new ExportData(
                studentWiseTitle(report),
                studentWiseSubtitle(report, teacher, startDate, endDate),
                "Student Register",
                headers,
                withLegend);
    }

    /**
     * Clean printable PDF summary for the same dataset: identity, present, total
     * and percentage only. Deliberately NOT the wide per-session matrix, which
     * stays in the XLSX register.
     */
    private ExportData buildTeacherStudentWiseSummaryData(LocalDate startDate,
                                                          LocalDate endDate, Long subjectId,
                                                          Long sectionId, Long batchId) {
        Teacher teacher = teacherResolver.resolve();
        StudentWiseReportDTO report = studentWiseReportService.getStudentWiseReport(
                startDate, endDate, subjectId, sectionId, batchId);

        List<String> headers =
                List.of("Enrollment No.", "Student Name", "Present", "Total Classes", "Percentage");

        List<List<Object>> rows = new ArrayList<>();
        for (com.dagacs.dto.StudentWiseRowDTO row : report.getRows()) {
            List<Object> pdfRow = new ArrayList<>();
            pdfRow.add(row.getEnrollmentNumber());
            pdfRow.add(row.getName());
            pdfRow.add(row.getPresentCount());
            pdfRow.add(row.getTotalRecordedCount());
            pdfRow.add(percentageText(row.getPercentage()));
            rows.add(pdfRow);
        }

        return new ExportData(
                studentWiseTitle(report),
                studentWiseSubtitle(report, teacher, startDate, endDate),
                "Attendance Summary",
                headers,
                rows);
    }

    /** {@code P}/{@code A} inside the date columns; blank for a missing mark. */
    private static String markSymbol(String status) {
        if ("PRESENT".equals(status)) {
            return "P";
        }
        if ("ABSENT".equals(status)) {
            return "A";
        }
        return "";
    }

    /**
     * Status of one session for one student, or {@code null} when the student
     * has no record for that session. Mirrors the Flutter model's
     * {@code StudentWiseRow.cellStatus} so both renderings agree.
     */
    private static String cellStatusFor(com.dagacs.dto.StudentWiseRowDTO row, Long sessionId) {
        for (com.dagacs.dto.StudentWiseCellDTO cell : row.getCells()) {
            if (sessionId.equals(cell.getSessionId())) {
                return cell.getStatus();
            }
        }
        return null;
    }

    /** Two-decimal percentage, or a dash when no class was conducted. */
    private static String percentageText(Double percentage) {
        return percentage == null ? "-" : String.format(Locale.ROOT, "%.2f%%", percentage);
    }

    private static String studentWiseTitle(StudentWiseReportDTO report) {
        return "DAGACS - Student Attendance Report";
    }

    private static String studentWiseSubtitle(StudentWiseReportDTO report, Teacher teacher,
                                              LocalDate startDate, LocalDate endDate) {
        String section = report.getSectionName() != null
                ? report.getSectionName()
                : (report.getBatchCode() != null ? "Batch " + report.getBatchCode() : "-");
        return "Subject: " + report.getSubjectName()
                + " | Section: " + section
                + " | Teacher: " + teacher.getFullName()
                + " | Date Range: " + dateRangeText(startDate, endDate);
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