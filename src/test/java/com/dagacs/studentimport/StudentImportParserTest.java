package com.dagacs.studentimport;

import com.dagacs.exception.AuthException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentImportParserTest {

    private static final String HEADER = "Name,Roll Number,Academic Session,Program,"
            + "Email,Gender,Father Name,Mother Name,Photo URL,Enrollment Number,Age,"
            + "Admission Date,Status,Batch,Section";

    private final StudentImportParser parser = new StudentImportParser();

    @Test
    void parsesValidCsv() {
        byte[] bytes = csv(HEADER,
                "Rahul Kumar,2201CE001,2025-26,B.Tech,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,ACTIVE,B1,A",
                "Anjali,2201CE002,2025-26,B.Tech,,F,,,https://x/y.png,ENR-002,19,,INACTIVE,B1,");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("Rahul Kumar", rows.get(0).values().get("name"));
        assertEquals("2201CE001", rows.get(0).values().get("rollNumber"));
        assertEquals("2025-26", rows.get(0).values().get("academicSession"));
        assertEquals("B.Tech", rows.get(0).values().get("program"));
        assertEquals("stu1@dagacs.local", rows.get(0).values().get("email"));
        assertEquals("", rows.get(0).values().get("photoUrl"));
        assertEquals("B1", rows.get(0).values().get("batch"));
        assertEquals("A", rows.get(0).values().get("section"));
        assertEquals("", rows.get(1).values().get("email"));
        assertEquals("https://x/y.png", rows.get(1).values().get("photoUrl"));
    }

    @Test
    void stripsUtf8BomFromCsv() {
        byte[] bytes = ("\uFEFF" + HEADER + "\n"
                + "Rahul Kumar,2201CE001,2025-26,B.Tech,,,,,,,,ACTIVE,B1\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("Rahul Kumar", rows.get(0).values().get("name"));
    }

    @Test
    void preservesCommasInsideQuotedCsvField() {
        byte[] bytes = csv(HEADER,
                "\"Kumar, Rahul\",2201CE001,2025-26,B.Tech,,,,,,,,,B1,");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("Kumar, Rahul", rows.get(0).values().get("name"));
    }

    @Test
    void skipsBlankCsvRows_butKeepsPhysicalLineNumbers() {
        byte[] bytes = csv(HEADER, "", " ", ",,,,,,,,,,,,,,",
                "Rahul Kumar,2201CE001,2025-26,B.Tech,,,,,,,,,B1,");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals(5, rows.get(0).rowNumber());
    }

    @Test
    void parsesNumericRollNumberCellFromXlsx() throws Exception {
        byte[] bytes = xlsxWithNumericRoll();

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.xlsx", bytes);

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals(2201001L, Long.parseLong(rows.get(0).values().get("rollNumber")));
    }

    @Test
    void headerIsCaseInsensitiveAndOrderInsensitive() {
        byte[] bytes = ("name,roll number,academic session,program,"
                + "email,gender,father name,mother name,photo url,enrollment number,age,"
                + "admission date,status,batch,section\n"
                + "Rahul Kumar,2201CE001,2025-26,B.Tech,,,,,,,,,B1,A\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("2201CE001", rows.get(0).values().get("rollNumber"));
    }

    @Test
    void missingRequiredColumnRejected() {
        byte[] bytes = ("Name,Roll Number,Program\n"
                + "Rahul Kumar,2201CE001,B.Tech\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        AuthException ex = assertThrows(AuthException.class,
                () -> parser.parse("students.csv", bytes));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Header is missing required column(s)"));
    }

    @Test
    void unknownColumnRejected() {
        byte[] bytes = csv(HEADER + ",Extra",
                "Rahul Kumar,2201CE001,2025-26,B.Tech,,,,,,,,,B1,A,x");

        AuthException ex = assertThrows(AuthException.class,
                () -> parser.parse("students.csv", bytes));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Unknown column 'Extra'"));
    }

    @Test
    void unsupportedExtensionRejected() {
        byte[] bytes = "not-a-file".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        AuthException ex = assertThrows(AuthException.class,
                () -> parser.parse("students.txt", bytes));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void emptyFileRejected() {
        AuthException ex = assertThrows(AuthException.class,
                () -> parser.parse("students.csv", new byte[0]));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void headerOnlyCsvReturnsNoRows() {
        byte[] bytes = (HEADER + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        List<StudentImportParser.StudentImportRow> rows =
                parser.parse("students.csv", bytes);

        assertTrue(rows.isEmpty());
    }

    @Test
    void corruptedXlsxRejected() {
        byte[] bytes = "this-is-not-a-workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        AuthException ex = assertThrows(AuthException.class,
                () -> parser.parse("students.xlsx", bytes));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Could not read the XLSX file"));
    }

    private static byte[] xlsxWithNumericRoll() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Students");
            String[] header = headerArray();
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < header.length; c++) {
                headerRow.createCell(c).setCellValue(header[c]);
            }
            Row data = sheet.createRow(1);
            data.createCell(1).setCellValue(2201001.0);
            data.createCell(2).setCellValue("2025-26");
            data.createCell(3).setCellValue("B.Tech");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String[] headerArray() {
        return HEADER.split(",");
    }

    private static byte[] csv(String header, String... rows) {
        StringBuilder sb = new StringBuilder(header).append('\n');
        for (String row : rows) {
            sb.append(row).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
