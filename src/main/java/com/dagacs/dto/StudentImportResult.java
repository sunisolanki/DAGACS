package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * M9.10 Student bulk import result (all-or-nothing).
 * <p>
 * Mirrors the SOW "import summary report" contract (validation, duplicate
 * detection by roll number, summary report): {@code totalRows} counts the
 * non-empty data rows read from the file, {@code importedRows} the profiles
 * actually persisted, {@code rejectedRows} the rows that failed validation.
 * Because the import is all-or-nothing, {@code rejectedRows > 0} always implies
 * {@code importedRows == 0} - nothing from the file is persisted on failure.
 * </p>
 * <p>
 * {@code errors} carries one entry per rejected row. {@link RowError#rowNumber}
 * is the physical file row (Excel row number / CSV record offset from the
 * header); a value of {@code 0} denotes a file-level (non-row) problem. Each
 * entry also mirrors the DAGACS HTTP status that applies to that row
 * ({@code 400} validation, {@code 409} conflict) - the controller derives the
 * overall response status from these.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportResult {

    private int totalRows;

    private int importedRows;

    private int rejectedRows;

    private String message;

    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private int rowNumber;
        private String field;
        private String message;
        private int status;
    }
}