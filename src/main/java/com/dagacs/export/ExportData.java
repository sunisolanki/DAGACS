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
 */
public record ExportData(String title, String subtitle, String sheetName,
                         List<String> headers, List<List<Object>> rows) {
}