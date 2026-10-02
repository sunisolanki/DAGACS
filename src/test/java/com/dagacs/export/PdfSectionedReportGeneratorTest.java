package com.dagacs.export;

import com.dagacs.config.ReportBrandingProperties;
import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceMatrixColumnDTO;
import com.dagacs.dto.HodAttendanceMatrixCellDTO;
import com.dagacs.dto.HodAttendanceMatrixDTO;
import com.dagacs.dto.HodAttendanceMatrixRowDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4C.3: the generic ability to print several reports as one PDF.
 *
 * <p>{@code HodContextPackPdfTest} proves what the HOD Pack gets out of this. This
 * class proves the mechanism itself, so a fault here is identifiable as a fault in
 * the mechanism rather than in the Pack.</p>
 *
 * <h3>Why the mechanism is a merge</h3>
 * <p>OpenPDF 1.3.39 fixes a page's {@code MediaBox} from the document's live page
 * size at the moment the page object is created, and only refreshes that size from
 * the pending one at page initialisation - so one {@code Document} cannot emit a
 * portrait page and then a landscape one. This suite pins the two properties that
 * make the merge a legitimate substitute rather than a workaround: each page keeps
 * the size it was rendered at, and nothing is rotated to achieve it.</p>
 */
class PdfSectionedReportGeneratorTest {

