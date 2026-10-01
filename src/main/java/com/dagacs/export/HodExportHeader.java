package com.dagacs.export;

import com.dagacs.config.ReportBrandingProperties;
import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.entity.Department;
import com.dagacs.security.AuthenticatedHodResolver;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4A: the professional header block shared by every HOD attendance export.
 *
 * <p>One implementation, five reports, so the institution line, the department
 * line, the academic context, the date range and the generation timestamp can
 * never drift between the overview, the matrix, the student report, the subject
 * report and the low-attendance report.</p>
 *
 * <h3>Line sources - deliberately different origins</h3>
 * <ul>
 *   <li><b>Institution</b> comes from {@link ReportBrandingProperties}, i.e.
 *       deployment configuration. The application stores no institution, so this
 *       is configuration rather than invented data.</li>
 *   <li><b>Department</b> comes from the real {@link Department#getName()} of the
 *       authenticated HOD, resolved from the JWT. It is <b>never</b> configuration
 *       and never a request parameter, so a report can never claim a department
 *       its HOD does not belong to.</li>
 *   <li><b>Academic context and date range</b> come from the values the
 *       canonical report service already used to build the data for this very
 *       request, so the header cannot disagree with the body.</li>
 * </ul>
 *
 * <p><b>No calculation happens here.</b> This class only arranges already-computed
 * values into a header; it never touches an attendance figure, so it cannot alter
 * the Phase 3 denominator.</p>
 */
@Component
public class HodExportHeader {

    /**
     * Stable wording for the legend, shared by every report so a reader learns it
     * once. It states the approved Phase 3 semantics explicitly.
     */
    public static final String DENOMINATOR_LEGEND =
            "Total Classes = conducted attendance sessions. Cells show Present / Conducted Classes. "
                    + "Unmarked conducted classes count as not attended. "
                    + "Attendance % = Present / Conducted Classes x 100; when no class was conducted it is N/A.";

    private static final String NO_SESSIONS_NOTICE =
            "No attendance sessions were conducted for the selected criteria.";

    private static final DateTimeFormatter GENERATED_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    private final ReportBrandingProperties branding;
    private final AuthenticatedHodResolver hodResolver;

    public HodExportHeader(ReportBrandingProperties branding,
                           AuthenticatedHodResolver hodResolver) {
        this.branding = branding;
        this.hodResolver = hodResolver;
    }

    /**
     * The header block for one export.
     *
     * @param reportTitle  the human report title, e.g. {@code ATTENDANCE MATRIX}
     * @param context      the already-authorized academic context of the data
     * @param startDate    applied range start (ISO), or null
     * @param endDate      applied range end (ISO), or null
     * @param generatedAt  the generation timestamp; null omits the line
     * @param extraLines   report-specific context lines (e.g. the selected subject)
     */
    public Block build(String reportTitle,
                       HodAttendanceContextDTO context,
                       String startDate,
                       String endDate,
                       LocalDateTime generatedAt,
                       String... extraLines) {
        String department = departmentText();
        List<String> lines = new ArrayList<>();
        for (String line : extraLines) {
            if (notBlank(line)) {
                lines.add(line.trim());
            }
        }
        addIfPresent(lines, "Academic Session", context == null ? null : context.getAcademicSessionName());
        addIfPresent(lines, "Program", context == null ? null : context.getProgramName());
        addIfPresent(lines, "Semester", context == null ? null : context.getSemesterName());
        addIfPresent(lines, "Section", context == null ? null : context.getSectionName());
        lines.add("Date Range: " + dateRangeText(startDate, endDate));
        // The real department of the authenticated HOD, never configuration.
        if (notBlank(department)) {
            lines.add("Department: " + department);
        }
        if (generatedAt != null) {
            lines.add("Generated: " + GENERATED_FORMAT.format(generatedAt));
        }
        lines.add(DENOMINATOR_LEGEND);
        return new Block(institution(), department, reportTitle, lines);
    }

    /**
     * The same header, with the explicit empty-state notice appended as the last
     * line. Used when a context produced no conducted sessions, so the file states
     * that plainly instead of presenting an empty table that reads like a failure
     * to generate.
     */
    public Block buildEmpty(String reportTitle,
                            HodAttendanceContextDTO context,
                            String startDate,
                            String endDate,
                            LocalDateTime generatedAt,
                            String... extraLines) {
        Block block = build(reportTitle, context, startDate, endDate, generatedAt, extraLines);
        List<String> lines = new ArrayList<>(block.metaLines());
        lines.add(noSessionsNotice());
        return new Block(block.institution(), block.department(), reportTitle, lines);
    }

    /** The configured institution name, with a defensive fallback. */
    public String institution() {
        return branding == null ? "DAGACS" : branding.resolvedInstitutionName();
    }

    /**
     * The real department name of the authenticated HOD.
     *
     * <p>Returns an empty string - which the header then omits - rather than a
     * placeholder, if the department cannot be resolved. In practice
     * {@link AuthenticatedHodResolver} already rejects a HOD with no department
     * with a 401 before any of this runs.</p>
     */
    public String departmentText() {
        try {
            Department department = hodResolver.resolve().getDepartment();
            return department == null || department.getName() == null
                    ? "" : department.getName().trim();
        } catch (RuntimeException e) {
            // Identity problems are the controller's concern and are handled by
            // its own authorization, which runs first. The header must never be
            // the thing that fails a request, so the line is simply omitted.
            return "";
        }
    }

    /** Human-readable range: a closed range, an open end, or an explicit "All dates". */
    public static String dateRangeText(String startDate, String endDate) {
        if (notBlank(startDate) && notBlank(endDate)) {
            return startDate + " to " + endDate;
        }
        if (notBlank(startDate)) {
            return "from " + startDate;
        }
        if (notBlank(endDate)) {
            return "until " + endDate;
        }
        return "All dates";
    }

    public static String noSessionsNotice() {
        return NO_SESSIONS_NOTICE;
    }

    private static void addIfPresent(List<String> lines, String label, String value) {
        if (notBlank(value)) {
            lines.add(label + ": " + value.trim());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The parts of a header, already shaped for the generators.
     *
     * <p>{@code institution} becomes the workbook/PDF title, {@code reportTitle}
     * the subtitle, and {@code metaLines} the context block beneath it.</p>
     */
    public record Block(String institution,
                        String department,
                        String reportTitle,
                        List<String> metaLines) {

        public List<String> metaLines() {
            return List.copyOf(metaLines);
        }
    }
}
