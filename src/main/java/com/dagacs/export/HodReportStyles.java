package com.dagacs.export;

/**
 * Phase 4B: the professional formatting each HOD attendance report asks for.
 *
 * <p>Every option is opt-in and every default here reproduces what the report
 * looked like in Phase 4A, so turning this class off entirely would change
 * nothing except the formatting. The layout itself - columns, ordering, values -
 * is not affected by any of this; this class only decides how a fixed table is
 * presented.</p>
 *
 * <h3>Why each report differs</h3>
 * <ul>
 *   <li><b>Matrix</b> - the only genuinely wide report. Landscape plus
 *       fit-to-width, because a cross-tab clipped across a portrait page is
 *       unusable, and a repeating header row so every printed page keeps its
 *       column labels. Deliberately <b>no</b> autofilter: the subject columns
 *       are dynamic, so filtering a row would hide a student's other subjects and
 *       invite a misreading of their total.</li>
 *   <li><b>Overview, Low, Subject</b> - narrow, portrait, autofilter so a HOD can
 *       sort and pick out the rows they care about.</li>
 *   <li><b>Student</b> - narrow, portrait, and its TOTAL row is emphasised so the
 *       figure that matters is the one that reads first.</li>
 * </ul>
 *
 * <p>Percentage columns are declared by their header text. Only the column that
 * actually holds a percentage is converted to a numeric cell, and
 * {@code "N/A"} - a percentage the Phase 3 resolver could not compute because no
 * class was conducted - stays literal text in every report.</p>
 */
final class HodReportStyles {

    private HodReportStyles() {
    }

    /** The shared professional baseline: A4 print setup, frozen header, portrait. */
    private static ExcelStyleOptions base() {
        return ExcelStyleOptions.defaults()
                .withPrintSetup(true)
                .withFreezeHeader(true)
                .withRepeatHeaderRows(true);
    }

    /**
     * The wide cross-tab.
     *
     * <p>Landscape and fit-to-width are the whole point of Phase 4B for this
     * report: without them the subject columns are simply cut off when printed.</p>
     */
    static ExcelStyleOptions matrix() {
        return base()
                .withLandscape(true)
                .withFitToWidth(true)
                .withAutoFilter(false)
                .withPercentageHeaders("Overall Attendance %")
                .withIntegerHeaders("Total Present", "Total Classes");
    }

    /** The context-wide subject summary: narrow, sortable. */
    static ExcelStyleOptions overview() {
        return base()
                .withAutoFilter(true)
                .withPercentageHeaders("Average Attendance")
                .withIntegerHeaders("Total Classes", "Students", "Below Threshold");
    }

    /** The threshold report: narrow, sortable, so a HOD can rank the students. */
    static ExcelStyleOptions lowAttendance() {
        return base()
                .withAutoFilter(true)
                .withPercentageHeaders("Attendance %")
                .withIntegerHeaders("Present", "Total Classes", "Subjects Below Threshold");
    }

    /** One student's breakdown; the TOTAL row is the figure being reported on. */
    static ExcelStyleOptions student() {
        return base()
                .withAutoFilter(false)
                .withEmphasizedTotalRows(true)
                .withPercentageHeaders("Attendance %")
                .withIntegerHeaders("Present", "Total Classes");
    }

    /** One subject's roster, sorted worst-first by the canonical service. */
    static ExcelStyleOptions subject() {
        return base()
                .withAutoFilter(true)
                .withPercentageHeaders("Attendance %")
                .withIntegerHeaders("Present", "Total Classes");
    }
}