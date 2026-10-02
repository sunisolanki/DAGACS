package com.dagacs.export;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4C.1: the PDF layout options, and above all the guarantee that opting
 * <i>out</i> changes nothing.
 *
 * <p>{@link PdfReportGenerator} is shared infrastructure. It renders the HOD
 * attendance PDFs and, through {@code ReportExportService}, every pre-existing
 * HOD M7.2 department report and every Teacher export. A change to a shared
 * default would silently reformat all of them, which is precisely the drift
 * Phase 4A forbade for export routing and which this phase forbids for
 * rendering.</p>
 *
 * <p>So the majority of this class is not testing the new features - it is
 * <b>pinning the defaults</b>. {@code PdfStyleOptions.defaults()} is asserted
 * field by field, and {@code generate(data)} is asserted to be indistinguishable
 * from {@code generate(data, defaults())}. If a future change relaxes any of
 * these, the failure message says which one.</p>
 *
 * <p>Page-number rendering is exercised here too, because the two-pass mechanism
 * it depends on is 4C.1 infrastructure; 4C.2 is what wires it to the HOD reports.</p>
 */
class PdfFormattingTest {

    private final PdfReportGenerator generator = new PdfReportGenerator();

    private static ExportData simpleReport() {
        return new ExportData("My Report", "Teacher: A | all dates", "Sheet",
                List.of("Subject Code", "Subject Name", "Present", "Attendance %"),
                List.of(
                        List.of("CS301", "Data Structures and Algorithms", 32L, "80.00%"),
                        List.of("CS302", "Computer Networks", 24L, "60.00%"),
                        List.of("CS303", "Theory of Computation", 30L, "N/A")));
    }

    private static ExportData wideReport(int subjectCount) {
        List<String> headers = new ArrayList<>(List.of("Enrollment No.", "Student Name"));
        for (int i = 0; i < subjectCount; i++) {
            headers.add("CS" + (300 + i) + " - Subject " + i);
        }
        headers.addAll(List.of("Total Present", "Total Classes", "Overall Attendance %"));

        List<Object> row = new ArrayList<>(List.of("CS2025001", "Rahul Sharma"));
        for (int i = 0; i < subjectCount; i++) {
            row.add("32 / 40");
        }
        row.addAll(List.of(960L, 1200L, "80.00%"));

        return new ExportData("Attendance Matrix", "context", "Sheet", headers,
                List.of(row), List.of("Program: B.Tech CSE"), true);
    }

    private static String magic(byte[] bytes) {
        return new String(Arrays.copyOf(bytes, 5), StandardCharsets.US_ASCII);
    }

    private static int pageCount(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }

