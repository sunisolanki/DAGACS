package com.dagacs.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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
 */
@Component
public class ExcelReportGenerator {

    private static final int MAX_COLUMN_WIDTH_CHARS = 60;
    private static final int IDENTITY_COLUMNS = 2;
    private static final int NARROW_COLUMN_MAX_CHARS = 14;

    public byte[] generate(ExportData data) {
        return generate(data, false);
    }

    public byte[] generate(ExportData data, boolean freezeHeader) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(data.sheetName()));

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle metaStyle = workbook.createCellStyle();
            Font metaFont = workbook.createFont();
            metaFont.setFontHeightInPoints((short) 10);
            metaStyle.setFont(metaFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setWrapText(true);
            border(headerStyle);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setFont(metaFont);
            border(bodyStyle);

            CellStyle centeredStyle = workbook.createCellStyle();
            centeredStyle.setFont(metaFont);
            centeredStyle.setAlignment(HorizontalAlignment.CENTER);
            border(centeredStyle);

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(data.title());
            titleCell.setCellStyle(titleStyle);

            if (data.subtitle() != null && !data.subtitle().isBlank()) {
                Row subtitleRow = sheet.createRow(rowIndex++);
                subtitleRow.createCell(0).setCellValue(data.subtitle());
            }

            if (data.metaLines() != null) {
                for (String line : data.metaLines()) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    Row metaRow = sheet.createRow(rowIndex++);
                    metaRow.createCell(0).setCellValue(line);
                }
            }

            rowIndex++;
            int headerRowIndex = rowIndex;
            Row headerRow = sheet.createRow(rowIndex++);
            for (int c = 0; c < data.headers().size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(data.headers().get(c));
                cell.setCellStyle(headerStyle);
            }

            int columnCount = data.headers().size();
            // Attendance marks and totals are centre aligned; identity columns
            // stay left aligned for readability.
            for (List<Object> rowData : data.rows()) {
                Row excelRow = sheet.createRow(rowIndex++);
                for (int c = 0; c < rowData.size(); c++) {
                    Cell cell = excelRow.createCell(c);
                    Object value = rowData.get(c);
                    if (value == null) {
                        // A genuine blank cell, not an empty string, so Excel
                        // shows a gap rather than the text "".
                        cell.setBlank();
                    } else if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                    cell.setCellStyle(c < IDENTITY_COLUMNS && columnCount > IDENTITY_COLUMNS
                            ? bodyStyle
                            : centeredStyle);
                }
            }

            applyColumnWidths(sheet, data);
            if (freezeHeader) {
                // Pin the header row and the two identity columns so the date
                // columns can scroll without losing the student identity.
                sheet.createFreezePane(IDENTITY_COLUMNS, headerRowIndex + 1);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
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