    private final PdfReportGenerator reportGenerator = new PdfReportGenerator();
    private final PdfSectionedReportGenerator generator =
            new PdfSectionedReportGenerator(reportGenerator);

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private static ExportData table(String title, String sheet, boolean wide, int rows) {
        List<String> headers = List.of("Code", "Name", "Value");
        List<List<Object>> body = new java.util.ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            body.add(List.of("C" + i, "Name " + i, i * 10));
        }
        return new ExportData(title, title + " subtitle", sheet, headers, body,
                List.of("Program: B.Tech CSE", "Semester: Semester 3"), wide);
    }

    private static PdfSectionedReportGenerator.PdfSection section(
            String label, ExportData data) {
        return new PdfSectionedReportGenerator.PdfSection(label, data,
                PdfStyleOptions.defaults()
                        .withPageFooter(true)
                        .withPageNumbers(true)
                        .withFooterContext(true)
                        .withContentWidths(true)
                        .withCellBorders(true)
                        .withHeaderShading(true)
                        .withKeepRowsIntact(true)
                        .withEmptyReportAsTable(true));
    }

    // ── The merge ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. sections are concatenated in the order they were given")
    void sectionsKeepTheirOrder() throws Exception {
        byte[] pdf = generator.generate(List.of(
                section("Alpha", table("ALPHA", "S1", false, 1)),
                section("Bravo", table("BRAVO", "S2", false, 1)),
                section("Charlie", table("CHARLIE", "S3", false, 1))));

        String text = PdfProbe.flatText(pdf);
        assertTrue(text.indexOf("ALPHA") < text.indexOf("BRAVO"), text);
        assertTrue(text.indexOf("BRAVO") < text.indexOf("CHARLIE"), text);
    }

    @Test
    @DisplayName("2. each page keeps the size it was rendered at")
    void everyPageKeepsItsOwnSize() throws Exception {
        byte[] pdf = generator.generate(List.of(
                section("Portrait-one", table("PORTRAIT-ONE", "S1", false, 1)),
                section("Landscape", table("LANDSCAPE-BODY", "S2", true, 1)),
                section("Portrait-two", table("PORTRAIT-TWO", "S3", false, 1))));

        assertAll(
                () -> assertFalse(PdfProbe.isLandscape(pdf, 1),
                        "section 1 is portrait"),
                () -> assertTrue(PdfProbe.isLandscape(pdf, 2),
                        "section 2 is landscape"),
                () -> assertFalse(PdfProbe.isLandscape(pdf, 3),
                        "section 3 is portrait"));
    }

    @Test
    @DisplayName("3. the landscape page is genuinely wide, not a rotated portrait")
    void landscapeIsRealNotRotated() throws Exception {
        byte[] pdf = generator.generate(List.of(
                section("Portrait", table("PORTRAIT-BODY", "S1", false, 1)),
                section("Landscape", table("LANDSCAPE-BODY", "S2", true, 1))));

        try (com.lowagie.text.pdf.PdfReader reader =
                     new com.lowagie.text.pdf.PdfReader(pdf)) {
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                com.lowagie.text.Rectangle size = reader.getPageSize(page);
                assertEquals(0, size.getRotation(),
                        "page " + page + " must carry no /Rotate entry: a rotated "
                                + "portrait page is not a landscape page");
            }
            com.lowagie.text.Rectangle wide = reader.getPageSize(2);
            assertTrue(wide.getWidth() > wide.getHeight(),
                    "the wide section must have width > height: " + wide.getWidth()
                            + "x" + wide.getHeight());
            com.lowagie.text.Rectangle withRotation = reader.getPageSizeWithRotation(2);
            assertTrue(withRotation.getWidth() > withRotation.getHeight(),
                    "the wide section must still be landscape once rotation is applied");
        }
    }

    @Test
    @DisplayName("4. a section that spans several pages is copied page for page")
    void multiPageSectionsSurvive() throws Exception {
        byte[] pdf = generator.generate(List.of(
                section("First", table("FIRST-BODY", "S1", false, 1)),
                section("Wide", table("WIDE-BODY", "S2", true, 90)),
                section("Last", table("LAST-BODY", "S3", false, 1))));

        int pages = PdfProbe.pageCount(pdf);
        assertTrue(pages >= 4, "expected several pages, got " + pages);
        // The middle section is landscape; the two around it are portrait, so a
        // merge that flattened sizes would show up as a uniform document.
        boolean sawLandscape = false;
        boolean sawPortrait = false;
        for (int page = 1; page <= pages; page++) {
            if (PdfProbe.isLandscape(pdf, page)) {
                sawLandscape = true;
            } else {
                sawPortrait = true;
            }
        }
        assertTrue(sawLandscape, "the wide section must contribute landscape pages");
        assertTrue(sawPortrait, "the narrow sections must contribute portrait pages");
    }

    // ── Continuous numbering ────────────────────────────────────────────────

    @Test
    @DisplayName("5. page numbering is continuous across every section")
    void numberingIsContinuousAcrossSections() throws Exception {
        byte[] pdf = generator.generate(List.of(
                section("One", table("ONE-BODY", "S1", false, 1)),
                section("Two", table("TWO-BODY", "S2", false, 40)),
                section("Three", table("THREE-BODY", "S3", false, 1))));

        int total = PdfProbe.pageCount(pdf);
        assertTrue(total > 2, "the fixture must span several pages, got " + total);

        List<String> pages = PdfProbe.pageTexts(pdf);
        for (int i = 0; i < pages.size(); i++) {
            assertTrue(pages.get(i).contains("Page " + (i + 1) + " of " + total),
                    "page " + (i + 1) + " must read 'Page " + (i + 1) + " of "
                            + total + "', but read: ["
                            + PdfProbe.tail(pages.get(i), 60) + "]");
        }
    }

    @Test
    @DisplayName("6. the running footer names the section it is on")
    void footerNamesTheSection() throws Exception {
        byte[] pdf = generator.generate(List.of(
                section("Pack - Alpha", table("ALPHA-BODY", "S1", false, 1)),
                section("Pack - Bravo", table("BRAVO-BODY", "S2", true, 1))));

        List<String> pages = PdfProbe.pageTexts(pdf);
        assertTrue(PdfProbe.flatText(pages.get(0)).contains("Pack - Alpha"),
                "page 1 must name its own section");
        assertTrue(PdfProbe.flatText(pages.get(1)).contains("Pack - Bravo"),
                "page 2 must name its own section, not the previous one");
    }

    // ── The unchanged single-report path ───────────────────────────────────

    @Test
    @DisplayName("7. one section through the merge reads exactly as a direct render")
    void singleSectionReadsAsThePlainRender() throws Exception {
        ExportData data = table("REPORT", "Sheet", false, 3);
        PdfStyleOptions layout = PdfStyleOptions.defaults()
                .withPageFooter(true).withPageNumbers(true).withFooterContext(true)
                .withContentWidths(true).withCellBorders(true)
                .withHeaderShading(true).withKeepRowsIntact(true)
                .withEmptyReportAsTable(true);

        byte[] direct = reportGenerator.generate(data, layout.withTotalPages(1),
                "REPORT", 0);
        byte[] merged = generator.generate(List.of(section("REPORT", data)));

        // Not a byte comparison, and deliberately so: the merge re-encodes the
        // container, so the two files cannot be identical. What must be identical
        // is the document a reader gets - same pages, same size, same text, same
        // numbering. The lock that the *unchanged* single-report path is untouched
        // is PdfFormattingTest$SharedGeneratorCompatibility, which compares
        // generate(data) with generate(data, defaults()) on the two-argument
        // overload this phase did not alter.
        assertEquals(PdfProbe.pageCount(direct), PdfProbe.pageCount(merged),
                "the merged document must have the same number of pages");
        assertEquals(PdfProbe.isLandscape(direct, 1), PdfProbe.isLandscape(merged, 1),
                "the merged document must have the same page size");
        assertEquals(PdfProbe.flatText(direct), PdfProbe.flatText(merged),
                "the merged document must carry the same text, including the footer");
    }

    // ── Refusals ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("8. an empty section list is refused rather than producing a broken file")
    void emptySectionListIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of()));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
        assertThrows(IllegalArgumentException.class, () -> PdfDocumentMerger.merge(List.of()));
    }

    @Test
    @DisplayName("9. a section with no data still prints its own page")
    void emptySectionStillPrints() throws Exception {
        ExportData empty = new ExportData("EMPTY", "nothing conducted", "S2",
                List.of("Code", "Name"), List.of(), List.of("Program: B.Tech CSE"),
                false);

        byte[] pdf = generator.generate(List.of(
                section("One", table("ONE-BODY", "S1", false, 1)),
                section("Two", empty),
                section("Three", table("THREE-BODY", "S3", false, 1))));

        assertEquals(3, PdfProbe.pageCount(pdf),
                "an empty section must still occupy a page rather than vanish");
        assertTrue(PdfProbe.flatText(pdf).contains("No rows for the selected criteria."),
                "an empty section must say why it is empty");
    }

    /**
     * The PDF inspection helpers, in one place.
     *
     * <p>Text extraction inserts a newline wherever the text was laid out across
     * lines, and a column legitimately wraps its own heading, so assertions run on
     * whitespace-collapsed text. Otherwise the suite would be measuring the
     * extractor's line breaking rather than the document.</p>
     */
    private static final class PdfProbe {

        private static List<String> pageTexts(byte[] pdf) throws Exception {
            com.lowagie.text.pdf.PdfReader reader =
                    new com.lowagie.text.pdf.PdfReader(new java.io.ByteArrayInputStream(pdf));
            try {
                com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                        new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
                List<String> texts = new java.util.ArrayList<>();
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    texts.add(extractor.getTextFromPage(page));
                }
                return texts;
            } finally {
                reader.close();
            }
        }

        private static String flatText(byte[] pdf) throws Exception {
            return flatText(String.join(" ", pageTexts(pdf)));
        }

        private static String flatText(String text) {
            return text.replaceAll("\\s+", " ").trim();
        }

        private static int pageCount(byte[] pdf) throws Exception {
            try (com.lowagie.text.pdf.PdfReader reader =
                         new com.lowagie.text.pdf.PdfReader(pdf)) {
                return reader.getNumberOfPages();
            }
        }

        private static boolean isLandscape(byte[] pdf, int page) throws Exception {
            try (com.lowagie.text.pdf.PdfReader reader =
                         new com.lowagie.text.pdf.PdfReader(pdf)) {
                com.lowagie.text.Rectangle size = reader.getPageSize(page);
                return size.getWidth() > size.getHeight();
            }
        }

        private static String tail(String text, int characters) {
            return text.substring(Math.max(0, text.length() - characters));
        }
    }
}