    private static List<String> pageTexts(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            List<String> texts = new ArrayList<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                texts.add(extractor.getTextFromPage(page));
            }
            return texts;
        } finally {
            reader.close();
        }
    }

    private static String allText(byte[] bytes) throws Exception {
        return String.join("\n", pageTexts(bytes));
    }

    private static boolean isLandscape(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
        try {
            var size = reader.getPageSize(1);
            return size.getWidth() > size.getHeight();
        } finally {
            reader.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PdfStyleOptions.defaults() is pinned to the pre-4C behaviour")
    class DefaultsAreFrozen {

        @Test
        @DisplayName("every field equals the historical value")
        void defaultsFieldByField() {
            PdfStyleOptions defaults = PdfStyleOptions.defaults();

            assertAll(
                    // Orientation is still taken from the data, not from options.
                    () -> assertFalse(defaults.hasLandscapeOverride()),
                    () -> assertEquals(null, defaults.pageMargins()),
                    () -> assertEquals(null, defaults.fontScale()),
                    // Equal-width columns: the historical behaviour.
                    () -> assertFalse(defaults.contentWidths()),
                    // The header row already repeated before Phase 4C.
                    () -> assertTrue(defaults.repeatHeaderRows()),
                    // OpenPDF's default lets a row split; that was the behaviour.
                    () -> assertFalse(defaults.keepRowsIntact()),
                    // No footer of any kind existed before Phase 4C.
                    () -> assertFalse(defaults.pageFooter()),
                    () -> assertFalse(defaults.pageNumbers()),
                    () -> assertFalse(defaults.footerContext()),
                    () -> assertFalse(defaults.headerShading()),
                    () -> assertFalse(defaults.cellBorders()),
                    () -> assertFalse(defaults.emphasizeTotalRows()),
                    // Headers were joined into one line rather than tabulated.
                    () -> assertFalse(defaults.emptyReportAsTable()),
                    // Centring started at the third column.
                    () -> assertEquals(2, defaults.centerFromColumn()),
                    () -> assertEquals(null, defaults.totalPages()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Teacher and M7.2 compatibility")
    class SharedGeneratorCompatibility {

        @Test
        @DisplayName("generate(data) is byte-structurally identical to the defaults overload")
        void legacyOverloadMatchesDefaults() throws Exception {
            ExportData data = simpleReport();
            byte[] legacy = generator.generate(data);
            byte[] viaOptions = generator.generate(data, PdfStyleOptions.defaults());

            assertEquals(magic(legacy), "%PDF-");
            assertEquals(pageCount(legacy), pageCount(viaOptions),
                    "the page count must not change merely by using the options overload");
            assertEquals(isLandscape(legacy), isLandscape(viaOptions),
                    "orientation must not change merely by using the options overload");
            assertEquals(allText(legacy), allText(viaOptions),
                    "the extracted text must be identical on both paths");
        }

        @Test
        @DisplayName("defaults render no footer and no page numbers")
        void defaultsHaveNoFooter() throws Exception {
            String text = allText(generator.generate(simpleReport()));
            assertAll(
                    () -> assertFalse(text.contains("Page 1 of"),
                            "no page numbering existed before Phase 4C"),
                    () -> assertFalse(text.contains("of 1\n"), "no total page count either"));
        }

        @Test
        @DisplayName("defaults use equal column widths")
        void defaultsUseEqualWidths() throws Exception {
            // The historical table called no setWidths, so every column shares the
            // page equally. Opting into content widths is what changes that.
            byte[] defaults = generator.generate(simpleReport());
            byte[] contentWidths = generator.generate(simpleReport(),
                    PdfStyleOptions.defaults().withContentWidths(true));
            assertEquals(pageCount(defaults), pageCount(contentWidths),
                    "both must be valid documents");
            assertNotEquals(new String(defaults), new String(contentWidths),
                    "content-derived widths must actually change the rendering");
        }

        @Test
        @DisplayName("orientation still comes from the data model")
        void orientationComesFromTheData() throws Exception {
            ExportData portrait = new ExportData("R", "s", "Sheet",
                    List.of("A", "B"), List.of(List.of("1", "2")), List.of(), false);
            ExportData landscape = new ExportData("R", "s", "Sheet",
                    List.of("A", "B"), List.of(List.of("1", "2")), List.of(), true);

            assertFalse(isLandscape(generator.generate(portrait)),
                    "a portrait report must stay portrait");
            assertTrue(isLandscape(generator.generate(landscape)),
                    "the wide Teacher student-wise register must stay landscape");
        }

        @Test
        @DisplayName("an explicit orientation override wins over the data")
        void orientationOverride() throws Exception {
            ExportData portraitFlagged = new ExportData("R", "s", "Sheet",
                    List.of("A", "B"), List.of(List.of("1", "2")), List.of(), false);
            byte[] forced = generator.generate(portraitFlagged,
                    PdfStyleOptions.defaults().withLandscape(true));
            assertTrue(isLandscape(forced),
                    "an explicit override must be honoured when a report sets one");
        }

        @Test
        @DisplayName("the empty report still renders its headers as one joined line")
        void emptyReportKeepsHistoricalRendering() throws Exception {
            byte[] bytes = generator.generate(new ExportData("Empty Report", "all dates",
                    "Sheet", List.of("Subject Code", "Present"), List.of()));
            String text = allText(bytes);
            // The PdfReportGeneratorTest assertion, pinned here too so a change of
            // strategy is a deliberate one rather than an accident.
            assertTrue(text.contains("Empty Report"));
            assertTrue(text.contains("Subject Code"));
            assertFalse(text.contains("No rows for the selected criteria."),
                    "the historical empty rendering has no table and therefore no notice row");
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Page footer and Page X of Y")
    class PageFooter {

        @Test
        @DisplayName("a footer is absent unless it is requested")
        void noFooterByDefault() throws Exception {
            assertFalse(allText(generator.generate(simpleReport())).contains("Page 1 of 1"));
        }

        @Test
        @DisplayName("a single-page report still reads Page 1 of 1")
        void singlePageReadsOneOfOne() throws Exception {
            byte[] bytes = generator.generate(simpleReport(),
                    PdfStyleOptions.defaults()
                            .withPageFooter(true)
                            .withPageNumbers(true)
                            .withTotalPages(1));
            assertTrue(allText(bytes).contains("Page 1 of 1"),
                    "a reader must be able to tell a one-page document from a truncated one");
        }

        @Test
        @DisplayName("the running context line can be shown with the page number")
        void contextLineIsRendered() throws Exception {
            byte[] bytes = generator.generate(simpleReport(),
                    PdfStyleOptions.defaults()
                            .withPageFooter(true)
                            .withPageNumbers(true)
                            .withFooterContext(true)
                            .withTotalPages(1));
            String text = allText(bytes);
            assertTrue(text.contains("Page 1 of 1"));
            assertTrue(text.contains("My Report"),
                    "the footer must identify the report on every page");
        }

        @Test
        @DisplayName("the footer does not appear on the body flow of page one")
        void footerStaysOutOfTheBody() throws Exception {
            // The footer is drawn from onEndPage into the margin, so the heading
            // block still starts at the top of page one and the table is not
            // pushed down by it.
            byte[] withoutFooter = generator.generate(simpleReport());
            byte[] withFooter = generator.generate(simpleReport(),
                    PdfStyleOptions.defaults()
                            .withPageFooter(true).withPageNumbers(true)
                            .withTotalPages(1));
            assertEquals(pageCount(withoutFooter), pageCount(withFooter),
                    "adding a footer must never repaginate the document");
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Two-pass page counting")
    class TwoPass {

        /** A report tall enough to need more than one page. */
        private ExportData longReport(int rows) {
            List<List<Object>> body = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                body.add(List.of("CS20250" + String.format("%02d", i % 100),
                        "Student Number " + i,
                        (i % 40) + "L",
                        (i % 100) + "." + (i % 10) + "%"));
            }
            return new ExportData("Attendance Overview", "Department: X", "Sheet",
                    List.of("Enrollment No.", "Student Name", "Present", "Attendance %"),
                    body);
        }

        @Test
        @DisplayName("the rendered total equals the document's real page count")
        void totalMatchesRealPageCount() throws Exception {
            ExportData data = longReport(200);
            byte[] bytes = PdfRenderedDocument.render(generator, data,
                    PdfStyleOptions.defaults()
                            .withPageFooter(true)
                            .withPageNumbers(true));

            int actual = pageCount(bytes);
            assertTrue(actual > 1, "200 rows must span several pages, got " + actual);

            List<String> pages = pageTexts(bytes);
            for (int i = 0; i < pages.size(); i++) {
                assertTrue(pages.get(i).contains("Page " + (i + 1) + " of " + actual),
                        "page " + (i + 1) + " must state the real total of " + actual);
            }
        }

        @Test
        @DisplayName("every page carries the footer, including the last")
        void everyPageIsNumbered() throws Exception {
            byte[] bytes = PdfRenderedDocument.render(generator, longReport(120),
                    PdfStyleOptions.defaults().withPageFooter(true).withPageNumbers(true));
            int total = pageCount(bytes);
            List<String> pages = pageTexts(bytes);
            for (int i = 0; i < pages.size(); i++) {
                assertTrue(pages.get(i).contains("Page " + (i + 1) + " of " + total),
                        "page " + (i + 1) + " of " + total + " must be numbered");
            }
        }

        @Test
        @DisplayName("page numbering is skipped entirely when it is not requested")
        void singlePassWhenNotRequested() throws Exception {
            byte[] bytes = PdfRenderedDocument.render(generator, longReport(120),
                    PdfStyleOptions.defaults());
            assertTrue(pageCount(bytes) > 1);
            assertFalse(allText(bytes).contains("of 12"),
                    "no total should be written when page numbers are off");
            assertFalse(allText(bytes).contains("Page 1"),
                    "no page numbers should be written at all");
        }

        @Test
        @DisplayName("no data is re-fetched between the passes")
        void bothPassesShareOneExportData() throws Exception {
            // PdfRenderedDocument only ever receives a finished ExportData, so it
            // has no service to call. This test pins the structural guarantee by
            // showing the same instance drives both passes: the row content is
            // present exactly once per page and no duplicate body is produced.
            ExportData data = longReport(60);
            byte[] onePass = generator.generate(data);
            byte[] twoPass = PdfRenderedDocument.render(generator, data,
                    PdfStyleOptions.defaults().withPageFooter(true).withPageNumbers(true));

            assertEquals(pageCount(onePass), pageCount(twoPass),
                    "the counting pass must not change how the document paginates");
            // Every enrolment number appears once per row across the document.
            int occurrences = countOccurrences(allText(twoPass), "CS2025000");
            assertEquals(1, occurrences,
                    "a data value must not be duplicated by the second pass");
        }

        private int countOccurrences(String haystack, String needle) {
            int count = 0;
            int index = haystack.indexOf(needle);
            while (index >= 0) {
                count++;
                index = haystack.indexOf(needle, index + needle.length());
            }
            return count;
        }

        @Test
        @DisplayName("the counting pass does not emit a total it cannot know")
        void probePassHasNoTotal() throws Exception {
            // The first pass renders with a null total, which is the only state
            // in which a total is genuinely unavailable.
            byte[] probe = generator.generate(longReport(120),
                    PdfStyleOptions.defaults()
                            .withPageFooter(true).withPageNumbers(true));
            String text = allText(probe);
            assertTrue(text.contains("Page 1"));
            assertFalse(text.contains("Page 1 of"),
                    "an unresolved pass must not invent a total");
        }

        @Test
        @DisplayName("page counting works for a wide report too")
        void wideReportIsCounted() throws Exception {
            byte[] bytes = PdfRenderedDocument.render(generator, wideReport(45),
                    PdfStyleOptions.defaults().withPageFooter(true).withPageNumbers(true));
            assertEquals("%PDF-", magic(bytes));
            assertEquals(pageCount(bytes), 1, "one wide row fits on one landscape page");
            assertTrue(allText(bytes).contains("Page 1 of 1"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Opt-in table layout")
    class TableLayout {

        @Test
        @DisplayName("row integrity can be requested")
        void keepRowsIntact() throws Exception {
            byte[] bytes = generator.generate(longEnoughToSplit(),
                    PdfStyleOptions.defaults().withKeepRowsIntact(true));
            assertEquals("%PDF-", magic(bytes));
            assertTrue(pageCount(bytes) >= 1);
        }

        private ExportData longEnoughToSplit() {
            List<List<Object>> body = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                body.add(List.of("CS" + i,
                        "A deliberately long student name that needs to wrap across "
                                + "several lines inside its cell so the row becomes taller "
                                + "than a comfortable single line",
                        20L, "50.00%"));
            }
            return new ExportData("Report", "sub", "Sheet",
                    List.of("Code", "Name", "Present", "Attendance %"), body);
        }

        @Test
        @DisplayName("header shading and borders can be requested and still render")
        void shadingAndBorders() throws Exception {
            byte[] bytes = generator.generate(simpleReport(),
                    PdfStyleOptions.defaults()
                            .withHeaderShading(true)
                            .withCellBorders(true));
            assertEquals("%PDF-", magic(bytes));
            assertTrue(allText(bytes).contains("Subject Code"),
                    "shading must not disturb the header text");
        }

        @Test
        @DisplayName("a total row can be emphasised using the 4B row markers")
        void totalRowEmphasis() throws Exception {
            ExportData data = new ExportData("Student Attendance", "sub", "Sheet",
                    List.of("Subject", "Present", "Total Classes", "Attendance %"),
                    List.of(List.of("CS301", 32L, 40L, "80.00%"),
                            List.of("TOTAL", 32L, 40L, "80.00%")))
                    .withTotalRows(1);
            byte[] bytes = generator.generate(data,
                    PdfStyleOptions.defaults().withEmphasizedTotalRows(true));
            String text = allText(bytes);
            assertTrue(text.contains("TOTAL"));
            assertTrue(text.contains("80.00%"), "emphasis must not alter any value");
        }

        @Test
        @DisplayName("an empty report can render as a real table")
        void emptyReportAsTable() throws Exception {
            byte[] bytes = generator.generate(
                    new ExportData("Empty", "all dates", "Sheet",
                            List.of("Subject Code", "Present"), List.of()),
                    PdfStyleOptions.defaults().withEmptyReportAsTable(true));
            String text = allText(bytes);
            assertTrue(text.contains("Subject Code"));
            assertTrue(text.contains("No rows for the selected criteria."),
                    "an empty report must state why it is empty, not present a blank table");
        }

        @Test
        @DisplayName("N/A is rendered verbatim, never as 0%")
        void naIsVerbatim() throws Exception {
            String text = allText(generator.generate(simpleReport(),
                    PdfStyleOptions.defaults()
                            .withPageFooter(true).withPageNumbers(true)
                            .withTotalPages(1)));
            assertAll(
                    () -> assertTrue(text.contains("N/A"),
                            "a percentage with no denominator must survive as N/A"),
                    () -> assertTrue(text.contains("80.00%")),
                    () -> assertTrue(text.contains("60.00%")),
                    // Exactly the three percentages the report supplied, and no
                    // fourth. A fabricated zero would show up as an extra "0.00%".
                    () -> assertEquals(3, countOccurrences(text, "%"),
                            "no denominator must never become an extra zero percentage; "
                                    + "rendered text was: " + text));
        }

        private int countOccurrences(String haystack, String needle) {
            int count = 0;
            int index = haystack.indexOf(needle);
            while (index >= 0) {
                count++;
                index = haystack.indexOf(needle, index + needle.length());
            }
            return count;
        }

        @Test
        @DisplayName("user-controlled text is never treated as markup")
        void userTextStaysInert() throws Exception {
            // The Excel apostrophe rule is deliberately NOT applied here: OpenPDF
            // encodes strings itself, and prefixing an apostrophe would print a
            // stray character into the document.
            ExportData data = new ExportData("Report", "sub", "Sheet",
                    List.of("Student Name", "Subject"),
                    List.of(List.of("<script>alert(1)</script>", "=1+1"),
                            List.of("Rahul & Sons <Ltd>", "O'Brien")));
            String text = allText(generator.generate(data));
            assertAll(
                    () -> assertTrue(text.contains("Rahul & Sons"),
                            "an ampersand is legal text and must survive"),
                    () -> assertTrue(text.contains("O'Brien"),
                            "an apostrophe inside a name must survive"),
                    () -> assertFalse(text.contains("'<"),
                            "the spreadsheet escaping marker must not leak into a PDF"));
        }
    }
}