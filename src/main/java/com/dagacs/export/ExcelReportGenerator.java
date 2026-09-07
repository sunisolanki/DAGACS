package com.dagacs.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
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
 * valid .xlsx workbook in memory: title row, subtitle row, header row, then one
 * row per report row. Excel is the SOW-stated required export format; this class
 * deliberately keeps POI construction isolated from report data retrieval.
 */
@Component
public class ExcelReportGenerator {

    private static final int MAX_COLUMN_WIDTH_CHARS = 60;

    public byte[] generate(ExportData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(data.sheetName()));

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(data.title());
            titleCell.setCellStyle(titleStyle);

            if (data.subtitle() != null && !data.subtitle().isBlank()) {
                Row subtitleRow = sheet.createRow(rowIndex++);
                subtitleRow.createCell(0).setCellValue(data.subtitle());
            }

            rowIndex++;
            Row headerRow = sheet.createRow(rowIndex++);
            for (int c = 0; c < data.headers().size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(data.headers().get(c));
                cell.setCellStyle(headerStyle);
            }

            for (List<Object> rowData : data.rows()) {
                Row excelRow = sheet.createRow(rowIndex++);
                for (int c = 0; c < rowData.size(); c++) {
                    Cell cell = excelRow.createCell(c);
                    Object value = rowData.get(c);
                    if (value == null) {
                        cell.setCellValue((String) null);
                    } else if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            for (int c = 0; c < data.headers().size(); c++) {
                int maxChars = data.headers().get(c).length();
                for (List<Object> rowData : data.rows()) {
                    if (c < rowData.size() && rowData.get(c) != null) {
                        maxChars = Math.max(maxChars, rowData.get(c).toString().length());
                    }
                }
                int widthChars = Math.min(MAX_COLUMN_WIDTH_CHARS, maxChars + 2);
                sheet.setColumnWidth(c, widthChars * 256);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
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