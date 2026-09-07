package com.dagacs.export;

/**
 * Supported M7.2 export formats. Chosen by the two path-based export endpoints
 * ({@code .xlsx} / {@code .pdf}); no client-supplied format string is trusted.
 */
public enum ExportFormat {
    XLSX,
    PDF
}