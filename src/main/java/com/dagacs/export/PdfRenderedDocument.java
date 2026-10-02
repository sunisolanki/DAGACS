package com.dagacs.export;

import com.lowagie.text.pdf.PdfReader;

import java.io.IOException;

/**
 * Phase 4C: renders a PDF, and resolves {@code Page X of Y} in exactly two passes.
 *
 * <h3>Why two passes are mandatory on this library</h3>
 * <p>OpenPDF 1.3.39 (an iText 2.1.7 fork) has <b>no</b>
 * {@code PdfWriter.getNumberOfPages()}. Only {@code getPageNumber()} exists, which
 * reports the page being written, not how many there will be. The usual iText
 * approach - write each page as it is produced, then write the total from
 * {@code onCloseDocument} - is therefore unavailable, because by the time the
 * total is known the pages have already been written.
 *
 * <p>So the count is obtained by rendering the document once, reading that
 * output's page count with {@link PdfReader#getNumberOfPages()}, and rendering
 * again with the total supplied.</p>
 *
 * <h3>The rule that keeps this honest</h3>
 * <p><b>Both passes receive the same {@link ExportData} instance and no service is
 * consulted in between.</b> The data is loaded and arranged once, upstream, by the
 * canonical report service; this class only renders bytes. A second pass that
 * re-fetched would double the database cost of every PDF export, and would risk the
 * two passes disagreeing about the data - the exact failure Phase 4A made
 * structurally impossible for the Excel/PDF parity. {@code PdfFormattingTest}
 * asserts that enabling page numbers does not change how many times the report
 * service is called.</p>
 *
 * <p>The cost of the second pass is CPU, not I/O.</p>
 *
 * <h3>When the count is not needed</h3>
 * <p>If a report does not ask for page numbers, this class renders once and
 * returns. The first pass exists only to discover the total, so it is skipped
 * entirely rather than rendered and thrown away.</p>
 */
final class PdfRenderedDocument {

    private PdfRenderedDocument() {
    }

    /**
     * Renders {@code data} honouring {@code options}.
     *
     * @param generator the renderer used for every pass
     * @param data      the report to render; the <b>same instance</b> is used for
     *                  both passes
     * @param options   the layout to apply
     */
    static byte[] render(PdfReportGenerator generator, ExportData data,
                         PdfStyleOptions options) {
        if (!options.pageNumbers()) {
            // No total is wanted, so no counting pass is run at all.
            return generator.generate(data, options);
        }
        // Pass 1: render to discover how many pages the document needs. The result
        // is counted and discarded.
        byte[] probe = generator.generate(data, options.withTotalPages(null));
        int total = countPages(probe);
        // Pass 2: the same data, the same options, now with the resolved total.
        return generator.generate(data, options.withTotalPages(total));
    }

    /**
     * The page count of a rendered PDF.
     *
     * <p>A PDF that cannot be parsed here would be a defect in the renderer itself,
     * so the failure is surfaced rather than silently producing a footer with no
     * total.</p>
     */
    static int countPages(byte[] pdf) {
        try (PdfReader reader = new PdfReader(pdf)) {
            return reader.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the generated PDF page count", e);
        }
    }
}