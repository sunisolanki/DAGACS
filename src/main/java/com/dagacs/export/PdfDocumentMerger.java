package com.dagacs.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSmartCopy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4C.3: joins several rendered PDFs into one file, keeping every page's own
 * size.
 *
 * <h3>Why a merge is the mechanism</h3>
 * <p>OpenPDF 1.3.39 captures a page's {@code MediaBox} from the document's live
 * page size <b>when the page object is created</b>, and only refreshes that live
 * size from the pending one at page initialisation. The consequence is that a single
 * {@link Document} cannot emit a portrait page followed by a landscape one: the
 * size is always one page behind. The library offers no per-page resize, and
 * rotating a portrait page would be a fake, not a landscape page.</p>
 *
 * <p>So a deliverable that genuinely mixes orientations is rendered part by part -
 * each part by the existing {@link PdfReportGenerator}, which already sizes each
 * document from {@link ExportData#landscape()} - and the pages are then merged
 * here. {@link PdfSmartCopy} copies each source page as an imported page carrying
 * its own dimensions, which is what preserves a landscape page as landscape.</p>
 *
 * <h3>What this explicitly does not do</h3>
 * <p>{@link PdfCopy#setRotateContents(boolean)} is left at its default of
 * {@code false}. No page is rotated, scaled or re-boxed: a wide page in the output
 * is a wide page because the content was laid out on a wide page.
 * {@code HodContextPackPdfTest} asserts the absence of a {@code /Rotate} entry and
 * asserts the page's real width and height, so this cannot quietly regress into a
 * rotated portrait page.</p>
 *
 * <p>The result is one valid PDF with one continuous page sequence, which is what
 * lets the merged footer read {@code "Page 8 of 8"} on the last page.</p>
 */
public final class PdfDocumentMerger {

    private PdfDocumentMerger() {
    }

    /**
     * Concatenates the given documents, in order, into a single PDF.
     *
     * @param documents already-rendered PDFs, in the order their pages should appear
     * @return the merged PDF
     * @throws IllegalArgumentException when nothing was supplied
     * @throws DocumentException       when a supplied document cannot be read
     */
    public static byte[] merge(List<byte[]> documents) throws DocumentException {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("A merged document must contain at least one part");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // The container's own page size is irrelevant: every page below is an
        // imported page that keeps the size it was rendered at.
        Document document = new Document(PageSize.A4);
        PdfCopy copy = new PdfSmartCopy(document, out);
        // Never rotate an imported page - that would hide a sizing fault behind a
        // viewer-level trick.
        copy.setRotateContents(false);
        document.open();

        List<PdfReader> readers = new ArrayList<>();
        try {
            for (byte[] part : documents) {
                if (part == null || part.length == 0) {
                    continue;
                }
                PdfReader reader = new PdfReader(part);
                readers.add(reader);
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    PdfImportedPage imported = copy.getImportedPage(reader, page);
                    copy.addPage(imported);
                }
            }
        } catch (IOException e) {
            throw new DocumentException("Failed to read a rendered PDF part: " + e);
        } finally {
            for (PdfReader reader : readers) {
                reader.close();
            }
        }

        document.close();
        return out.toByteArray();
    }
}
