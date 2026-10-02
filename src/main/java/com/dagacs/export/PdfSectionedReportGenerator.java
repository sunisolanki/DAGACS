package com.dagacs.export;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4C.3: renders an ordered list of report sections as ONE PDF, with a
 * continuous page sequence and a real page size per section.
 *
 * <h3>What it is for</h3>
 * <p>The HOD Context Pack is the first deliverable that needs several reports of
 * one authorized context in a single file. Before this class a "one file, several
 * reports" PDF had no home: {@link PdfReportGenerator} renders one report, and
 * OpenPDF 1.3.39 cannot change a document's page size half way through. Each
 * section is therefore rendered by the <b>unchanged</b> generator - which already
 * sizes each document from {@link ExportData#landscape()}, so the wide cross-tab
 * comes out landscape and the narrow summaries come out portrait - and the pages
 * are merged by {@link PdfDocumentMerger}, which keeps each page's own box.</p>
 *
 * <h3>Data is loaded once, rendered twice</h3>
 * <p>This class never calls a service, a repository or a query. It receives
 * finished {@link ExportData} instances and only produces bytes. That is what makes
 * the two passes free of database cost: the same objects are rendered once to
 * count pages and again to print the real totals, so enabling continuous
 * {@code Page X of Y} cannot change an export's query count - the property
 * {@code HodAttendanceExportQueryCountIntegrationTest} exists to protect.</p>
 *
 * <h3>How continuous numbering is achieved</h3>
 * <p>Pass one renders every section with no total and counts its pages. The counts
 * give each section an offset (how many pages precede it) and the document a
 * total. Pass two re-renders every section with that offset and total, so the first
 * page of the last section reads {@code "Page 8 of 8"} rather than {@code "Page 3 of
 * 3"}.</p>
 *
 * <p>Pass one and pass two paginate identically because the only difference is
 * text in the page <b>margin</b>, which cannot push the body onto another page -
 * the same argument {@link PdfPageFooter} already makes for a single report.</p>
 *
 * <p>Section order is the caller's order, declared once by whoever builds the
 * sections, exactly as {@link ExportWorkbook} declares sheet order for the
 * spreadsheet. A page break is inherent: each section is its own document, so a
 * section can never continue immediately after the previous one's last row.</p>
 */
@Component
public class PdfSectionedReportGenerator {

    /**
     * One part of the deliverable.
     *
     * @param label   what the running footer names this part, e.g.
     *                {@code "Attendance Context Pack - Attendance Matrix"}
     * @param data    the section's contents, already loaded by the caller
     * @param options the layout for this section; orientation still comes from
     *                {@link ExportData#landscape()} unless the options override it
     */
    public record PdfSection(String label, ExportData data, PdfStyleOptions options) {

        public PdfSection {
            if (data == null) {
                throw new IllegalArgumentException("A section must carry its report data");
            }
            if (options == null) {
                options = PdfStyleOptions.defaults();
            }
        }

        /** A section labelled with the report's own sheet name. */
        public static PdfSection of(ExportData data, PdfStyleOptions options) {
            return new PdfSection(data.sheetName(), data, options);
        }
    }

    private final PdfReportGenerator generator;

    public PdfSectionedReportGenerator(PdfReportGenerator generator) {
        this.generator = generator;
    }

    /**
     * Renders every section into one PDF, in order.
     *
     * @param sections the sections; must not be empty
     * @return the merged PDF
     */
    public byte[] generate(List<PdfSection> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException(
                    "A sectioned document must contain at least one section");
        }

        // Pass 1: discover how many pages each section needs. No total is known
        // yet, so each section renders its own count - which is then discarded.
        List<byte[]> counted = new ArrayList<>(sections.size());
        int[] pageCounts = new int[sections.size()];
        int total = 0;
        for (int i = 0; i < sections.size(); i++) {
            PdfSection section = sections.get(i);
            byte[] probe = render(section, 0, null);
            pageCounts[i] = PdfRenderedDocument.countPages(probe);
            total += pageCounts[i];
        }

        // Pass 2: the same data, now numbered inside the whole document.
        int offset = 0;
        for (int i = 0; i < sections.size(); i++) {
            counted.add(render(sections.get(i), offset, total));
            offset += pageCounts[i];
        }

        try {
            return PdfDocumentMerger.merge(counted);
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("Failed to assemble the PDF document", e);
        }
    }

    /** Renders one section, numbered as part of the whole. */
    private byte[] render(PdfSection section, int pageNumberOffset, Integer totalPages) {
        PdfStyleOptions options = section.options().withTotalPages(totalPages);
        return generator.generate(section.data(), options, section.label(), pageNumberOffset);
    }

    /**
     * The page count each section will contribute.
     *
     * <p>Exposed so a caller can state the expected size of the deliverable in a
     * log or a test without rendering it twice itself.</p>
     */
    public int[] sectionPageCounts(List<PdfSection> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException(
                    "A sectioned document must contain at least one section");
        }
        int[] counts = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            counts[i] = PdfRenderedDocument.countPages(
                    render(sections.get(i), 0, null));
        }
        return counts;
    }
}
