package com.dagacs.export;

import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceMatrixCellDTO;
import com.dagacs.dto.HodAttendanceMatrixColumnDTO;
import com.dagacs.dto.HodAttendanceMatrixDTO;
import com.dagacs.dto.HodAttendanceMatrixRowDTO;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 3 export orchestrator for the HOD attendance matrix.
 *
 * <p><b>Read-only and additive.</b> It reuses
 * {@link HodAttendanceReportService} for the data and the frozen
 * {@link ExcelReportGenerator} / {@link PdfReportGenerator} for the rendering, so
 * no attendance rule and no generator behaviour is duplicated or re-implemented
 * here. The Excel and the PDF are produced from the <b>same</b>
 * {@link ExportData} instance, which is what guarantees the two files can never
 * disagree about a single attendance number.</p>
 *
 * <h3>Logical column order (fixed)</h3>
 * <pre>
 * 1. Enrollment No.
 * 2. Student Name
 * 3..N. one column per subject, dynamically generated from the selected context
 * N+1. Total Present
 * N+2. Total Classes
 * N+3. Overall Attendance %
 * </pre>
 * <p>The subject columns are dynamic; no subject is ever hardcoded. Each student
 * occupies exactly one row - this is a cross-tab academic report, never a
 * normalised {@code Student / Subject / Date / Status} table.</p>
 *
 * <p><b>No calculation happens here.</b> Every figure - the cell denominators
 * (conducted classes), the row totals and the overall percentage - arrives
 * already computed on the matrix DTO by the single canonical resolver in
 * {@link HodAttendanceReportService}. This service only arranges it into the
 * frozen {@link ExportData} table, which is exactly why the Excel and the PDF
 * can never disagree, and why neither can drift from the on-screen report.</p>
 *
 * <p><b>Context isolation.</b> The matrix is only ever produced for a fully
 * resolved context (session, program, semester, section) and the service
 * authorizes every supplied id against the authenticated HOD's department, so
 * the file can contain only the students and subjects of that one context -
 * never an M.Tech student, another semester, another section, another
 * department or another academic session.</p>
 *
 * <h3>Unpaged by construction</h3>
 * <p>An export is a file-generation operation, so it always requests the whole
 * student population of the context rather than one page of it.</p>
 *
 * <p>Known and explicitly deferred: the existing {@link PdfReportGenerator}
 * renders a plain table with no branding, headers or column-width tuning, so the
 * <b>PDF visual redesign is deferred to the 4B/4C sub-phases</b>. No new PDF code
 * is introduced here, and no fake or placeholder download is ever produced.</p>
 *
 * <p><b>Phase 4A.</b> The header block is now built by the shared
 * {@link HodExportHeader}, so the institution, the real department, the academic
 * context, the date range and the generation timestamp are identical to the other
 * four HOD reports. The existing attachment name is deliberately unchanged, so
 * every filename and header assertion in {@code HodAttendanceMatrixExportTest}
 * keeps holding.</p>
 */
@Service
public class HodAttendanceMatrixExportService {

    private static final int UNPAGED_SIZE = Integer.MAX_VALUE;

    private final HodAttendanceReportService attendanceReportService;
    private final HodExportHeader exportHeader;
    private final ExcelReportGenerator excelGenerator;
    private final PdfReportGenerator pdfGenerator;

