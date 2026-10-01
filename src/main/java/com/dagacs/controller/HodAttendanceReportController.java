package com.dagacs.controller;

import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceMatrixDTO;
import com.dagacs.dto.HodAttendanceOverviewDTO;
import com.dagacs.dto.HodLowAttendanceReportDTO;
import com.dagacs.dto.HodStudentAttendanceDetailDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailDTO;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.export.ExportFormat;
import com.dagacs.export.HodAttendanceMatrixExportService;
import com.dagacs.export.HodAttendanceReportExportService;
import com.dagacs.export.HodContextPackExportService;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Phase 3 HOD attendance intelligence API.
 *
 * <p>Dedicated reporting endpoints rather than an overload of the Phase 2
 * hierarchy endpoints, which are frozen and keep serving their existing
 * contracts unchanged.</p>
 *
 * <pre>
 * GET /api/hod/attendance/overview
 * GET /api/hod/attendance/matrix
 * GET /api/hod/attendance/student/{studentId}
 * GET /api/hod/attendance/subject/{subjectId}
 * GET /api/hod/attendance/low
 * GET /api/hod/attendance/matrix/export.xlsx
 * GET /api/hod/attendance/matrix/export.pdf
 * GET /api/hod/attendance/overview/export.xlsx
 * GET /api/hod/attendance/overview/export.pdf
 * GET /api/hod/attendance/student/{studentId}/export.xlsx
 * GET /api/hod/attendance/student/{studentId}/export.pdf
 * GET /api/hod/attendance/subject/{subjectId}/export.xlsx
 * GET /api/hod/attendance/subject/{subjectId}/export.pdf
 * GET /api/hod/attendance/low/export.xlsx
 * GET /api/hod/attendance/low/export.pdf
 * </pre>
 *
 * <p><b>Authorization.</b> {@code @PreAuthorize("hasRole('HOD')')} plus the
 * {@code /api/hod/**} rule in {@code SecurityConfig} means an unauthenticated
 * request is 401 and any non-HOD role is 403 before this controller is reached.
 * The department is <b>never</b> a parameter: it is derived from the JWT, and
 * every supplied academic id, student id and subject id is proven to belong to
 * that department - and to the exact academic context - inside the service,
 * before any data query runs. A cross-department or out-of-context id yields 403
 * with no data.</p>
 *
 * <p><b>An export is never broader than its screen.</b> Every export endpoint
 * delegates to the very same report service method the on-screen report uses, so
 * it inherits the identical {@code assertOwned} /
 * {@code assertStudentInContext} / {@code assertSubjectInContext} authorization
 * and the identical attendance arithmetic. There is no separate export data
 * path, which is what makes an export security bypass structurally impossible
 * rather than merely tested for.</p>
 *
 * <p><b>Matrix context is mandatory.</b> {@code /matrix} and its two exports
 * require Academic Session, Program, Semester and Section; an incomplete
 * selection is rejected with 400 and
 * {@link HodAttendanceReportService#MATRIX_CONTEXT_REQUIRED_MESSAGE} so a mixed
 * department/semester cross-tab can never be produced. The overview, student,
 * subject and low endpoints accept a partial context, which falls back to the
 * pre-existing department-wide behaviour.</p>
 *
 * <p><b>Date ranges are optional, inclusive and additive.</b> They are applied
 * with {@code AND} on top of the academic context, never with {@code OR}, so the
 * two filters can never widen each other. A start date after the end date is
 * 400 through the same {@link InvalidDateRangeException} the other HOD
 * endpoints use.</p>
 */
@RestController
@RequestMapping("/api/hod/attendance")
@PreAuthorize("hasRole('HOD')")
public class HodAttendanceReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_MEDIA_TYPE = "application/pdf";

    private final HodAttendanceReportService attendanceReportService;
    private final HodAttendanceMatrixExportService matrixExportService;
    private final HodAttendanceReportExportService reportExportService;
    private final HodContextPackExportService contextPackExportService;

    public HodAttendanceReportController(HodAttendanceReportService attendanceReportService,
                                         HodAttendanceMatrixExportService matrixExportService,
                                         HodAttendanceReportExportService reportExportService,
                                         HodContextPackExportService contextPackExportService) {
        this.attendanceReportService = attendanceReportService;
        this.matrixExportService = matrixExportService;
        this.reportExportService = reportExportService;
        this.contextPackExportService = contextPackExportService;
    }

    @GetMapping("/overview")
    public ResponseEntity<HodAttendanceOverviewDTO> getOverview(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(attendanceReportService.getOverview(
                selection(academicSessionId, programId, semesterId, sectionId),
                toCanonicalString(startDate), toCanonicalString(endDate)));
    }

    @GetMapping("/matrix")
    public ResponseEntity<HodAttendanceMatrixDTO> getMatrix(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "sortBy", defaultValue = "enrollmentNumber") String sortBy,
            @RequestParam(name = "direction", defaultValue = "asc") String direction) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(attendanceReportService.getMatrix(
                selection(academicSessionId, programId, semesterId, sectionId),
                subjectId, toCanonicalString(startDate), toCanonicalString(endDate),
                page, size, sortBy, direction));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<HodStudentAttendanceDetailDTO> getStudent(
            @PathVariable("studentId") Long studentId,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(attendanceReportService.getStudentDetail(
                selection(academicSessionId, programId, semesterId, sectionId),
                studentId, subjectId,
                toCanonicalString(startDate), toCanonicalString(endDate)));
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<HodSubjectAttendanceDetailDTO> getSubject(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(attendanceReportService.getSubjectDetail(
                selection(academicSessionId, programId, semesterId, sectionId),
                subjectId, toCanonicalString(startDate), toCanonicalString(endDate)));
    }

    @GetMapping("/low")
    public ResponseEntity<HodLowAttendanceReportDTO> getLowAttendance(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(attendanceReportService.getLowAttendance(
                selection(academicSessionId, programId, semesterId, sectionId),
                toCanonicalString(startDate), toCanonicalString(endDate)));
    }

    /**
     * The attendance matrix as a cross-tab spreadsheet.
     *
     * <p>Always unpaged: an export is a file-generation operation and must
     * contain every row of the selected context, not one page of it. The data is
     * the identical matrix the on-screen report renders, so the file and the
     * screen can never disagree.</p>
     */
    @GetMapping("/matrix/export.xlsx")
    public ResponseEntity<byte[]> exportMatrixXlsx(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        byte[] bytes = matrixExportService.export(ExportFormat.XLSX,
                selection(academicSessionId, programId, semesterId, sectionId),
                subjectId, toCanonicalString(startDate), toCanonicalString(endDate));
        return attachment(bytes, XLSX_MEDIA_TYPE,
                matrixExportService.fileName(ExportFormat.XLSX, startDate, endDate));
    }

    /**
     * The same cross-tab as a PDF, produced from the identical data model by the
     * existing generator so the two files can never disagree on any number.
     * A visual redesign of the PDF layout is out of Phase 3 scope.
     */
    @GetMapping("/matrix/export.pdf")
    public ResponseEntity<byte[]> exportMatrixPdf(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        byte[] bytes = matrixExportService.export(ExportFormat.PDF,
                selection(academicSessionId, programId, semesterId, sectionId),
                subjectId, toCanonicalString(startDate), toCanonicalString(endDate));
        return attachment(bytes, PDF_MEDIA_TYPE,
                matrixExportService.fileName(ExportFormat.PDF, startDate, endDate));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Phase 4A exports
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The attendance overview as a subject-summary spreadsheet.
     *
     * <p>Phase 4A. Same service method as the on-screen overview, so the file and
     * the screen can never disagree, and the authorization is identical.</p>
     */
    @GetMapping("/overview/export.xlsx")
    public ResponseEntity<byte[]> exportOverviewXlsx(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return overviewAttachment(ExportFormat.XLSX, academicSessionId, programId,
                semesterId, sectionId, startDate, endDate);
    }

    /** The same overview as a PDF, from the identical data model. */
    @GetMapping("/overview/export.pdf")
    public ResponseEntity<byte[]> exportOverviewPdf(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return overviewAttachment(ExportFormat.PDF, academicSessionId, programId,
                semesterId, sectionId, startDate, endDate);
    }

    /**
     * One student's attendance as a spreadsheet.
     *
     * <p>The student id is proven to belong to the selected context inside the
     * service, so a cross-context or cross-department student id is a 403 with no
     * data rather than another department's report.</p>
     */
    @GetMapping("/student/{studentId}/export.xlsx")
    public ResponseEntity<byte[]> exportStudentXlsx(
            @PathVariable("studentId") Long studentId,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return studentAttachment(ExportFormat.XLSX, studentId, academicSessionId, programId,
                semesterId, sectionId, subjectId, startDate, endDate);
    }

    /** The same student report as a PDF, from the identical data model. */
    @GetMapping("/student/{studentId}/export.pdf")
    public ResponseEntity<byte[]> exportStudentPdf(
            @PathVariable("studentId") Long studentId,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "subjectId", required = false) Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return studentAttachment(ExportFormat.PDF, studentId, academicSessionId, programId,
                semesterId, sectionId, subjectId, startDate, endDate);
    }

    /**
     * One subject's attendance as a spreadsheet.
     *
     * <p>The subject id is proven to belong to the selected context inside the
     * service, exactly as the on-screen subject report proves it.</p>
     */
    @GetMapping("/subject/{subjectId}/export.xlsx")
    public ResponseEntity<byte[]> exportSubjectXlsx(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return subjectAttachment(ExportFormat.XLSX, subjectId, academicSessionId, programId,
                semesterId, sectionId, startDate, endDate);
    }

    /** The same subject report as a PDF, from the identical data model. */
    @GetMapping("/subject/{subjectId}/export.pdf")
    public ResponseEntity<byte[]> exportSubjectPdf(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return subjectAttachment(ExportFormat.PDF, subjectId, academicSessionId, programId,
                semesterId, sectionId, startDate, endDate);
    }

    /** The low-attendance report as a spreadsheet. */
    @GetMapping("/low/export.xlsx")
    public ResponseEntity<byte[]> exportLowXlsx(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return lowAttachment(ExportFormat.XLSX, academicSessionId, programId,
                semesterId, sectionId, startDate, endDate);
    }

    /** The same low-attendance report as a PDF, from the identical data model. */
    @GetMapping("/low/export.pdf")
    public ResponseEntity<byte[]> exportLowPdf(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return lowAttachment(ExportFormat.PDF, academicSessionId, programId,
                semesterId, sectionId, startDate, endDate);
    }

    /**
     * The context metadata of the current selection, so a client can confirm
     * exactly which academic context a report will be computed for.
     */
    @GetMapping("/context")
    public ResponseEntity<HodAttendanceContextDTO> getContext(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId) {
        return ResponseEntity.ok(attendanceReportService.resolveContextMetadata(
                selection(academicSessionId, programId, semesterId, sectionId)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Phase 4B - the Context Pack
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * One workbook holding the reports of this academic context.
     *
     * <p><b>Phase 4B.</b> Five sheets - Executive Summary, Attendance Matrix,
     * Low Attendance, Subject Summary, Student Summary - each produced by the same
     * canonical service methods the standalone exports use, so the Pack cannot
     * disagree with any single report a HOD already trusts.</p>
     *
     * <p><b>The same authorization, not a second one.</b> The endpoint takes only
     * the academic-selection parameters the Phase 4A context reports take. No
     * department, student or subject identity is accepted from the client, and
     * the department is still derived from the JWT inside the service, so the
     * cross-department and cross-context rejections of Phase 4A apply to the Pack
     * unchanged. A Pack is also strictly narrower than any Phase 4A endpoint: it
     * cannot reach one student's report, only the whole authorized context.</p>
     *
     * <p>XLSX only for now. A multi-sheet PDF needs pagination and print chrome,
     * which is Phase 4C's scope, and shipping one before that exists would mean
     * designing it twice.</p>
     */
    @GetMapping("/context-pack/export.xlsx")
    public ResponseEntity<byte[]> exportContextPack(
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        HodContextPackExportService.ExportedPack pack = contextPackExportService.export(
                selection(academicSessionId, programId, semesterId, sectionId),
                startDate, endDate);
        return attachment(pack.bytes(), XLSX_MEDIA_TYPE, pack.fileName());
    }

    private static ResponseEntity<byte[]> attachment(byte[] body, String mediaType, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    /**
     * Builds one export attachment from a report that already knows its own file
     * name.
     *
     * <p>The name is derived by the export service from the report data it just
     * built, so a name can never describe a context the bytes are not from, and no
     * second service call is needed just to name a file.</p>
     */
    private static ResponseEntity<byte[]> exportAttachment(
            HodAttendanceReportExportService.ExportedFile exported, ExportFormat format) {
        return attachment(exported.bytes(),
                format == ExportFormat.PDF ? PDF_MEDIA_TYPE : XLSX_MEDIA_TYPE,
                exported.fileName());
    }

    private ResponseEntity<byte[]> overviewAttachment(ExportFormat format,
                                                       Long academicSessionId,
                                                       Long programId,
                                                       Long semesterId,
                                                       Long sectionId,
                                                       LocalDate startDate,
                                                       LocalDate endDate) {
        return exportAttachment(reportExportService.exportOverview(format,
                selection(academicSessionId, programId, semesterId, sectionId),
                startDate, endDate), format);
    }

    private ResponseEntity<byte[]> studentAttachment(ExportFormat format,
                                                     Long studentId,
                                                     Long academicSessionId,
                                                     Long programId,
                                                     Long semesterId,
                                                     Long sectionId,
                                                     Long subjectId,
                                                     LocalDate startDate,
                                                     LocalDate endDate) {
        return exportAttachment(reportExportService.exportStudent(format,
                selection(academicSessionId, programId, semesterId, sectionId),
                studentId, subjectId, startDate, endDate), format);
    }

    private ResponseEntity<byte[]> subjectAttachment(ExportFormat format,
                                                     Long subjectId,
                                                     Long academicSessionId,
                                                     Long programId,
                                                     Long semesterId,
                                                     Long sectionId,
                                                     LocalDate startDate,
                                                     LocalDate endDate) {
        return exportAttachment(reportExportService.exportSubject(format,
                selection(academicSessionId, programId, semesterId, sectionId),
                subjectId, startDate, endDate), format);
    }

    private ResponseEntity<byte[]> lowAttachment(ExportFormat format,
                                                 Long academicSessionId,
                                                 Long programId,
                                                 Long semesterId,
                                                 Long sectionId,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
        return exportAttachment(reportExportService.exportLow(format,
                selection(academicSessionId, programId, semesterId, sectionId),
                startDate, endDate), format);
    }

    private static HodAcademicSelection selection(Long academicSessionId,
                                                  Long programId,
                                                  Long semesterId,
                                                  Long sectionId) {
        return new HodAcademicSelection(academicSessionId, programId, semesterId, sectionId);
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }
}
