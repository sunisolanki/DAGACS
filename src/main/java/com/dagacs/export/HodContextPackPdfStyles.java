package com.dagacs.export;

import java.util.List;

/**
 * Phase 4C.3: the professional PDF layout the HOD Context Pack asks for, and the
 * order its five sections are printed in.
 *
 * <p>This is the single place the Pack's PDF presentation is decided, mirroring
 * the existing {@link HodReportPdfStyles} (which does the same for the five
 * standalone HOD PDFs) and {@link HodReportStyles} (which does it for the
 * spreadsheet). Keeping it in one class is what stops the footer and layout rules
 * being re-invented inside the export service.</p>
 *
 * <h3>Same five questions, same order, as the workbook</h3>
 * <p>The PDF repeats the spreadsheet's section order exactly, and both are built
 * from the same {@link com.dagacs.export.ExportData} instances, so the two files
 * cannot disagree about a single number:</p>
 * <ol>
 *   <li>Executive Summary - how is the cohort doing</li>
 *   <li>Attendance Matrix - the full cross-tab</li>
 *   <li>Low Attendance - who is at risk and why</li>
 *   <li>Subject Summary - subject by subject</li>
 *   <li>Student Summary - per student</li>
 * </ol>
 *
 * <h3>Orientation: the data model already decided it</h3>
 * <p>No preset sets a landscape override. The cross-tab is the only wide report
 * and {@code ExportData.landscape()} is already {@code true} for exactly that
 * sheet, so the Matrix section is landscape and the other four are portrait
 * without a second source of truth. That matters because a real mixed-orientation
 * PDF cannot be produced by one OpenPDF document - see
 * {@link PdfSectionedReportGenerator} - and keeping orientation in the data model
 * is what lets each section simply be rendered on its own size.</p>
 *
 * <h3>What every Pack PDF gains over five loose files</h3>
 * <ul>
 *   <li>one file, one continuous {@code Page X of Y} across all five sections;</li>
 *   <li>a running footer naming the pack and the section on every page;</li>
 *   <li>each section starting on its own page;</li>
 *   <li>the same column widths, borders, shaded headers, intact rows, emphasised
 *       totals and explicit empty state the standalone 4C.2 HOD PDFs already have.</li>
 * </ul>
 *
 * <p>None of this touches a section's data, columns, values, headers, branding,
 * academic context or date range: those are assembled upstream and are identical to
 * the workbook's.</p>
 */
final class HodContextPackPdfStyles {

    private HodContextPackPdfStyles() {
    }

    /** The label prefix every page of the Pack carries in its running footer. */
    private static final String PACK_LABEL = "Attendance Context Pack";

    /**
     * The layout shared by every Pack section.
     *
     * <p>The same opt-in set the standalone 4C.2 HOD PDFs use, so a section printed
     * here looks like the report a HOD already trusts, only inside the Pack.</p>
     */
    private static PdfStyleOptions sectionLayout() {
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

    /**
     * The five sections, in workbook order.
     *
     * <p>Declared here so the PDF cannot quietly print a different order, or a
     * different number of sections, than the spreadsheet it accompanies.</p>
     */
    static List<PdfSectionedReportGenerator.PdfSection> sections(
            List<ExportData> sectionData) {
        return java.util.stream.IntStream.range(0, sectionData.size())
                .mapToObj(i -> {
                    ExportData data = sectionData.get(i);
                    return new PdfSectionedReportGenerator.PdfSection(
                            PACK_LABEL + " - " + data.sheetName(), data, sectionLayout());
                })
                .toList();
    }
}
