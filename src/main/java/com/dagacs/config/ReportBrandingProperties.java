package com.dagacs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 4A report branding, bound to {@code app.report.branding.*}.
 *
 * <p><b>Institution name only.</b> The application has no institution entity and
 * no institution column anywhere in the schema, so the name that prints on an
 * exported report is deployment configuration rather than fabricated data. It is
 * never read from the request and never trusted from a client.</p>
 *
 * <p><b>The department name is deliberately NOT configurable here.</b> A
 * configurable department line would let a deployment print a department its HOD
 * does not belong to. The department line on every HOD report comes from the
 * real {@code Department.name} of the authenticated HOD, resolved from the JWT
 * by {@code AuthenticatedHodResolver} - the same source the on-screen reports
 * use. See {@code HodExportHeader}.</p>
 *
 * <p>Env-overridable, with the placeholder carrying the development-safe default
 * {@code DAGACS}, so production can brand an institution without a code change
 * or a rebuild.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.report.branding")
public class ReportBrandingProperties {

    /**
     * The institution line printed above every report title.
     *
     * <p>Falls back to {@code DAGACS} when a deployment blanks the property, so
     * the header line is never left empty and never rendered as a placeholder.</p>
     */
    private String institutionName = "DAGACS";

    /**
     * The institution line actually used, with a defensive fallback.
     *
     * <p>A null, empty or whitespace-only configuration resolves to the default
     * rather than producing a report with a blank heading.</p>
     */
    public String resolvedInstitutionName() {
        return (institutionName == null || institutionName.isBlank())
                ? "DAGACS"
                : institutionName.trim();
    }
}
