package com.dagacs.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDataFormat;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal, dependency-free (POI-only) XLSX builder for M7.2 exports. Produces a
 * valid .xlsx workbook in memory: title row, subtitle row, any additional
 * metadata lines, header row, then one row per report row. Excel is the
 * SOW-stated required export format; this class deliberately keeps POI
 * construction isolated from report data retrieval.
 *
 * <p>{@code freezeHeader} opts a report into a frozen header row and frozen
 * leading identity columns (used by the student-wise attendance register) while
 * every other report keeps its current, unpinned layout.
 *
 * <h3>Phase 4B - professional formatting</h3>
 * <p>Every professional feature is opt-in through {@link ExcelStyleOptions}, whose
 * {@link ExcelStyleOptions#defaults()} reproduces the layout above exactly. The
 * two pre-existing signatures keep their behaviour by delegating to those
 * defaults, so the Teacher M7.2 reports - which share this generator - cannot be
 * affected by a Phase 4B feature being enabled elsewhere. That is a structural
 * guarantee rather than a promise, which is why the options are a record with a
 * single {@code defaults()} factory instead of a set of setters.</p>
 *
 * <p>Opt-in additions: A4 print setup with margins and a print area, repeating
 * print header rows, fit-to-width for wide reports, autofilter on list-shaped
 * reports, real numeric percentage cells, thousands-separated counts, landscape
 * orientation in the spreadsheet (previously honoured by the PDF only), and
 * emphasis on total rows.</p>
 *
 * <h3>Where percentages are converted</h3>
 * <p>A declared percentage column is written as a genuine numeric cell with an
 * Excel percentage format, so the value sorts and filters as a number. The
 * conversion happens <b>here, at the rendering layer</b>: the {@link ExportData}
 * model still carries the same {@code "80.00%"} text the Phase 4A reports
 * produced, so the API, the DTOs and every model-level assertion are untouched.
 * The displayed result is identical - {@code 0.80} formatted as {@code 0.00%}
 * reads as {@code 80.00%}.</p>
 *
 * <p><b>{@code "N/A"} is never converted.</b> A percentage the Phase 3 resolver
 * could not compute - no conducted class, so no denominator - stays the literal
 * text {@code N/A}. It is never written as a number and never as
 * {@code 0.00%}.</p>
 *
 * <h3>Spreadsheet safety</h3>
 * <p>Every string cell is written through {@link #safeCellText(String)}, which
 * neutralises a leading {@code = + - @} (and tab/CR) so a user-controlled name
 * cannot be interpreted as a formula by Excel. <b>Ordinary text is untouched</b>:
 * a value that does not start with one of those characters is written verbatim,
 * with no escaping, truncation or case change.</p>
 */
@Component
public class ExcelReportGenerator {

    private static final int MAX_COLUMN_WIDTH_CHARS = 60;
    private static final int IDENTITY_COLUMNS = 2;
    private static final int NARROW_COLUMN_MAX_CHARS = 14;

    /** Excel percentage format used for a declared percentage column. */
    private static final String PERCENT_FORMAT = "0.00%";

    /** Excel integer format used for a declared count column. */
    private static final String INTEGER_FORMAT = "#,##0";

    /** A4 print margins, in inches. */
    private static final double PRINT_MARGIN = 0.5;

    public byte[] generate(ExportData data) {
        return generate(data, ExcelStyleOptions.defaults());
    }

    public byte[] generate(ExportData data, boolean freezeHeader) {
        return generate(data, ExcelStyleOptions.defaults().withFreeze(freezeHeader));
    }

    /**
     * Renders one report with the professional formatting it asked for.
     *
     * <p>This is the only overload that applies Phase 4B formatting; the two
     * signatures above reach it with the defaults that reproduce the historical
     * layout.</p>
     */
    public byte[] generate(ExportData data, ExcelStyleOptions options) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(data.sheetName()));
            renderSheet(workbook, sheet, data, options);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    /**
     * Renders every sheet of a multi-sheet workbook into one .xlsx file.
     *
     * <p>Phase 4B, for the HOD Context Pack. Each sheet carries its own data and
     * its own options, so a wide cross-tab and a narrow summary can live in one
     * workbook with the layout each needs.</p>
     */
    public byte[] generateWorkbook(ExportWorkbook workbookData) {
        if (workbookData == null || workbookData.sheets().isEmpty()) {
            throw new IllegalArgumentException("A workbook must contain at least one sheet");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (ExportWorkbook.SheetSpec spec : workbookData.sheets()) {
                Sheet sheet = workbook.createSheet(sanitizeSheetName(spec.name()));
                renderSheet(workbook, sheet, spec.data(), spec.options());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    /**
     * Writes one sheet: heading block, header row, body rows, then the opt-in
     * print and navigation features.
     *
     * <p>Shared by the single-sheet and multi-sheet paths, so both produce
     * identical output for identical options.</p>
     */
    private void renderSheet(XSSFWorkbook workbook, Sheet sheet, ExportData data,
                             ExcelStyleOptions options) {
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        Font metaFont = workbook.createFont();
        metaFont.setFontHeightInPoints((short) 10);

        CellStyle metaStyle = workbook.createCellStyle();
        metaStyle.setFont(metaFont);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setWrapText(true);
        border(headerStyle);

        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setFont(metaFont);
        border(bodyStyle);

        CellStyle centeredStyle = workbook.createCellStyle();
        centeredStyle.setFont(metaFont);
        centeredStyle.setAlignment(HorizontalAlignment.CENTER);
        border(centeredStyle);

        // Phase 4B numeric styles. Built only when a report declares the column,
        // so a report that declares none allocates nothing extra.
        XSSFDataFormat formats = workbook.createDataFormat();
        CellStyle percentStyle = options.percentageHeaders().isEmpty()
                ? null : numericStyle(workbook, centeredStyle, formats.getFormat(PERCENT_FORMAT));
        CellStyle integerStyle = options.integerHeaders().isEmpty()
                ? null : numericStyle(workbook, centeredStyle, formats.getFormat(INTEGER_FORMAT));

        // Phase 4B total-row emphasis.
        CellStyle totalBodyStyle = null;
        CellStyle totalCenteredStyle = null;
        if (options.emphasizeTotalRows() && !data.totalRowIndices().isEmpty()) {
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 10);
            short tint = IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex();
            totalBodyStyle = workbook.createCellStyle();
            totalBodyStyle.setFont(totalFont);
            totalBodyStyle.setFillForegroundColor(tint);
            totalBodyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(totalBodyStyle);
            totalCenteredStyle = workbook.createCellStyle();
            totalCenteredStyle.setFont(totalFont);
            totalCenteredStyle.setAlignment(HorizontalAlignment.CENTER);
            totalCenteredStyle.setFillForegroundColor(tint);
            totalCenteredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(totalCenteredStyle);
        }

        int rowIndex = 0;
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(safeCellText(data.title()));
        titleCell.setCellStyle(titleStyle);

        if (data.subtitle() != null && !data.subtitle().isBlank()) {
            Row subtitleRow = sheet.createRow(rowIndex++);
            subtitleRow.createCell(0).setCellValue(safeCellText(data.subtitle()));
        }

        if (data.metaLines() != null) {
            for (String line : data.metaLines()) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                Row metaRow = sheet.createRow(rowIndex++);
                metaRow.createCell(0).setCellValue(safeCellText(line));
            }
        }

        rowIndex++;
        int headerRowIndex = rowIndex;
        int columnCount = data.headers().size();
        Row headerRow = sheet.createRow(rowIndex++);
        for (int c = 0; c < columnCount; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(safeCellText(data.headers().get(c)));
            cell.setCellStyle(headerStyle);
        }

        Set<Integer> totalRows = new HashSet<>(data.totalRowIndices());

        for (int r = 0; r < data.rows().size(); r++) {
            List<Object> rowData = data.rows().get(r);
            Row excelRow = sheet.createRow(rowIndex++);
            boolean isTotal = totalRows.contains(r);
            for (int c = 0; c < rowData.size(); c++) {
                Cell cell = excelRow.createCell(c);
                boolean leftAligned = c < IDENTITY_COLUMNS && columnCount > IDENTITY_COLUMNS;
                // The base style for this position, which writeCell may replace
                // when the column is a declared numeric one.
                CellStyle baseStyle = isTotal
                        ? (leftAligned ? totalBodyStyle : totalCenteredStyle)
                        : (leftAligned ? bodyStyle : centeredStyle);
                cell.setCellStyle(writeCell(cell, rowData.get(c), c, data, options,
                        percentStyle, integerStyle, baseStyle));
            }
        }
        int lastRowIndex = rowIndex - 1;

        applyColumnWidths(sheet, data);
        if (options.freezeHeader()) {
            sheet.createFreezePane(options.freezeIdentityColumns(), headerRowIndex + 1);
        }
        if (options.autoFilter() && lastRowIndex >= headerRowIndex) {
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, lastRowIndex, 0,
                    Math.max(columnCount - 1, 0)));
        }
        if (options.printSetup()) {
            applyPrintSetup(workbook, sheet, columnCount, lastRowIndex, headerRowIndex, options);
        }
    }

    /**
     * Writes one body cell and returns the style it should carry.
     *
     * <p>A declared percentage column is written as a real numeric cell; the
     * conversion is deliberately last-resort, so anything that is not a
     * parseable percentage - most importantly {@code "N/A"} - is written as the
     * ordinary text it already was, and keeps {@code baseStyle}.</p>
     */
    private CellStyle writeCell(Cell cell, Object value, int column, ExportData data,
                                ExcelStyleOptions options, CellStyle percentStyle,
                                CellStyle integerStyle, CellStyle baseStyle) {
        if (value == null) {
            // A genuine blank cell, not an empty string, so Excel shows a gap
            // rather than the text "".
            cell.setBlank();
            return baseStyle;
        }
        String header = column < data.headers().size() ? data.headers().get(column) : null;
        boolean percentColumn = percentStyle != null
                && header != null && options.percentageHeaders().contains(header);

        if (value instanceof Number number) {
            if (percentColumn) {
                // A raw fraction supplied directly by a report.
                cell.setCellValue(number.doubleValue() / 100.0);
                return percentStyle;
            }
            cell.setCellValue(number.doubleValue());
            if (integerStyle != null && header != null
                    && options.integerHeaders().contains(header)) {
                return integerStyle;
            }
            return baseStyle;
        }

        String text = value.toString();
        if (percentColumn) {
            Double fraction = parsePercentage(text);
            if (fraction != null) {
                cell.setCellValue(fraction);
                return percentStyle;
            }
            // "N/A", or anything else unparseable: stays ordinary text. Never 0.
        }
        cell.setCellValue(safeCellText(text));
        return baseStyle;
    }

    /**
     * Parses a rendered percentage such as {@code "82.67%"} into the fraction
     * Excel stores ({@code 0.8267}), or null when it is not a percentage.
     *
     * <p>Returning null for {@code "N/A"} is what keeps the Phase 3 "no
     * denominator means N/A" rule intact all the way to the rendered cell.</p>
     */
    static Double parsePercentage(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || !trimmed.endsWith("%")) {
            return null;
        }
        String numeric = trimmed.substring(0, trimmed.length() - 1).trim();
        if (numeric.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(numeric)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .doubleValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Neutralises spreadsheet formula injection without touching ordinary text.
     *
     * <p>A leading {@code =}, {@code +}, {@code -} or {@code @} makes Excel treat
     * a cell as a formula, which is how a student or subject named
     * {@code =cmd|...} could execute on a reader's machine. Prefixing an
     * apostrophe marks the value as literal text; Excel hides the marker.</p>
     *
     * <p>Only the leading character is considered and only these characters
     * trigger it, so {@code "Rahul"}, {@code "O'Brien"}, {@code "Semester 3"} and
     * every other legitimate value is returned unchanged.</p>
     */
    static String safeCellText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char first = text.charAt(0);
        boolean dangerous = first == '=' || first == '+' || first == '-'
                || first == '@' || first == '\t' || first == '\r';
        return dangerous ? "'" + text : text;
    }

    /** A numeric style cloned from {@code base} with the given number format. */
    private static CellStyle numericStyle(XSSFWorkbook workbook, CellStyle base, short format) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(base);
        style.setDataFormat(format);
        return style;
    }

    /**
     * Applies the opt-in print configuration.
     *
     * <p>Fit-to-width lets a wide cross-tab print at a readable size across one
     * page width, and the repeating header row means every printed page keeps its
     * column labels - the difference between a usable printout and a wall of
     * numbers.</p>
     */
    private void applyPrintSetup(XSSFWorkbook workbook, Sheet sheet, int columnCount,
                                 int lastRowIndex, int headerRowIndex,
                                 ExcelStyleOptions options) {
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        // Phase 4B: the spreadsheet honours orientation too, where previously
        // only the PDF did. A wide cross-tab is unreadable in portrait.
        printSetup.setLandscape(options.landscape());

        if (options.fitToWidth()) {
            printSetup.setFitWidth((short) 1);
            printSetup.setFitHeight((short) 0);
            sheet.setFitToPage(true);
        }

        sheet.setMargin(Sheet.LeftMargin, PRINT_MARGIN);
        sheet.setMargin(Sheet.RightMargin, PRINT_MARGIN);
        sheet.setMargin(Sheet.TopMargin, PRINT_MARGIN);
        sheet.setMargin(Sheet.BottomMargin, PRINT_MARGIN);

        if (options.repeatHeaderRows() && lastRowIndex >= headerRowIndex) {
            sheet.setRepeatingRows(new CellRangeAddress(headerRowIndex, headerRowIndex, -1, -1));
        }
        if (columnCount > 0 && lastRowIndex >= 0) {
            workbook.setPrintArea(workbook.getSheetIndex(sheet),
                    0, columnCount - 1, 0, lastRowIndex);
        }
    }

    private static void border(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void applyColumnWidths(Sheet sheet, ExportData data) {
        int columnCount = data.headers().size();
        for (int c = 0; c < columnCount; c++) {
            int maxChars = data.headers().get(c).length();
            for (List<Object> rowData : data.rows()) {
                if (c < rowData.size() && rowData.get(c) != null) {
                    maxChars = Math.max(maxChars, rowData.get(c).toString().length());
                }
            }
            int cap = (c >= IDENTITY_COLUMNS && columnCount > IDENTITY_COLUMNS)
                    ? NARROW_COLUMN_MAX_CHARS
                    : MAX_COLUMN_WIDTH_CHARS;
            int widthChars = Math.min(cap, maxChars + 2);
            sheet.setColumnWidth(c, widthChars * 256);
        }
    }

    private static String sanitizeSheetName(String name) {
        String clean = name == null ? "Report"
                : name.replaceAll("[\\[\\]:*?/\\\\]", "").trim();
        if (clean.isBlank()) {
            clean = "Report";
        }
        return clean.length() > 31 ? clean.substring(0, 31) : clean;
    }
}