package com.dagacs.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal OpenPDF-based PDF builder for M7.2 exports. PDF is justified by the
 * corrected SOW for official records and shareable reports with the same query
 * layer as the API. Produces a plain, readable table PDF (no branding/charts/
 * logos/signatures, per scope). One library only, no report designer/framework.
 *
 * <p>The student-wise attendance register carries one column per conducted
 * session date and is flagged {@link ExportData#landscape()}, so it renders on
 * a landscape page and repeats its header row on every page.
 *
 * <h3>Phase 4C - professional layout, strictly opt-in</h3>
 * <p>{@link #generate(ExportData)} still delegates to {@link #generate(ExportData,
 * PdfStyleOptions)} with {@link PdfStyleOptions#defaults()}, and every default is
 * the behaviour that existed before Phase 4C. A shared generator is the main
 * hazard in this phase - it renders the HOD attendance PDFs and, through
 * {@code ReportExportService}, the pre-existing HOD M7.2 department reports and
 * every Teacher export - so a change that altered a default would silently
 * reformat all of them. {@code PdfFormattingTest} pins the defaults rather than
 * trusting them.</p>
 *
 * <p>Opt-in additions: content-derived column widths, a running footer with the
 * report context and {@code Page X of Y}, row integrity, header shading, cell
 * borders, total-row emphasis, and a real table for an empty report.</p>
 *
 * <h3>Text handling</h3>
 * <p>Values are passed to {@link Phrase} as plain text. OpenPDF encodes and
 * escapes strings itself, so the apostrophe-prefix trick used for spreadsheet
 * formula injection is <b>not</b> applied here - it would print a stray
 * character into a PDF. No markup is ever interpreted, because no markup
 * language is involved.</p>
 */
@Component
public class PdfReportGenerator {

    /** A4 with width and height swapped, for the wide student-wise register. */
    private static final Rectangle LANDSCAPE_A4 =
            new Rectangle(PageSize.A4.getHeight(), PageSize.A4.getWidth());

    /** Historical portrait margins, preserved for {@link PdfStyleOptions#defaults()}. */
    private static final float[] PORTRAIT_MARGINS = {36f, 36f, 36f, 36f};

    /** Historical landscape margins, preserved for {@link PdfStyleOptions#defaults()}. */
    private static final float[] LANDSCAPE_MARGINS = {24f, 24f, 30f, 24f};

    /** Historical base font size for a wide report. */
    private static final float LANDSCAPE_FONT_SCALE = 6.5f;

    /** Historical base font size for a narrow report. */
    private static final float PORTRAIT_FONT_SCALE = 9f;

    /** Light grey fill behind a header row. */
    private static final Color HEADER_FILL = new Color(230, 230, 230);

    /** Slightly stronger tint for a total row. */
    private static final Color TOTAL_FILL = new Color(221, 235, 247);

    /** Upper bound on a column's share of the table width, as a percentage. */
    private static final float MAX_COLUMN_PERCENT = 40f;

    /** A column never takes less than this share, however short its content. */
    private static final float MIN_COLUMN_PERCENT = 3f;

    public byte[] generate(ExportData data) {
        return generate(data, PdfStyleOptions.defaults());
    }

    /**
     * Renders one report with the layout it asked for.
     *
     * <p>This is the only overload that applies Phase 4C formatting; the signature
     * above reaches it with the defaults that reproduce the historical layout.</p>
     */
    public byte[] generate(ExportData data, PdfStyleOptions options) {
        boolean wide = options.hasLandscapeOverride()
                ? options.landscapeOverride()
                : data.landscape();
        float scale = resolveFontScale(options, wide);
        float[] margins = resolveMargins(options, wide);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Explicit width/height swap: Rectangle.rotate() is a no-op while
            // the page rotation is 0, which silently yields a portrait page.
            Document document = wide
                    ? new Document(LANDSCAPE_A4,
                    margins[0], margins[1], margins[2], margins[3])
                    : new Document(PageSize.A4,
                    margins[0], margins[1], margins[2], margins[3]);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            if (options.pageFooter()) {
                // The footer lives in the margin, so it can never alter how the
                // body paginates.
                writer.setPageEvent(new PdfPageFooter(data.title(),
                        options.pageNumbers(), options.footerContext(),
                        options.totalPages()));
            }
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font headerFont = new Font(Font.HELVETICA, scale, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, scale, Font.NORMAL);

            document.add(new Paragraph(data.title(), titleFont));
            if (data.subtitle() != null && !data.subtitle().isBlank()) {
                document.add(new Paragraph(data.subtitle(), normalFont));
            }
            if (data.metaLines() != null) {
                for (String line : data.metaLines()) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    document.add(new Paragraph(line, normalFont));
                }
            }
            document.add(new Paragraph(" ", normalFont));

            if (data.rows().isEmpty()) {
                addEmptyReport(document, data, headerFont, options);
                document.close();
                return out.toByteArray();
            }

            document.add(buildTable(data, headerFont, bodyFont, options));
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }

    /**
     * The empty-report rendering.
     *
     * <p>The historical behaviour is preserved by default: OpenPDF only emits a
     * {@link PdfPTable}'s header row once a body row has been completed, so an
     * empty report used to render its headers as one plain line. A report can opt
     * into a real, bordered table instead.</p>
     */
    private void addEmptyReport(Document document, ExportData data, Font headerFont,
                                PdfStyleOptions options) {
        if (!options.emptyReportAsTable()) {
            document.add(new Paragraph(
                    String.join("   |   ", data.headers()), headerFont));
            return;
        }
        PdfPTable table = new PdfPTable(data.headers().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : data.headers()) {
            table.addCell(headerCell(header, headerFont, options));
        }
        PdfPCell notice = new PdfPCell(
                new Phrase("No rows for the selected criteria.", headerFont));
        notice.setColspan(Math.max(data.headers().size(), 1));
        notice.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        table.addCell(notice);
        document.add(table);
    }

    /** Builds the body table, applying the opt-in layout features. */
    private PdfPTable buildTable(ExportData data, Font headerFont, Font bodyFont,
                                 PdfStyleOptions options) {
        PdfPTable table = new PdfPTable(data.headers().size());
        table.setWidthPercentage(100);
        if (options.repeatHeaderRows()) {
            // Repeats the header on every page.
            table.setHeaderRows(1);
        }
        if (options.keepRowsIntact()) {
            // Without this, OpenPDF's default lets a tall row break across a page
            // boundary, splitting one student's record in two.
            table.setSplitRows(false);
        }
        if (options.contentWidths()) {
            applyContentWidths(table, data);
        }

        for (String header : data.headers()) {
            table.addCell(headerCell(header, headerFont, options));
        }

        Set<Integer> totalRows = new HashSet<>(data.totalRowIndices());
        for (int r = 0; r < data.rows().size(); r++) {
            List<Object> rowData = data.rows().get(r);
            boolean isTotal = options.emphasizeTotalRows() && totalRows.contains(r);
            for (int c = 0; c < rowData.size(); c++) {
                Object value = rowData.get(c);
                Font font = isTotal ? bold(bodyFont) : bodyFont;
                PdfPCell cell = new PdfPCell(
                        new Phrase(value == null ? "" : value.toString(), font));
                cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                cell.setPadding(2);
                if (c >= options.centerFromColumn()) {
                    cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                }
                if (options.cellBorders()) {
                    cell.setBorderWidth(0.4f);
                }
                if (isTotal && options.emphasizeTotalRows()) {
                    cell.setBackgroundColor(TOTAL_FILL);
                }
                table.addCell(cell);
            }
        }
        return table;
    }

    private PdfPCell headerCell(String header, Font headerFont, PdfStyleOptions options) {
        PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setVerticalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(2);
        if (options.cellBorders()) {
            cell.setBorderWidth(0.4f);
        }
        if (options.headerShading()) {
            cell.setBackgroundColor(HEADER_FILL);
        }
        return cell;
    }

    /**
     * Shares the table width in proportion to how much content each column holds.
     *
     * <p>Without this every column is the same width, so a two-word "Enrollment
     * No." gets as much space as a long student name and the report reads as a
     * grid of truncated text. Shares are clamped so one very wide column cannot
     * starve the rest and no column becomes unusably narrow.</p>
     */
    private void applyContentWidths(PdfPTable table, ExportData data) {
        int columnCount = data.headers().size();
        if (columnCount == 0) {
            return;
        }
        float[] weights = new float[columnCount];
        float total = 0f;
        for (int c = 0; c < columnCount; c++) {
            weights[c] = contentWeight(data, c);
            total += weights[c];
        }
        if (total <= 0f) {
            return;
        }
        float[] percentages = new float[columnCount];
        for (int c = 0; c < columnCount; c++) {
            percentages[c] = Math.max(MIN_COLUMN_PERCENT,
                    Math.min(MAX_COLUMN_PERCENT, (weights[c] / total) * 100f));
        }
        normalise(percentages);
        try {
            table.setWidths(percentages);
        } catch (DocumentException e) {
            // A width profile the page cannot satisfy is not worth failing an
            // export over; the equal-width fallback is still readable.
            table.setWidthPercentage(100);
        }
    }

    /** How much horizontal space a column's longest value needs, in characters. */
    private static float contentWeight(ExportData data, int column) {
        float weight = column < data.headers().size() ? data.headers().get(column).length() : 0;
        for (List<Object> row : data.rows()) {
            if (column < row.size() && row.get(column) != null) {
                weight = Math.max(weight, row.get(column).toString().length());
            }
        }
        return Math.max(weight, 4f);
    }

    /** Scales the shares back to exactly 100% after clamping. */
    private static void normalise(float[] percentages) {
        float sum = 0f;
        for (float value : percentages) {
            sum += value;
        }
        if (sum <= 0f || sum == 100f) {
            return;
        }
        for (int i = 0; i < percentages.length; i++) {
            percentages[i] = percentages[i] * 100f / sum;
        }
    }

    private static Font bold(Font font) {
        return new Font(font.getBaseFont(), font.getSize(), Font.BOLD, font.getColor());
    }

    private static float resolveFontScale(PdfStyleOptions options, boolean wide) {
        Float configured = options.fontScale();
        return configured != null ? configured
                : (wide ? LANDSCAPE_FONT_SCALE : PORTRAIT_FONT_SCALE);
    }

    private static float[] resolveMargins(PdfStyleOptions options, boolean wide) {
        Float[] configured = options.pageMargins();
        if (configured != null && configured.length == 4) {
            return new float[]{configured[0], configured[1], configured[2], configured[3]};
        }
        return wide ? LANDSCAPE_MARGINS.clone() : PORTRAIT_MARGINS.clone();
    }
}