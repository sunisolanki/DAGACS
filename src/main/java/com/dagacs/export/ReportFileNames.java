package com.dagacs.export;

import java.time.LocalDate;

/**
 * Deterministic, ASCII-safe export filenames (M7.2). Filenames encode role,
 * report type and the requested inclusive date filter so the same report with
 * different filters never collides. No filesystem path is ever exposed.
 */
public final class ReportFileNames {

    private ReportFileNames() {
    }

    public static String forHod(String reportType, LocalDate startDate, LocalDate endDate, String extension) {
        return "dagacs_hod_" + reportType + dateSuffix(startDate, endDate) + "." + extension;
    }

    public static String forTeacher(LocalDate startDate, LocalDate endDate, String extension) {
        return "dagacs_teacher_subject_wise" + dateSuffix(startDate, endDate) + "." + extension;
    }

    public static String forTeacherStudentWise(LocalDate startDate, LocalDate endDate, String extension) {
        return "dagacs_teacher_student_wise" + dateSuffix(startDate, endDate) + "." + extension;
    }

    private static String dateSuffix(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return "_" + startDate + "_to_" + endDate;
        }
        if (startDate != null) {
            return "_" + startDate + "_onwards";
        }
        if (endDate != null) {
            return "_to_" + endDate;
        }
        return "_all";
    }
}