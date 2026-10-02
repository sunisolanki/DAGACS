package com.dagacs.export;

/**
 * Phase 4C.2: the professional PDF layout each HOD attendance report asks for.
 *
 * <p>This is the single place the HOD PDF presentation is decided, mirroring the
 * existing {@link HodReportStyles} that does the same for Excel. Keeping it in one
 * class is what stops the footer and layout rules being re-invented inside each
 * export service - the two HOD services call into here instead of building options
 * themselves.</p>
 *
 * <h3>Scope: HOD only</h3>
 * <p>The pre-existing HOD M7.2 department reports and every Teacher PDF share
 * {@code ReportExportService}, which renders through
 * {@link PdfReportGenerator}'s frozen default. They are deliberately not reached
 * from here: wiring them would mean changing a file that also produces Teacher
 * output, and that is outside this phase.</p>
 *
 * <h3>Orientation is not decided here</h3>
 * <p>No preset sets a landscape override. Orientation continues to come from
 * {@link ExportData#landscape()}, which Phase 3 already sets correctly per report
 * - landscape for the wide cross-tab, portrait for everything else. A second
 * source of truth for orientation would be one more thing to keep in step, for no
 * gain, so the presets leave it alone.</p>
 *
 * <h3>Font size is not decided here either</h3>
 * <p>The historical scales (9pt portrait, 6.5pt landscape) are kept. The defect
 * this phase addresses is that every column is the same width; content-derived
 * widths fix that. Shrinking the type on top would add a clipping risk without a
 * proven benefit.</p>
 *
 * <h3>What every HOD PDF gains</h3>
 * <ul>
 *   <li>a running footer naming the report, plus {@code Page X of Y}, drawn in
 *       the page margin so it can never repaginate the body;</li>
 *   <li>column widths proportional to their content, instead of all columns
 *       sharing the page equally;</li>
 *   <li>cell borders and a shaded header row, so a printed page reads as a
 *       table;</li>
 *   <li>rows kept intact, so one student's record is never split across a page
 *       break;</li>
 *   <li>an empty report rendered as a real table that states why it is empty,
 *       rather than one line of joined headers.</li>
 * </ul>
 *
 * <p>None of this touches the report's data, columns, values, headers, branding,
 * academic context or date range: those are assembled upstream and are unaffected
 * by anything in this class.</p>
 */
final class HodReportPdfStyles {

    private HodReportPdfStyles() {
    }

    /**
     * The layout shared by every HOD attendance PDF.
     *
     * <p>Total-row emphasis is included here because it is a no-op for any report
     * that marks no total row, and having it here means the student report - the
     * one report that does mark one - needs no special case.</p>
     */
    private static PdfStyleOptions base() {
        return PdfStyleOptions.defaults()
                .withPageFooter(true)
                .withPageNumbers(true)
                .withFooterContext(true)
                .withContentWidths(true)
                .withCellBorders(true)
                .withHeaderShading(true)
                .withKeepRowsIntact(true)
                .withEmphasizedTotalRows(true)
                .withEmptyReportAsTable(true);
    }

    /** The context-wide subject summary. */
    static PdfStyleOptions overview() {
        return base();
    }

    /** The threshold report a HOD works through. */
    static PdfStyleOptions lowAttendance() {
        return base();
    }

    /** One student's breakdown; its TOTAL row is marked by the export layer. */
    static PdfStyleOptions student() {
        return base();
    }

    /** One subject's roster. */
    static PdfStyleOptions subject() {
        return base();
    }

    /**
     * The wide cross-tab.
     *
     * <p>Identical to the base layout. Orientation is the only thing that differs
     * between this report and the others, and that comes from the data model -
     * see the class comment.</p>
     */
    static PdfStyleOptions matrix() {
        return base();
    }
}