package com.dagacs.export;

import java.util.Set;

/**
 * Phase 4B: the professional formatting applied on top of a rendered workbook.
 *
 * <p><b>Every field defaults to the behaviour that already existed</b>, so
 * {@link #defaults()} reproduces the pre-4B output exactly. Formatting is
 * therefore strictly opt-in: a report that does not ask for it gets the Phase 3 /
 * Phase 4A layout, unchanged.
 *
 * <p><b>This is the Teacher M7.2 regression lock.</b>
 * {@link ExcelReportGenerator} is shared - the M7.2 department and teacher
 * reports in {@code ReportExportService} also render through it. Because the
 * defaults are the old behaviour and no existing call site passes an options
 * object, Teacher output cannot change as a result of Phase 4B being added. That
 * is a structural guarantee rather than a promise, which is why the options are a
 * record with one {@code defaults()} factory instead of a set of setters.</p>
 *
 * <p><b>Why percentages are declared by header name.</b> A report knows its own
 * column labels; asking the caller to compute column indices would be a second
 * place to get out of step with the table it is describing. A header that is not
 * present is simply never matched, so a stale declaration cannot corrupt an
 * unrelated column.</p>
 *
 * <p>Instances are immutable and safe to share.</p>
 */
public record ExcelStyleOptions(boolean printSetup,
                                boolean landscape,
                                boolean fitToWidth,
                                boolean repeatHeaderRows,
                                boolean autoFilter,
                                boolean freezeHeader,
                                int freezeIdentityColumns,
                                Set<String> percentageHeaders,
                                Set<String> integerHeaders,
                                boolean emphasizeTotalRows) {

    /** Identity columns pinned when {@code freezeHeader} is on; matches Phase 3. */
    private static final int DEFAULT_IDENTITY_COLUMNS = 2;

    /**
     * The pre-Phase-4B behaviour, field for field.
     *
     * <p>This is the value every existing render path uses, and the reason Phase
     * 4B cannot alter Teacher M7.2 or any other existing consumer.</p>
     */
    public static ExcelStyleOptions defaults() {
        return new ExcelStyleOptions(false, false, false, false, false, false,
                DEFAULT_IDENTITY_COLUMNS, Set.of(), Set.of(), false);
    }

    public ExcelStyleOptions {
        percentageHeaders = percentageHeaders == null ? Set.of() : Set.copyOf(percentageHeaders);
        integerHeaders = integerHeaders == null ? Set.of() : Set.copyOf(integerHeaders);
    }

    /**
     * The Phase 4A freezing behaviour, expressed through the options.
     *
     * <p>Preserved so the existing {@code generate(data, freezeHeader)} signature
     * keeps producing exactly what it always did.</p>
     */
    public ExcelStyleOptions withFreeze(boolean freeze) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, repeatHeaderRows,
                autoFilter, freeze, freezeIdentityColumns, percentageHeaders, integerHeaders,
                emphasizeTotalRows);
    }

    /** Copy of this configuration with the landscape flag replaced. */
    public ExcelStyleOptions withLandscape(boolean value) {
        return new ExcelStyleOptions(printSetup, value, fitToWidth, repeatHeaderRows,
                autoFilter, freezeHeader, freezeIdentityColumns, percentageHeaders,
                integerHeaders, emphasizeTotalRows);
    }

    /** Copy of this configuration with print setup enabled or disabled. */
    public ExcelStyleOptions withPrintSetup(boolean value) {
        return new ExcelStyleOptions(value, landscape, fitToWidth, repeatHeaderRows,
                autoFilter, freezeHeader, freezeIdentityColumns, percentageHeaders,
                integerHeaders, emphasizeTotalRows);
    }

    /** Copy of this configuration with fit-to-width enabled or disabled. */
    public ExcelStyleOptions withFitToWidth(boolean value) {
        return new ExcelStyleOptions(printSetup, landscape, value, repeatHeaderRows,
                autoFilter, freezeHeader, freezeIdentityColumns, percentageHeaders,
                integerHeaders, emphasizeTotalRows);
    }

    /** Copy of this configuration with repeating print header rows toggled. */
    public ExcelStyleOptions withRepeatHeaderRows(boolean value) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, value,
                autoFilter, freezeHeader, freezeIdentityColumns, percentageHeaders,
                integerHeaders, emphasizeTotalRows);
    }

    /** Copy of this configuration with autofilter enabled or disabled. */
    public ExcelStyleOptions withAutoFilter(boolean value) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, repeatHeaderRows,
                value, freezeHeader, freezeIdentityColumns, percentageHeaders, integerHeaders,
                emphasizeTotalRows);
    }

    /** Copy of this configuration with the header row frozen or unfrozen. */
    public ExcelStyleOptions withFreezeHeader(boolean value) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, repeatHeaderRows,
                autoFilter, value, freezeIdentityColumns, percentageHeaders, integerHeaders,
                emphasizeTotalRows);
    }

    /** Copy of this configuration with emphasized total rows enabled or disabled. */
    public ExcelStyleOptions withEmphasizedTotalRows(boolean value) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, repeatHeaderRows,
                autoFilter, freezeHeader, freezeIdentityColumns, percentageHeaders, integerHeaders,
                value);
    }

    /** Copy of this configuration declaring the given columns as percentages. */
    public ExcelStyleOptions withPercentageHeaders(String... headers) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, repeatHeaderRows,
                autoFilter, freezeHeader, freezeIdentityColumns, Set.of(headers), integerHeaders,
                emphasizeTotalRows);
    }

    /** Copy of this configuration declaring the given columns as plain counts. */
    public ExcelStyleOptions withIntegerHeaders(String... headers) {
        return new ExcelStyleOptions(printSetup, landscape, fitToWidth, repeatHeaderRows,
                autoFilter, freezeHeader, freezeIdentityColumns, percentageHeaders, Set.of(headers),
                emphasizeTotalRows);
    }
}