    public HodAttendanceMatrixExportService(HodAttendanceReportService attendanceReportService,
                                            HodExportHeader exportHeader,
                                            ExcelReportGenerator excelGenerator,
                                            PdfReportGenerator pdfGenerator) {
        this.attendanceReportService = attendanceReportService;
        this.exportHeader = exportHeader;
        this.excelGenerator = excelGenerator;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional(readOnly = true)
    public byte[] export(ExportFormat format, HodAcademicSelection selection, Long subjectId,
                         String startDate, String endDate) {
        ExportData data = buildMatrixData(selection, subjectId, startDate, endDate);
        return switch (format) {
            case XLSX -> excelGenerator.generate(data, true);
            case PDF -> pdfGenerator.generate(data);
        };
    }

    /** Deterministic attachment name, derived only from the format and range. */
    public String fileName(ExportFormat format, java.time.LocalDate startDate,
                           java.time.LocalDate endDate) {
        String extension = format == ExportFormat.PDF ? "pdf" : "xlsx";
        String suffix;
        if (startDate != null && endDate != null) {
            suffix = "_" + startDate + "_to_" + endDate;
        } else if (startDate != null) {
            suffix = "_" + startDate + "_onwards";
        } else if (endDate != null) {
            suffix = "_to_" + endDate;
        } else {
            suffix = "_all";
        }
        return "dagacs_hod_attendance_matrix" + suffix + "." + extension;
    }

    /**
     * Maps the matrix onto the generic export table.
     *
     * <p>Package-visible for the export test so a column-order or
     * context-isolation regression is asserted against the data model itself and
     * not only against a rendered file.</p>
     */
    ExportData buildMatrixData(HodAcademicSelection selection, Long subjectId,
                               String startDate, String endDate) {
        HodAttendanceMatrixDTO matrix = attendanceReportService.getMatrix(
                selection, subjectId, startDate, endDate,
                0, UNPAGED_SIZE,
                HodAttendanceReportService.SORT_ENROLLMENT_NUMBER, "asc");

        HodAttendanceContextDTO context = matrix.getContext();
        List<HodAttendanceMatrixColumnDTO> subjects = matrix.getSubjects() == null
                ? List.of()
                : matrix.getSubjects();

        // 1. Enrollment No.  2. Student Name  3..N. dynamic subject columns
        // N+1. Total Present  N+2. Total Classes  N+3. Overall Attendance %
        List<String> headers = new ArrayList<>(List.of("Enrollment No.", "Student Name"));
        for (HodAttendanceMatrixColumnDTO subject : subjects) {
            headers.add(columnLabel(subject));
        }
        headers.addAll(List.of("Total Present", "Total Classes", "Overall Attendance %"));

        List<List<Object>> rows = new ArrayList<>();
        boolean anyConductedClass = false;
        for (HodAttendanceMatrixRowDTO row : matrix.getStudents() == null
                ? List.<HodAttendanceMatrixRowDTO>of() : matrix.getStudents()) {
            List<Object> cells = new ArrayList<>();
            cells.add(row.getEnrollmentNumber() == null || row.getEnrollmentNumber().isBlank()
                    ? row.getRollNumber() : row.getEnrollmentNumber());
            cells.add(row.getStudentName());
            for (HodAttendanceMatrixCellDTO cell : row.getSubjects() == null
                    ? List.<HodAttendanceMatrixCellDTO>of() : row.getSubjects()) {
                // "32 / 40" - the meaningful present/total form, never a bare
                // percentage that hides the denominator.
                cells.add(cell.getPresent() + " / " + cell.getTotal());
                if (cell.getTotal() != null && cell.getTotal() > 0) {
                    anyConductedClass = true;
                }
            }
            cells.add(row.getTotalPresent());
            cells.add(row.getTotalClasses());
            // "N/A" when nothing was conducted: the established convention, and
            // never a fabricated 0.00%.
            cells.add(percentageText(row.getOverallPercentage()));
            rows.add(cells);
        }

        String title = subjectId == null
                ? "HOD ATTENDANCE MATRIX"
                : "HOD ATTENDANCE MATRIX (SUBJECT)";
        String[] subjectLines = subjectId == null || subjects.isEmpty()
                ? new String[0]
                : new String[]{"Subjects: " + subjectListText(subjects)};

        // Phase 4A: the shared header, so institution, real department, context,
        // date range and generation timestamp match the other four reports. An
        // empty matrix states that plainly instead of presenting a blank table.
        HodExportHeader.Block header = anyConductedClass
                ? exportHeader.build(title, context, startDate, endDate,
                java.time.LocalDateTime.now(), subjectLines)
                : exportHeader.buildEmpty(title, context, startDate, endDate,
                java.time.LocalDateTime.now(), subjectLines);

        return new ExportData(
                header.institution(),
                header.reportTitle(),
                "Attendance Matrix",
                headers,
                rows,
                header.metaLines(),
                // A cross-tab with dynamic subject columns is inherently wide:
                // landscape keeps no subject column clipped.
                true);
    }

    private static String subjectListText(List<HodAttendanceMatrixColumnDTO> subjects) {
        StringBuilder joined = new StringBuilder();
        for (HodAttendanceMatrixColumnDTO subject : subjects) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(columnLabel(subject));
        }
        return joined.toString();
    }

    private static String columnLabel(HodAttendanceMatrixColumnDTO subject) {
        String code = subject.getSubjectCode();
        String name = subject.getSubjectName();
        if (code == null || code.isBlank()) {
            return name == null ? "Subject" : name;
        }
        return name == null || name.isBlank() ? code : code + " - " + name;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String percentageText(Double percentage) {
        return percentage == null ? "N/A" : String.format(Locale.ROOT, "%.2f%%", percentage);
    }
}
