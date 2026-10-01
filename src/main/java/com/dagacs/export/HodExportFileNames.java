package com.dagacs.export;

import java.time.LocalDate;

/**
 * Phase 4A: deterministic, filesystem-safe attachment names for HOD attendance
 * reports.
 *
 * <p><b>Deterministic.</b> The name is derived only from the report kind, the
 * academic context and the date range. It deliberately contains <b>no generated
 * timestamp</b>, so exporting the same context twice yields the same file name and
 * a re-export cannot quietly create a second, differently-named copy of the same
 * report. The generation timestamp is printed <i>inside</i> the document header
 * instead, where it is informative without making the file name unstable.</p>
 *
 * <p><b>Safe.</b> Context names are real database values, so they are sanitized
 * rather than trusted: anything outside {@code [A-Za-z0-9]} collapses to a single
 * hyphen, which removes the Windows-reserved characters ({@code \ / : * ? " < > |}),
 * control characters, path traversal and leading/trailing dots that would produce
 * an illegal or surprising file on the receiving machine.</p>
 *
 * <p>Follows the same shape as the existing {@code ReportFileNames} (M7.2) rather
 * than introducing a second naming convention.</p>
 */
public final class HodExportFileNames {

    /** Upper bound on the whole attachment name, extension included. */
    private static final int MAX_NAME_LENGTH = 120;

    /** Upper bound on any single context segment (e.g. a long subject name). */
    private static final int MAX_SEGMENT_LENGTH = 40;

    private static final String INSTITUTION_PREFIX = "DAGACS_Attendance";

    private HodExportFileNames() {
    }

    /**
     * Builds the attachment name for one HOD report export.
     *
     * @param reportKind  stable report identifier, e.g. {@code Matrix},
     *                    {@code Overview}, {@code Student}, {@code Subject},
     *                    {@code Low}
     * @param contextName optional already-joined context segment, e.g.
     *                    {@code BTech-CSE_Sem3_Section-A}
     * @param subjectCode optional subject code, appended only for a
     *                    subject-specific report
     * @param format      the export format, whose extension becomes the suffix
     */
    public static String build(String reportKind,
                               String contextName,
                               String subjectCode,
                               ExportFormat format,
                               LocalDate startDate,
                               LocalDate endDate) {
        StringBuilder name = new StringBuilder(INSTITUTION_PREFIX);
        name.append('_').append(sanitize(reportKind));
        append(name, contextName);
        append(name, subjectCode);
        name.append(dateSuffix(startDate, endDate));
        name.append('.').append(format.extension());
        return truncate(name.toString(), MAX_NAME_LENGTH);
    }

    private static void append(StringBuilder name, String segment) {
        String clean = truncateSegment(sanitize(segment));
        if (!clean.isEmpty()) {
            name.append('_').append(clean);
        }
    }

    /**
     * Collapses every character outside {@code [A-Za-z0-9_]} into a single hyphen.
     *
     * <p>Letters, digits, hyphens and underscores are kept, so the result stays
     * readable and the {@code _} separators that delimit the academic levels
     * survive. Everything else - the Windows-reserved characters
     * ({@code \ / : * ? " < > |}), spaces, control characters, path traversal and
     * the dots of a file extension - becomes a hyphen, and runs are collapsed.
     * Leading and trailing separators are trimmed so no segment is a bare
     * {@code -} or {@code _}.</p>
     */
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String flattened = raw.replaceAll("[^A-Za-z0-9_]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        return truncateSegment(flattened);
    }

    /**
     * Caps one segment, trimming any trailing separator left by the cut so a
     * truncated segment never ends in a bare hyphen or underscore.
     */
    private static String truncateSegment(String value) {
        if (value.length() <= MAX_SEGMENT_LENGTH) {
            return value;
        }
        return truncate(value, MAX_SEGMENT_LENGTH).replaceAll("[-_]+$", "");
    }

    /**
     * The date-range suffix.
     *
     * <p>Mirrors the existing M7.2 convention: a closed range, an open start, an
     * open end, or an explicit {@code All} when no date filter is applied, so a
     * reader can tell a full-term report from a date-filtered one without opening
     * the file.</p>
     */
    private static String dateSuffix(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return "_" + startDate + "_to_" + endDate;
        }
        if (startDate != null) {
            return "_" + startDate + "_onwards";
        }
        if (endDate != null) {
            return "_to_" + endDate;
        }
        return "_All";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Joins the readable context parts into one self-describing filename segment.
     *
     * <p>{@code Program} and {@code Section} carry an explicit label, because the
     * bare values are otherwise unreadable next to each other - {@code A} says
     * nothing without knowing it is a section. The semester is <b>not</b> labelled
     * because a real semester name already reads as one ({@code "Semester 3"}),
     * and {@code Semester-Semester-3} would be noise.</p>
     *
     * <p>Each part is sanitized before it is joined, so the {@code _} separators
     * between the levels survive; sanitizing the joined string instead would
     * collapse every separator into a hyphen. A level that is absent contributes
     * nothing, so a partial context produces a shorter, still honest name.</p>
     */
    public static String labelledContextSegment(String program, String semester, String section) {
        StringBuilder joined = new StringBuilder();
        appendLabelled(joined, "Program", program);
        append(joined, semester);
        appendLabelled(joined, "Section", section);
        return joined.toString();
    }

    private static void appendLabelled(StringBuilder joined, String label, String value) {
        String clean = sanitize(value);
        if (clean.isEmpty()) {
            return;
        }
        if (!joined.isEmpty()) {
            joined.append('_');
        }
        joined.append(label).append('-').append(clean);
    }
}
