package com.dagacs.studentimport;

import com.dagacs.exception.AuthException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class StudentImportParser {

    public static final List<String> REQUIRED_COLUMNS = List.of(
            "name", "rollNumber", "academicSession", "program");

    public static final List<String> OPTIONAL_COLUMNS = List.of(
            "email", "gender", "fatherName", "motherName", "photoUrl",
            "enrollmentNumber", "age", "admissionDate", "status",
            "batch", "section", "semester");

    private static final Map<String, String> HEADER_ALIASES = buildAliases();

    private static final int MAX_ROWS = 2000;

    private static final Set<String> KNOWN_COLUMNS = new HashSet<>();

    static {
        KNOWN_COLUMNS.addAll(REQUIRED_COLUMNS);
        KNOWN_COLUMNS.addAll(OPTIONAL_COLUMNS);
    }

    public record StudentImportRow(int rowNumber, Map<String, String> values) {
    }

    public List<StudentImportRow> parse(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new AuthException("The uploaded file is empty", 400);
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return parseXlsx(bytes);
        }
        if (lower.endsWith(".csv")) {
            return parseCsv(bytes);
        }
        throw new AuthException(
                "Unsupported file type. Upload an .xlsx or .csv file", 400);
    }

    private List<StudentImportRow> parseXlsx(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            Map<Integer, String> indexToColumn = mapHeaderCells(headerRow, formatter);
            if (indexToColumn.isEmpty()) {
                throw new AuthException("The file has no header row", 400);
            }
            int maxIndex = Collections.max(indexToColumn.keySet());

            List<StudentImportRow> rows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c <= maxIndex; c++) {
                    String column = indexToColumn.get(c);
                    if (column == null) {
                        continue;
                    }
                    Cell cell = row.getCell(c);
                    String value = cell == null
                            ? ""
                            : formatter.formatCellValue(cell, null).trim();
                    values.put(column, value);
                }
                addRowIfPresent(rows, r + 1, values);
            }
            return rows;
        } catch (AuthException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new AuthException(
                    "Could not read the XLSX file. Upload a valid .xlsx workbook", 400);
        }
    }

    private List<StudentImportRow> parseCsv(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        List<List<String>> records = parseCsvRecords(content);
        if (records.isEmpty()) {
            throw new AuthException("The CSV file is empty", 400);
        }
        List<String> columns = mapHeaderLine(records.get(0));

        List<StudentImportRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.stream().allMatch(value -> value == null || value.isBlank())) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                String value = c < record.size() ? record.get(c) : "";
                values.put(columns.get(c), value.trim());
            }
            addRowIfPresent(rows, i + 1, values);
        }
        return rows;
    }

    private void addRowIfPresent(List<StudentImportRow> rows, int rowNumber,
                                     Map<String, String> values) {
        if (values.values().stream().allMatch(String::isEmpty)) {
            return;
        }
        if (rows.size() >= MAX_ROWS) {
            throw new AuthException(
                    "File exceeds the maximum supported row count of " + MAX_ROWS, 400);
        }
        rows.add(new StudentImportRow(rowNumber, values));
    }

    private Map<Integer, String> mapHeaderCells(Row headerRow, DataFormatter formatter) {
        if (headerRow == null || headerRow.getLastCellNum() <= 0) {
            throw new AuthException("The file has no header row", 400);
        }
        Map<Integer, String> indexToColumn = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            String raw = cell == null ? "" : formatter.formatCellValue(cell, null).trim();
            if (raw.isEmpty()) {
                continue;
            }
            String key = HEADER_ALIASES.get(normalizeHeader(raw));
            if (key == null) {
                throw new AuthException(
                        "Unknown column '" + raw + "'. Expected columns: " + expectedColumns(), 400);
            }
            if (!seen.add(key)) {
                throw new AuthException("Duplicate column '" + raw + "' in the header", 400);
            }
            indexToColumn.put(c, key);
        }
        validateRequiredColumns(seen);
        return indexToColumn;
    }

    private List<String> mapHeaderLine(List<String> header) {
        if (header.isEmpty() || header.stream().allMatch(String::isBlank)) {
            throw new AuthException("The file has no header row", 400);
        }
        List<String> mapped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : header) {
            if (raw.isBlank()) {
                throw new AuthException("The comma-delimited header contains a blank column", 400);
            }
            String key = HEADER_ALIASES.get(normalizeHeader(raw));
            if (key == null) {
                throw new AuthException(
                        "Unknown column '" + raw + "'. Expected columns: " + expectedColumns(), 400);
            }
            if (!seen.add(key)) {
                throw new AuthException("Duplicate column '" + raw + "' in the header", 400);
            }
            mapped.add(key);
        }
        validateRequiredColumns(seen);
        return mapped;
    }

    private void validateRequiredColumns(Set<String> seen) {
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(column -> !seen.contains(column))
                .toList();
        if (!missing.isEmpty()) {
            throw new AuthException("Header is missing required column(s): "
                    + displayNames(missing), 400);
        }
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    private static List<String> displayNames(List<String> canonical) {
        List<String> names = new ArrayList<>();
        for (String column : canonical) {
            names.add(displayName(column));
        }
        return names;
    }

    private static String displayName(String canonical) {
        switch (canonical) {
            case "rollNumber": return "Roll Number";
            case "fatherName": return "Father Name";
            case "motherName": return "Mother Name";
            case "photoUrl": return "Photo URL";
            case "enrollmentNumber": return "Enrollment Number";
            case "admissionDate": return "Admission Date";
            case "academicSession": return "Academic Session";
            case "program": return "Program";
            case "batch": return "Batch";
            case "section": return "Section";
            case "semester": return "Semester";
            default:
                return canonical.substring(0, 1).toUpperCase(Locale.ROOT)
                        + canonical.substring(1);
        }
    }

    private static String expectedColumns() {
        return "Required: " + REQUIRED_COLUMNS.stream().map(c -> displayName(c)).reduce((a, b) -> a + ", " + b).orElse("")
                + ". Optional: " + OPTIONAL_COLUMNS.stream().map(c -> displayName(c)).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("rollnumber", "rollNumber");
        aliases.put("email", "email");
        aliases.put("name", "name");
        aliases.put("gender", "gender");
        aliases.put("fathername", "fatherName");
        aliases.put("mothername", "motherName");
        aliases.put("photourl", "photoUrl");
        aliases.put("enrollmentnumber", "enrollmentNumber");
        aliases.put("age", "age");
        aliases.put("admissiondate", "admissionDate");
        aliases.put("status", "status");
        aliases.put("academicsession", "academicSession");
        aliases.put("program", "program");
        aliases.put("batch", "batch");
        aliases.put("section", "section");
        aliases.put("semester", "semester");
        aliases.put("programid", "program");
        aliases.put("batchid", "batch");
        aliases.put("sectionid", "section");
        return Map.copyOf(aliases);
    }

    static List<List<String>> parseCsvRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int length = content.length();
        int i = 0;
        while (i < length) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                i++;
                continue;
            }
            if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
                i++;
                continue;
            }
            if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
                i++;
                continue;
            }
            if (c == '\r') {
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
                i += (i + 1 < length && content.charAt(i + 1) == '\n') ? 2 : 1;
                continue;
            }
            field.append(c);
            i++;
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }
}
