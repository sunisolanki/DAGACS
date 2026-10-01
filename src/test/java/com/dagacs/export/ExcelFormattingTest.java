package com.dagacs.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4B: the professional Excel formatting itself, and - just as importantly -
 * that opting out of it changes nothing.
 *
 * <p>The Teacher M7.2 regression lock lives here. {@link ExcelReportGenerator} is
 * shared with every M7.2 department and teacher report, so the test that matters
 * most is not "the new formatting works" but "the defaults are the old
 * behaviour". If a future change made professional formatting the default, every
 * Teacher export would silently change and these tests would fail.</p>
 */
class ExcelFormattingTest {

    private final ExcelReportGenerator generator = new ExcelReportGenerator();

    private static ExportData simpleReport() {
        return new ExportData("Daily-Lecture Attendance Report",
                "Department: X | 2026-01-01 to 2026-01-31",
                "Daily Lecture",
                List.of("Date", "Subject", "Present", "Attendance %"),
                List.of(
                        List.of("2026-01-05", "CS301", 32L, "80.00%"),
                        List.of("2026-01-06", "CS302", 24L, "60.00%"),
                        List.of("2026-01-07", "CS303", 0L, "N/A")));
    }

    private static ExportData wideReport() {
        return new ExportData("Attendance Matrix", "context", "Attendance Matrix",
                List.of("Enrollment No.", "Student Name", "CS301", "CS302",
                        "Total Present", "Total Classes", "Overall Attendance %"),
                List.of(List.of("CS2025001", "Rahul", "32 / 40", "30 / 35",
                        62L, 75L, "82.67%")));
    }

