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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Minimal OpenPDF-based PDF builder for M7.2 exports. PDF is justified by the
 * corrected SOW for official records and shareable reports with the same query
 * layer as the API. Produces a plain, readable table PDF (no branding/charts/
 * logos/signatures, per scope). One library only, no report designer/framework.
 *
 * <p>The student-wise attendance register carries one column per conducted
 * session date and is flagged {@link ExportData#landscape()}, so it renders on
 * a landscape page and repeats its header row on every page.
 */
@Component
public class PdfReportGenerator {

    /** A4 with width and height swapped, for the wide student-wise register. */
    private static final Rectangle LANDSCAPE_A4 =
            new Rectangle(PageSize.A4.getHeight(), PageSize.A4.getWidth());

    public byte[] generate(ExportData data) {
        boolean wide = data.landscape();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Explicit width/height swap: Rectangle.rotate() is a no-op while
            // the page rotation is 0, which silently yields a portrait page.
            Document document = wide
                    ? new Document(LANDSCAPE_A4, 24, 24, 30, 24)
                    : new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            float scale = wide ? 6.5f : 9f;
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
                // OpenPDF only emits a PdfPTable's header row once a body row has
                // been completed, so an empty report renders its headers as a
                // plain header line instead of a phantom table.
                document.add(new Paragraph(String.join("   |   ", data.headers()), headerFont));
                document.close();
                return out.toByteArray();
            }

            PdfPTable table = new PdfPTable(data.headers().size());
            table.setWidthPercentage(100);
            // Repeats the header on every page.
            table.setHeaderRows(1);
            for (String header : data.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                cell.setPadding(2);
                table.addCell(cell);
            }
            for (List<Object> rowData : data.rows()) {
                for (int c = 0; c < rowData.size(); c++) {
                    Object value = rowData.get(c);
                    PdfPCell cell = new PdfPCell(
                            new Phrase(value == null ? "" : value.toString(), bodyFont));
                    cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                    cell.setPadding(2);
                    if (c >= 2) {
                        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    }
                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }
}
