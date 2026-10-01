package com.dagacs.export;

import java.util.List;

/**
 * Phase 4B: a workbook of several {@link ExportData} sheets, each with its own
 * formatting.
 *
 * <p>Phase 4A established one file per report. The HOD Context Pack needs several
 * reports of the <b>same authorized academic context</b> in one deliverable, so
 * this carries the sheets and their order; the data in each sheet still comes
 * from the canonical Phase 3 report service, and nothing here performs or
 * changes a calculation.</p>
 *
 * <p>Sheet order is the order of {@link #sheets()}, which is what a reader sees
 * as the workbook tabs. It is therefore declared once, here, rather than being
 * implied by whichever service happened to be called.</p>
 *
 * <p>A per-sheet {@link ExcelStyleOptions} is what lets a wide cross-tab be
 * landscape with fit-to-width while a narrow summary in the same workbook stays
 * portrait - layouts that cannot both be right for a single global setting.</p>
 *
 * @param sheets the sheets, in workbook order; never empty
 */
public record ExportWorkbook(List<SheetSpec> sheets) {

    public ExportWorkbook {
        sheets = sheets == null ? List.of() : List.copyOf(sheets);
    }

    /**
     * One sheet of the workbook.
     *
     * @param name    the tab name, sanitised by the generator
     * @param data    the report contents
     * @param options the formatting for this sheet
     */
    public record SheetSpec(String name, ExportData data, ExcelStyleOptions options) {
    }

    /** Convenience factory using the default (pre-Phase-4B) formatting. */
    public static SheetSpec sheet(String name, ExportData data) {
        return new SheetSpec(name, data, ExcelStyleOptions.defaults());
    }

    /** Convenience factory for a sheet with explicit professional formatting. */
    public static SheetSpec sheet(String name, ExportData data, ExcelStyleOptions options) {
        return new SheetSpec(name, data, options);
    }
}