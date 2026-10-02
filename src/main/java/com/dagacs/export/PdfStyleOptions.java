package com.dagacs.export;

/**
 * Phase 4C: the professional PDF layout, expressed as opt-in options.
 *
 * <p><b>Every default is the behaviour that already existed.</b> That is the whole
 * point of this class: {@link #defaults()} reproduces the pre-Phase-4C PDF
 * exactly, so {@link PdfReportGenerator#generate(ExportData)} - which delegates
 * to it - produces an indistinguishable document for every existing caller. Only
 * a report that explicitly asks for something gets it.
 *
 * <h3>Why this must be strictly additive</h3>
 * <p>{@link PdfReportGenerator} is shared infrastructure. It renders the HOD
 * attendance PDFs <i>and</i>, through {@code ReportExportService}, the pre-existing
 * HOD M7.2 department reports and every Teacher export. A change to the shared
 * generator that altered a default would silently reformat Teacher M7.2 output,
 * which is exactly the kind of drift Phase 4A forbade for routing and this class
 * forbids for rendering.
 *
 * <p>The defaults are therefore pinned by explicit assertions in
 * {@code PdfFormattingTest}, not merely intended: page size, margins, font size,
 * equal column widths, no footer, no page numbers, no borders, no shading, and
 * the historical "join the headers into one line" empty-report rendering are all
 * asserted so that a future change cannot quietly relax one of them.</p>
 *
 * <h3>Orientation is not a free choice here</h3>
 * <p>{@link #landscapeOverride()} is {@code null} by default, meaning the
 * orientation is taken from {@link ExportData#landscape()} exactly as before. The
 * override exists only so a later report can assert its own orientation
 * explicitly; a report that never sets it keeps deciding through the data model,
 * which is where Phase 3 already decided it.</p>
 *
 * <p>Instances are immutable and safe to share.</p>
 */
public record PdfStyleOptions(Boolean landscapeOverride,
                               Float[] pageMargins,
                               Float fontScale,
                               boolean contentWidths,
                               boolean repeatHeaderRows,
                               boolean keepRowsIntact,
                               boolean pageFooter,
                               boolean pageNumbers,
                               boolean footerContext,
                               boolean headerShading,
                               boolean cellBorders,
                               boolean emphasizeTotalRows,
                               boolean emptyReportAsTable,
                               int centerFromColumn,
                               Integer totalPages) {

    /**
     * The pre-Phase-4C PDF, field for field.
     *
     * <p>The values here mirror {@code PdfReportGenerator} as it existed before
     * Phase 4C: orientation from the data, the two historical margin sets, the
     * {@code 6.5f / 9f} font scale chosen by orientation, equal-width columns,
     * repeating header rows, no footer, no page numbers, no borders, no shading,
     * no total-row emphasis, the header-joining empty report, and centring from
     * the third column onwards.</p>
     */
    public static PdfStyleOptions defaults() {
        return new PdfStyleOptions(null, null, null,
                false, true, false, false, false, false, false, false, false, false,
                2, null);
    }

    public PdfStyleOptions {
        if (pageMargins != null) {
            pageMargins = pageMargins.clone();
        }
    }

    /** True when orientation should be forced rather than read from the data. */
    public boolean hasLandscapeOverride() {
        return landscapeOverride != null;
    }

    /** Copy of this configuration with orientation forced. */
    public PdfStyleOptions withLandscape(boolean value) {
        return new PdfStyleOptions(value, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with the four page margins replaced. */
    public PdfStyleOptions withMargins(float left, float right, float top, float bottom) {
        return new PdfStyleOptions(landscapeOverride,
                new Float[]{left, right, top, bottom},
                fontScale, contentWidths, repeatHeaderRows, keepRowsIntact, pageFooter,
                pageNumbers, footerContext, headerShading, cellBorders, emphasizeTotalRows,
                emptyReportAsTable, centerFromColumn, totalPages);
    }

    /** Copy of this configuration with the base table font size replaced. */
    public PdfStyleOptions withFontScale(float value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, value, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with content-derived column widths toggled. */
    public PdfStyleOptions withContentWidths(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, value,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with repeating header rows toggled. */
    public PdfStyleOptions withRepeatHeaderRows(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                value, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with row-integrity toggled. */
    public PdfStyleOptions withKeepRowsIntact(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, value, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with the page footer toggled. */
    public PdfStyleOptions withPageFooter(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, value, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with page numbering toggled. */
    public PdfStyleOptions withPageNumbers(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, value, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with the running context line toggled. */
    public PdfStyleOptions withFooterContext(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, value,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with header shading toggled. */
    public PdfStyleOptions withHeaderShading(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                value, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with cell borders toggled. */
    public PdfStyleOptions withCellBorders(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, value, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with total-row emphasis toggled. */
    public PdfStyleOptions withEmphasizedTotalRows(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, value, emptyReportAsTable,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration with the real-table empty report toggled. */
    public PdfStyleOptions withEmptyReportAsTable(boolean value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, value,
                centerFromColumn, totalPages);
    }

    /** Copy of this configuration declaring the first column that is centred. */
    public PdfStyleOptions withCenterFromColumn(int value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                value, totalPages);
    }

    /**
     * Copy of this configuration carrying the resolved total page count.
     *
     * <p>Set by the second rendering pass only. A {@code null} total means the
     * count is not known yet, which is exactly the state of the first pass.</p>
     */
    public PdfStyleOptions withTotalPages(Integer value) {
        return new PdfStyleOptions(landscapeOverride, pageMargins, fontScale, contentWidths,
                repeatHeaderRows, keepRowsIntact, pageFooter, pageNumbers, footerContext,
                headerShading, cellBorders, emphasizeTotalRows, emptyReportAsTable,
                centerFromColumn, value);
    }
}