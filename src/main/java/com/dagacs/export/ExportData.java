package com.dagacs.export;

import java.util.List;

/**
 * Generic, format-agnostic report table consumed by both the Excel and PDF
 * generators (M7.2). Report data is always retrieved from the frozen report /
 * analytics data sources and mapped into this model by
 * {@link ReportExportService}; generators never touch repositories or business
 * rules.
 *
 * @param title     human-readable report title
 * @param subtitle  scope/metadata line (e.g. department, teacher, date range)
 * @param sheetName worksheet name used by the Excel generator
 * @param headers   human-readable column headers (no internal DB IDs)
 * @param rows      cell values aligned to {@code headers}
 * @param metaLines additional metadata lines rendered under the subtitle, in
 *                  order. Empty for reports that only need a title + subtitle.
 * @param landscape whether the PDF should use a landscape page. Set explicitly
 *                  by the report rather than inferred from the column count, so
 *                  a narrow report is never silently rotated and a wide one is
 *                  never silently clipped.
 */
public record ExportData(String title, String subtitle, String sheetName,
                         List<String> headers, List<List<Object>> rows,
                         List<String> metaLines, boolean landscape) {

    /** Convenience constructor for reports with no extra metadata lines. */
    public ExportData(String title, String subtitle, String sheetName,
                      List<String> headers, List<List<Object>> rows) {
        this(title, subtitle, sheetName, headers, rows, List.of(), false);
    }

    /** Convenience constructor for reports that only need extra metadata lines. */
    public ExportData(String title, String subtitle, String sheetName,
                      List<String> headers, List<List<Object>> rows,
                      List<String> metaLines) {
        this(title, subtitle, sheetName, headers, rows, metaLines, false);
    }
}
