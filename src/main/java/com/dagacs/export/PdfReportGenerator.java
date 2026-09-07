package com.dagacs.export;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Minimal OpenPDF-based PDF builder for M7.2 exports. PDF is justified by the
 * corrected SOW for official records and shareable reports with the same query
 * layer as the API. Produces a plain, readable table PDF (no branding/charts/
 * logos/signatures, per scope). One library only, no report designer/framework.
 */
@Component
public class PdfReportGenerator {

    public byte[] generate(ExportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, 9, Font.NORMAL);

            document.add(new Paragraph(data.title(), titleFont));
            if (data.subtitle() != null && !data.subtitle().isBlank()) {
                document.add(new Paragraph(data.subtitle(), normalFont));
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
            table.setHeaderRows(1);
            for (String header : data.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                table.addCell(cell);
            }
            for (List<Object> rowData : data.rows()) {
                for (Object value : rowData) {
                    table.addCell(new PdfPCell(new Phrase(value == null ? "" : value.toString(), bodyFont)));
                }
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }
}