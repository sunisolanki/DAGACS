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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentImportParserTest {

    private static final String HEADER = "Name,Roll Number,"
            + "Email,Gender,Father Name,Mother Name,Photo URL,Enrollment Number,Age,"
            + "Admission Date,Batch,Section,Semester";

    private final StudentImportParser parser = new StudentImportParser();

    @Test
    void parsesValidCsv() {
        byte[] bytes = csv(HEADER,
                "Rahul Kumar,2201CE001,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,B1,A",
                "Anjali,2201CE002,,F,,,https://x/y.png,ENR-002,19,,B1,");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("Rahul Kumar", rows.get(0).values().get("name"));
        assertEquals("2201CE001", rows.get(0).values().get("rollNumber"));
        assertEquals("stu1@dagacs.local", rows.get(0).values().get("email"));
        assertEquals("https://x/y.png", rows.get(1).values().get("photoUrl"));
        assertEquals("B1", rows.get(0).values().get("batch"));
        assertEquals("A", rows.get(0).values().get("section"));
    }

    @Test
    void stripsUtf8BomFromCsv() {
        byte[] bytes = ("\uFEFF" + HEADER + "\n"
                + "Rahul Kumar,2201CE001,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,B1\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("Rahul Kumar", rows.get(0).values().get("name"));
    }

    @Test
    void preservesCommasInsideQuotedCsvField() {
        byte[] bytes = csv(HEADER,
                "\"Kumar, Rahul\",2201CE001,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,B1,");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("Kumar, Rahul", rows.get(0).values().get("name"));
    }

    @Test
    void skipsBlankCsvRows_butKeepsPhysicalLineNumbers() {
        byte[] bytes = csv(HEADER, "", " ", ",,,,,,,,,,,,,,",
                "Rahul Kumar,2201CE001,stu1@dagacs.local,M,,,,,ENR-001,20,2026-01-01,B1,");

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
        byte[] bytes = ("name,roll number,"
                + "email,gender,father name,mother name,photo url,enrollment number,age,"
                + "admission date,batch,section,semester\n"
                + "Rahul Kumar,2201CE001,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,B1,A\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("2201CE001", rows.get(0).values().get("rollNumber"));
    }

    @Test
    void missingRequiredColumnRejected() {
        byte[] bytes = ("Name\n"
                + "Rahul Kumar\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        AuthException ex = assertThrows(AuthException.class,
                () -> parser.parse("students.csv", bytes));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Header is missing required column(s)"));
    }

    @Test
    void unknownColumnIgnored() {
        byte[] bytes = csv(HEADER + ",Extra",
                "Rahul Kumar,2201CE001,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,B1,A,extraValue");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("Rahul Kumar", rows.get(0).values().get("name"));
        assertTrue(rows.get(0).values().containsKey("rollNumber"));
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

    @Test
    void headerAliases_programId_batchId_sectionId_accepted() {
        String header = "Name,Roll Number,Program ID,Batch ID,Section ID";
        byte[] bytes = csv(header,
                "Rahul Kumar,2201CE001,M.Tech,Batch1,SectionA");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("M.Tech", rows.get(0).values().get("program"));
        assertEquals("Batch1", rows.get(0).values().get("batch"));
        assertEquals("SectionA", rows.get(0).values().get("section"));
    }

    @Test
    void semesterColumn_parsed() {
        String header = "Name,Roll Number,Batch,Section,Semester";
        byte[] bytes = csv(header,
                "Rahul Kumar,2201CE001,B1,A,3rd Semester");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("3rd Semester", rows.get(0).values().get("semester"));
    }

    @Test
    void enrollmentNumberAbsent_isNullInParsedRow() {
        String header = "Name,Roll Number";
        byte[] bytes = csv(header,
                "Rahul Kumar,2201CE001");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("2201CE001", rows.get(0).values().get("rollNumber"));
        assertNull(rows.get(0).values().get("enrollmentNumber"));
    }

    @Test
    void statusColumn_notInOptionalColumns() {
        byte[] bytes = csv(HEADER + ",Status",
                "Rahul Kumar,2201CE001,stu1@dagacs.local,M,Father,Mother,,ENR-001,20,2026-01-01,B1,A,ACTIVE");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).values().containsKey("name"));
        assertTrue(rows.get(0).values().containsKey("rollNumber"));
        assertTrue(!rows.get(0).values().containsKey("status"));
    }

    @Test
    void rollNumberTrimOnlyPreservesCase() {
        byte[] bytes = csv(HEADER,
                "Student A,MT25CSE001,stu1@dagacs.local,M,,,ENR-001,20,2026-01-01,B1,A");

        List<StudentImportParser.StudentImportRow> rows = parser.parse("students.csv", bytes);

        assertEquals(1, rows.size());
        assertEquals("MT25CSE001", rows.get(0).values().get("rollNumber"));
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
            data.createCell(2).setCellValue("stu1@dagacs.local");
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