    private Workbook render(ExportData data, ExcelStyleOptions options) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(generator.generate(data, options)));
    }

    private static int headerRowOf(Sheet sheet) {
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            if (sheet.getRow(r) != null && sheet.getRow(r).getCell(0) != null
                    && sheet.getRow(r).getCell(1) != null) {
                return r;
            }
        }
        throw new AssertionError("no header row");
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Teacher M7.2 regression lock")
    class TeacherLock {

        @Test
        @DisplayName("the defaults reproduce the pre-Phase-4B layout exactly")
        void defaultsAreTheOldBehaviour() throws IOException {
            try (Workbook workbook = render(simpleReport(), ExcelStyleOptions.defaults())) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                int headerRow = headerRowOf(sheet);

                // Title, subtitle, then one blank spacer before the header.
                assertEquals(3, headerRow,
                        "the historical heading block is one title, one subtitle "
                                + "and one spacer row");
                assertEquals("Date", sheet.getRow(headerRow).getCell(0).getStringCellValue());
                assertEquals(6, sheet.getLastRowNum(),
                        "three body rows after the heading block and header");

                assertFalse(sheet.getPrintSetup().getLandscape(),
                        "defaults must not silently rotate an existing report");
                assertNull(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet)
                        .getCTWorksheet().getAutoFilter(), "no autofilter by default");
                assertNull(sheet.getPaneInformation(), "no freeze pane by default");
                assertNull(sheet.getRepeatingRows(), "no repeating rows by default");
            }
        }

        @Test
        @DisplayName("generate(data) and the defaults overload are identical")
        void legacyOverloadMatchesDefaults() throws IOException {
            byte[] legacy = generator.generate(simpleReport());
            byte[] viaOptions = generator.generate(simpleReport(), ExcelStyleOptions.defaults());

            try (Workbook a = new XSSFWorkbook(new ByteArrayInputStream(legacy));
                 Workbook b = new XSSFWorkbook(new ByteArrayInputStream(viaOptions))) {
                Sheet left = a.getSheet("Daily Lecture");
                Sheet right = b.getSheet("Daily Lecture");
                assertEquals(left.getLastRowNum(), right.getLastRowNum());
                for (int r = 0; r <= left.getLastRowNum(); r++) {
                    Row leftRow = left.getRow(r);
                    Row rightRow = right.getRow(r);
                    if (leftRow == null || leftRow.getCell(0) == null) {
                        // The spacer row carries no cell at all on either path.
                        assertTrue(rightRow == null || rightRow.getCell(0) == null,
                                "row " + r + " must be absent on both paths");
                        continue;
                    }
                    assertEquals(leftRow.getCell(0).getStringCellValue(),
                            rightRow.getCell(0).getStringCellValue(),
                            "row " + r + " must be identical on both paths");
                }
            }
        }

        @Test
        @DisplayName("the historical freeze overload still freezes")
        void legacyFreezeOverloadStillFreezes() throws IOException {
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                    generator.generate(simpleReport(), true)))) {
                assertNotNull(workbook.getSheet("Daily Lecture").getPaneInformation(),
                        "generate(data, true) must keep freezing the header");
            }
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                    generator.generate(simpleReport())))) {
                assertNull(workbook.getSheet("Daily Lecture").getPaneInformation(),
                        "generate(data) must keep leaving the report unpinned");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Percentage rendering")
    class Percentages {

        @Test
        @DisplayName("a declared percentage column becomes a sortable numeric cell")
        void percentageBecomesNumeric() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults()
                    .withPercentageHeaders("Attendance %");
            try (Workbook workbook = render(simpleReport(), options)) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                int headerRow = headerRowOf(sheet);
                Cell first = sheet.getRow(headerRow + 1).getCell(3);

                assertEquals(CellType.NUMERIC, first.getCellType(),
                        "a HOD must be able to sort this column by value");
                assertEquals(0.80, first.getNumericCellValue(), 0.0001);
                assertEquals("0.00%", first.getCellStyle().getDataFormatString());
                assertEquals("80.00%",
                        new DataFormatter(Locale.ROOT).formatCellValue(first),
                        "the displayed figure must match the Phase 4A text exactly");
            }
        }

        @Test
        @DisplayName("N/A stays N/A: text, never a number, never 0.00%")
        void naStaysNa() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults()
                    .withPercentageHeaders("Attendance %");
            try (Workbook workbook = render(simpleReport(), options)) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                int headerRow = headerRowOf(sheet);
                Cell third = sheet.getRow(headerRow + 3).getCell(3);

                assertEquals(CellType.STRING, third.getCellType(),
                        "no denominator means no percentage exists - not zero");
                assertEquals("N/A", third.getStringCellValue());
                assertFalse(third.getCellStyle().getDataFormatString().contains("%"),
                        "N/A must not carry a percentage format that would imply 0.00%");
            }
        }

        @Test
        @DisplayName("every percentage displays exactly as its Phase 4A string")
        void displayedValuesAreUnchanged() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults()
                    .withPercentageHeaders("Attendance %");
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            try (Workbook workbook = render(simpleReport(), options)) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                int headerRow = headerRowOf(sheet);
                for (int r = 1; r <= 3; r++) {
                    Cell cell = sheet.getRow(headerRow + r).getCell(3);
                    String displayed = formatter.formatCellValue(cell);
                    String original = String.valueOf(
                            simpleReport().rows().get(r - 1).get(3));
                    assertEquals(original, displayed,
                            "row " + r + " must display identically to Phase 4A");
                }
            }
        }

        @Test
        @DisplayName("an undeclared percentage column is left as text")
        void undeclaredColumnsAreUntouched() throws IOException {
            try (Workbook workbook = render(simpleReport(), ExcelStyleOptions.defaults())) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                Cell cell = sheet.getRow(headerRowOf(sheet) + 1).getCell(3);
                assertEquals(CellType.STRING, cell.getCellType(),
                        "opting in per report is what keeps other consumers unchanged");
                assertEquals("80.00%", cell.getStringCellValue());
            }
        }

        @Test
        @DisplayName("only a parseable percentage is converted")
        void onlyPercentagesAreConverted() {
            assertAll(
                    () -> assertEquals(0.8267,
                            ExcelReportGenerator.parsePercentage("82.67%"), 0.0001),
                    () -> assertEquals(0.0,
                            ExcelReportGenerator.parsePercentage("0.00%"), 0.0001),
                    () -> assertEquals(1.0,
                            ExcelReportGenerator.parsePercentage("100.00%"), 0.0001),
                    // A bare percentage is still a percentage, not a sentence.
                    () -> assertEquals(0.80,
                            ExcelReportGenerator.parsePercentage("80%"), 0.0001),
                    // Anything that is not a percentage must survive as text.
                    () -> assertNull(ExcelReportGenerator.parsePercentage("N/A")),
                    () -> assertNull(ExcelReportGenerator.parsePercentage("")),
                    () -> assertNull(ExcelReportGenerator.parsePercentage(null)),
                    () -> assertNull(ExcelReportGenerator.parsePercentage("32 / 40")),
                    () -> assertNull(ExcelReportGenerator.parsePercentage("%")),
                    () -> assertNull(ExcelReportGenerator.parsePercentage("N/A%")),
                    () -> assertNull(ExcelReportGenerator.parsePercentage("about 80%")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Print setup")
    class PrintSetup {

        @Test
        @DisplayName("A4, margins and a print area are applied when asked for")
        void printSetupIsApplied() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults().withPrintSetup(true);
            try (Workbook workbook = render(simpleReport(), options)) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                assertEquals(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE,
                        sheet.getPrintSetup().getPaperSize());
                assertEquals(0.5, sheet.getMargin(Sheet.LeftMargin), 0.001);
                assertNotNull(workbook.getPrintArea(0));
            }
        }

        @Test
        @DisplayName("a wide report is landscape and fits one page wide")
        void wideReportFitsLandscape() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults()
                    .withPrintSetup(true).withLandscape(true).withFitToWidth(true);
            try (Workbook workbook = render(wideReport(), options)) {
                Sheet sheet = workbook.getSheet("Attendance Matrix");
                assertTrue(sheet.getPrintSetup().getLandscape());
                assertEquals(1, sheet.getPrintSetup().getFitWidth());
                assertEquals(0, sheet.getPrintSetup().getFitHeight());
                assertTrue(sheet.getFitToPage());
            }
        }

        @Test
        @DisplayName("the header row repeats on every printed page")
        void headerRepeatsOnPrint() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults()
                    .withPrintSetup(true).withRepeatHeaderRows(true);
            try (Workbook workbook = render(simpleReport(), options)) {
                assertNotNull(workbook.getSheet("Daily Lecture").getRepeatingRows(),
                        "every printed page must keep its column labels");
            }
        }

        @Test
        @DisplayName("nothing is configured when print setup is not requested")
        void printSetupIsOptIn() throws IOException {
            try (Workbook workbook = render(simpleReport(), ExcelStyleOptions.defaults())) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                assertNull(workbook.getPrintArea(0));
                assertNull(sheet.getRepeatingRows());
                assertFalse(sheet.getFitToPage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Autofilter and totals")
    class Navigation {

        @Test
        @DisplayName("an autofilter spans the header and the body")
        void autofilterCoversTheTable() throws IOException {
            ExcelStyleOptions options = ExcelStyleOptions.defaults().withAutoFilter(true);
            try (Workbook workbook = render(simpleReport(), options)) {
                Sheet sheet = workbook.getSheet("Daily Lecture");
                assertNotNull(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet)
                        .getCTWorksheet().getAutoFilter());
            }
        }

        @Test
        @DisplayName("a marked total row is bold and tinted")
        void totalRowIsEmphasized() throws IOException {
            ExportData data = new ExportData("Report", "sub", "Sheet",
                    List.of("Subject", "Present", "Attendance %"),
                    List.of(List.of("CS301", 32L, "80.00%"),
                            List.of("TOTAL", 32L, "80.00%")))
                    .withTotalRows(1);
            ExcelStyleOptions options = ExcelStyleOptions.defaults()
                    .withEmphasizedTotalRows(true);
            try (Workbook workbook = render(data, options)) {
                Sheet sheet = workbook.getSheet("Sheet");
                int headerRow = headerRowOf(sheet);
                Cell total = sheet.getRow(headerRow + 2).getCell(0);
                Cell ordinary = sheet.getRow(headerRow + 1).getCell(0);
                assertTrue(((org.apache.poi.xssf.usermodel.XSSFCellStyle) total.getCellStyle())
                                .getFont().getBold(),
                        "the total must read first");
                assertFalse(((org.apache.poi.xssf.usermodel.XSSFCellStyle) ordinary.getCellStyle())
                        .getFont().getBold());
            }
        }

        @Test
        @DisplayName("no report marks a total by default")
        void totalsAreOptIn() {
            assertTrue(new ExportData("t", "s", "n", List.of("A"), List.of()).totalRowIndices()
                    .isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Spreadsheet safety")
    class Safety {

        @Test
        @DisplayName("dangerous leading characters are neutralised in a real report")
        void dangerousNamesAreNeutralisedInAReport() throws IOException {
            ExportData data = new ExportData("Report", "sub", "Sheet",
                    List.of("Student Name", "Subject"),
                    List.of(
                            List.of("=1+1", "@SUM(A1)"),
                            List.of("+cmd", "-cmd"),
                            List.of("Rahul", "O'Brien"),
                            List.of("Semester 3", "B.Tech CSE")));
            try (Workbook workbook = render(data, ExcelStyleOptions.defaults())) {
                Sheet sheet = workbook.getSheet("Sheet");
                int headerRow = headerRowOf(sheet);
                for (int r = 1; r <= 4; r++) {
                    for (int c = 0; c < 2; c++) {
                        Cell cell = sheet.getRow(headerRow + r).getCell(c);
                        assertEquals(CellType.STRING, cell.getCellType(),
                                "row " + (headerRow + r) + " col " + c
                                        + " must never be a formula");
                        assertNotEqualsFormula(cell);
                    }
                }
                assertEquals("Rahul", sheet.getRow(headerRow + 3).getCell(0)
                        .getStringCellValue(), "an ordinary name is untouched");
                assertEquals("O'Brien", sheet.getRow(headerRow + 3).getCell(1)
                        .getStringCellValue(), "an apostrophe inside a name is untouched");
            }
        }

        private void assertNotEqualsFormula(Cell cell) {
            assertFalse(cell.getCellType() == CellType.FORMULA,
                    "a formula cell would execute on the reader's machine");
        }

        @Test
        @DisplayName("ordinary text is never escaped, trimmed or case-changed")
        void ordinaryTextIsUntouched() {
            assertAll(
                    () -> assertEquals("Rahul", ExcelReportGenerator.safeCellText("Rahul")),
                    () -> assertEquals("CS301", ExcelReportGenerator.safeCellText("CS301")),
                    () -> assertEquals("Semester 3", ExcelReportGenerator.safeCellText("Semester 3")),
                    () -> assertEquals("B.Tech CSE", ExcelReportGenerator.safeCellText("B.Tech CSE")),
                    () -> assertEquals("Computer Science & Engineering",
                            ExcelReportGenerator.safeCellText("Computer Science & Engineering")),
                    () -> assertEquals("O'Brien", ExcelReportGenerator.safeCellText("O'Brien")),
                    () -> assertEquals("M.Tech", ExcelReportGenerator.safeCellText("M.Tech")),
                    () -> assertEquals("", ExcelReportGenerator.safeCellText("")),
                    () -> assertNull(ExcelReportGenerator.safeCellText(null)));
        }

        @Test
        @DisplayName("every documented dangerous leading character is neutralised")
        void allDangerousCharacters() {
            for (char dangerous : new char[]{'=', '+', '-', '@', '\t', '\r'}) {
                String hostile = dangerous + "HYPERLINK(\"http://x\",\"click\")";
                assertEquals("'" + hostile, ExcelReportGenerator.safeCellText(hostile),
                        "leading " + (int) dangerous + " must be neutralised");
            }
        }

        @Test
        @DisplayName("a dangerous character elsewhere in the string is left alone")
        void onlyTheLeadingCharacterMatters() {
            assertAll(
                    () -> assertEquals("CS-301", ExcelReportGenerator.safeCellText("CS-301")),
                    () -> assertEquals("a-b", ExcelReportGenerator.safeCellText("a-b")),
                    () -> assertEquals("x@y", ExcelReportGenerator.safeCellText("x@y")),
                    () -> assertEquals("Dr. Rao", ExcelReportGenerator.safeCellText("Dr. Rao")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-sheet workbooks")
    class MultiSheet {

        @Test
        @DisplayName("each sheet keeps its own name and layout")
        void sheetsKeepTheirOwnLayout() throws IOException {
            ExportWorkbook workbookData = new ExportWorkbook(List.of(
                    ExportWorkbook.sheet("Executive Summary",
                            new ExportData("Summary", "", "Executive Summary",
                                    List.of("Metric", "Value"),
                                    List.of(List.of("Total Students", 3L))),
                            ExcelStyleOptions.defaults().withPrintSetup(true)),
                    ExportWorkbook.sheet("Attendance Matrix", wideReport(),
                            ExcelStyleOptions.defaults().withPrintSetup(true)
                                    .withLandscape(true).withFitToWidth(true))));

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                    generator.generateWorkbook(workbookData)))) {
                assertEquals(2, workbook.getNumberOfSheets());
                assertEquals("Executive Summary", workbook.getSheetName(0));
                assertEquals("Attendance Matrix", workbook.getSheetName(1));
                assertFalse(workbook.getSheetAt(0).getPrintSetup().getLandscape());
                assertTrue(workbook.getSheetAt(1).getPrintSetup().getLandscape(),
                        "a wide sheet must not inherit the narrow one's layout");
            }
        }

        @Test
        @DisplayName("an empty workbook is refused rather than producing a broken file")
        void emptyWorkbookIsRefused() {
            assertAll(
                    () -> assertThrowsIllegalArgument(
                            () -> generator.generateWorkbook(new ExportWorkbook(List.of()))),
                    () -> assertThrowsIllegalArgument(
                            () -> generator.generateWorkbook(null)));
        }

        private void assertThrowsIllegalArgument(Runnable action) {
            try {
                action.run();
                throw new AssertionError("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }
}