package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated envelope for the ADMIN Manage Students list. Returned by
 * {@code GET /api/admin/students} instead of a bare list so the screen can
 * render server-side pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPageResponse {

    private List<StudentManagementDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}