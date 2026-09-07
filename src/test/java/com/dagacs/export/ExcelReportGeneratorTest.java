package com.dagacs.export;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the M7.2 XLSX generator (valid workbook, meaningful headers,
 * data present, blank cells for null values).
 */
class ExcelReportGeneratorTest {

    private final ExcelReportGenerator generator = new ExcelReportGenerator();

    @Test
    void generate_emptyRows_producesWorkbookWithTitleSubtitleAndHeaders() throws Exception {
        byte[] bytes = generator.generate(new ExportData(
                "Daily Report", "Department: X | 2026-01-01 to 2026-01-31", "Daily Lecture",
                List.of("Subject Code", "Present", "Total"), List.of()));

        assertTrue(bytes.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Daily Lecture"));
            assertEquals("Daily Report",
                    workbook.getSheet("Daily Lecture").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Department: X | 2026-01-01 to 2026-01-31",
                    workbook.getSheet("Daily Lecture").getRow(1).getCell(0).getStringCellValue());
            assertEquals("Subject Code",
                    workbook.getSheet("Daily Lecture").getRow(3).getCell(0).getStringCellValue());
            // Empty data -> header row (index 3) is the last row.
            assertEquals(3, workbook.getSheet("Daily Lecture").getLastRowNum());
        }
    }

    @Test
    void generate_populatedRows_writesValuesAndBlankNullCells() throws Exception {
        byte[] bytes = generator.generate(new ExportData(
                "Subject Report", "all dates", "Subject-wise",
                List.of("Subject Code", "Present", "Total", "Percentage (%)"),
                java.util.Arrays.asList(
                        List.of("S001", 1L, 2L, 50.0),
                        java.util.Arrays.asList("S002", 0L, 0L, null))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Subject-wise");
            assertEquals(5, sheet.getLastRowNum());
            assertEquals("S001", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(1.0, sheet.getRow(4).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(50.0, sheet.getRow(4).getCell(3).getNumericCellValue(), 0.001);
            assertEquals("S002", sheet.getRow(5).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(5).getCell(3).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);
        }
    }
}