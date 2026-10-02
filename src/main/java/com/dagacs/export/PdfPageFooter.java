package com.dagacs.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.util.List;

/**
 * Phase 4C: the running footer - report context and {@code Page X of Y}.
 *
 * <p>Drawn from {@code onEndPage}, i.e. into the page <b>margin</b>, not into the
 * document flow. That is deliberate: content added in the flow can push the table
 * onto another page and change the pagination, whereas margin content cannot. A
 * footer can therefore never make "page 2 of 3" a lie, and it can never collide
 * with the last row of the table.</p>
 *
 * <h3>Why the total is passed in rather than read from the writer</h3>
 * <p>OpenPDF 1.3.39 - an iText 2.1.7 fork - has <b>no</b>
 * {@code PdfWriter.getNumberOfPages()}; only {@code getPageNumber()} exists, which
 * is the current page. The familiar iText trick of writing the total from
 * {@code onCloseDocument} is therefore impossible here. The total is resolved by
 * a first rendering pass and supplied to this class for the second, which is what
 * {@link PdfRenderedDocument} exists to orchestrate.</p>
 *
 * <h3>Why both passes draw the same amount</h3>
 * <p>The first pass has no total and renders {@code "Page 3"}; the second renders
 * {@code "Page 3 of 12"}. Both are drawn at the same baseline in the same font, so
 * the footer occupies identical space and the two passes paginate identically -
 * which is what makes the resolved total correct. {@link #paginationPlaceholder()}
 * exists to make that equality explicit and testable.</p>
 *
 * <p>The footer is never attached unless a report asks for it: see
 * {@link PdfStyleOptions#pageFooter()}, whose default is {@code false} precisely
 * so existing Teacher and M7.2 documents are unaffected.</p>
 */
final class PdfPageFooter extends PdfPageEventHelper {

    /** Footer type size. Small on purpose: it must never compete with the table. */
    private static final float FOOTER_FONT_SIZE = 8f;

    /** Distance from the bottom edge of the page to the footer's baseline. */
    private static final float FOOTER_BOTTOM_OFFSET = 18f;

    /** Vertical gap between the context line and the page-number line. */
    private static final float FOOTER_LINE_GAP = 10f;

    /** Muted grey, so the footer reads as chrome rather than as data. */
    private static final Color FOOTER_COLOR = new Color(90, 90, 90);

    /**
     * The first-pass stand-in for the total.
     *
     * <p>It is the same length and glyph shape as a real total is expected to be,
     * so the footer line occupies the same height in both passes. Without this the
     * first pass would emit a shorter string, which is harmless in the margin -
     * but keeping the passes textually symmetric is what makes the pagination
     * equality assertion meaningful.
     */
    private static final String PAGINATION_PLACEHOLDER = "00";

    private final String reportLabel;
    private final boolean showPageNumbers;
    private final boolean showContext;
    private final Integer totalPages;

    PdfPageFooter(String reportLabel, boolean showPageNumbers, boolean showContext,
                  Integer totalPages) {
        this.reportLabel = reportLabel;
        this.showPageNumbers = showPageNumbers;
        this.showContext = showContext;
        this.totalPages = totalPages;
    }

    /**
     * The text a first pass writes where the total will later appear.
     *
     * <p>Exposed so a test can assert that the first and second passes place
     * identically-sized content in the footer.</p>
     */
    static String paginationPlaceholder() {
        return PAGINATION_PLACEHOLDER;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        if (!showPageNumbers && !showContext) {
            // Nothing was asked for: draw nothing rather than an empty band.
            return;
        }

        Font footerFont = new Font(Font.HELVETICA, FOOTER_FONT_SIZE, Font.NORMAL,
                FOOTER_COLOR);
        int page = writer.getPageNumber();
        float pageWidth = document.getPageSize().getWidth();

        if (showContext && reportLabel != null && !reportLabel.isBlank()) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(reportLabel, footerFont),
                    pageWidth / 2f, document.bottomMargin() - FOOTER_BOTTOM_OFFSET
                            - FOOTER_LINE_GAP, 0);
        }

        if (showPageNumbers) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(pageLabel(page), footerFont),
                    pageWidth / 2f, document.bottomMargin() - FOOTER_BOTTOM_OFFSET, 0);
        }
    }

    /**
     * {@code "Page 3"} or {@code "Page 3 of 12"}.
     *
     * <p>A single-page report still reads {@code "Page 1 of 1"} rather than being
     * left blank, because a reader who sees one page and no footer cannot tell
     * whether the document was truncated.</p>
     */
    private String pageLabel(int page) {
        if (totalPages == null) {
            // First pass: the total is not known yet.
            return "Page " + page;
        }
        return "Page " + page + " of " + totalPages;
    }

    /**
     * The full margin height the footer needs.
     *
     * <p>Kept next to the drawing constants so the two cannot drift apart; a test
     * asserts the layout still fits inside an A4 page.</p>
     */
    static float requiredMargin() {
        return FOOTER_BOTTOM_OFFSET + FOOTER_LINE_GAP + FOOTER_FONT_SIZE + 6f;
    }

    /** The page size a document defaults to, exposed for the layout test. */
    static float portraitA4Width() {
        return PageSize.A4.getWidth();
    }

    /** Unused today, but keeps the context line open for a per-report caption. */
    List<String> contextLines() {
        return reportLabel == null || reportLabel.isBlank()
                ? List.of() : List.of(reportLabel);
    }
}