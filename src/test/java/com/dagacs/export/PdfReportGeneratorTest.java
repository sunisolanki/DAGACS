package com.dagacs.export;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the M7.2 PDF generator: valid %PDF- header, title/metadata and
 * table data present, empty-rows case still produces a readable document.
 */
class PdfReportGeneratorTest {

    private final PdfReportGenerator generator = new PdfReportGenerator();

    @Test
    void generate_populatedRows_producesValidPdfWithTitleAndData() throws Exception {
        byte[] bytes = generator.generate(new ExportData(
                "My Report", "Teacher: A | all dates", "ignore",
                List.of("Subject Code", "Present"), List.of(List.of("S001", 3L))));

        assertTrue(bytes.length > 0);
        String header = new String(Arrays.copyOf(bytes, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);

        PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String text = extractor.getTextFromPage(1);
            assertTrue(text.contains("My Report"));
            assertTrue(text.contains("Teacher: A | all dates"));
            assertTrue(text.contains("S001"));
            assertTrue(text.contains("3"));
        } finally {
            reader.close();
        }
    }

    @Test
    void generate_emptyRows_stillProducesValidPdfWithHeaders() throws Exception {
        byte[] bytes = generator.generate(new ExportData(
                "Empty Report", "all dates", "ignore",
                List.of("Subject Code", "Present"), List.of()));

        assertTrue(bytes.length > 0);
        String header = new String(Arrays.copyOf(bytes, 5), StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
        PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String text = extractor.getTextFromPage(1);
            assertTrue(text.contains("Empty Report"));
            assertTrue(text.contains("Subject Code"));
        } finally {
            reader.close();
        }
    }
}