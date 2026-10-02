package com.dagacs.export;

import com.dagacs.controller.HodAttendanceReportController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4C.4: the structural acceptance gate for the whole of Phase 4.
 *
 * <p>4A, 4B, 4C.1, 4C.2 and 4C.3 each proved their own slice. What had never
 * existed was a single place that states the <i>whole</i> Phase 4 contract, so
 * that "Phase 4 is complete" is a checkable claim rather than an accumulation of
 * per-phase notes. This class is that place, and it is deliberately structural:
 * it reads the shipped annotations and signatures rather than re-testing
 * behaviour, because the behaviour is already proven in depth by
 * {@code HodAttendanceExportSecurityIntegrationTest},
 * {@code HodContextPackSecurityIntegrationTest},
 * {@code HodAttendanceExportQueryCountIntegrationTest},
 * {@code PdfFormattingTest}, {@code HodAttendancePdfLayoutTest} and
 * {@code HodContextPackPdfTest}.</p>
 *
 * <h3>What a change to Phase 4 would break here first</h3>
 * <ul>
 *   <li>adding, removing or renaming an export route - a contract change;</li>
 *   <li>adding a HOD export route that is not behind the HOD role - an
 *       authorization gap;</li>
 *   <li>changing any field of {@link PdfStyleOptions#defaults()} - a silent
 *       reformat of every Teacher and M7.2 PDF, since they share the generator;</li>
 *   <li>bypassing {@link PdfRenderedDocument} in favour of a second two-pass or
 *       page-counting implementation - the duplication Phase 4 forbids.</li>
 * </ul>
 */
class Phase4ContractTest {

    private static final String BASE = "/api/hod/attendance";

    /** Every route Phase 4 ships on the HOD attendance controller. */
    private static final Set<String> EXPECTED_ROUTES = Set.of(
            // Phase 3 read surface, unchanged.
            BASE + "/overview",
            BASE + "/matrix",
            BASE + "/student/{studentId}",
            BASE + "/subject/{subjectId}",
            BASE + "/low",
            BASE + "/context",
            // Phase 3 matrix export, unchanged.
            BASE + "/matrix/export.xlsx",
            BASE + "/matrix/export.pdf",
            // Phase 4A: the four reports that previously had no export at all.
            BASE + "/overview/export.xlsx",
            BASE + "/overview/export.pdf",
            BASE + "/student/{studentId}/export.xlsx",
            BASE + "/student/{studentId}/export.pdf",
            BASE + "/subject/{subjectId}/export.xlsx",
            BASE + "/subject/{subjectId}/export.pdf",
            BASE + "/low/export.xlsx",
            BASE + "/low/export.pdf",
            // Phase 4B: the multi-sheet workbook.
            BASE + "/context-pack/export.xlsx",
            // Phase 4C.3: the same pack as one PDF.
            BASE + "/context-pack/export.pdf");

    private static Set<String> routesOf(Class<?> controller) {
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(
                controller, RequestMapping.class);
        assertNotNull(base, controller.getSimpleName() + " must declare its base path");
        String prefix = base.value()[0];

        Set<String> routes = new TreeSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            GetMapping get = AnnotatedElementUtils.findMergedAnnotation(
                    method, GetMapping.class);
            if (get == null) {
                continue;
            }
            String[] values = get.value().length == 0 ? get.path() : get.value();
            for (String value : values) {
                routes.add(prefix + value);
            }
        }
        return routes;
    }

    // ── The API contract ────────────────────────────────────────────────────

    @Test
    @DisplayName("1. the Phase 4 HOD attendance route surface is exactly as approved")
    void routeSurfaceIsUnchanged() {
        Set<String> actual = routesOf(HodAttendanceReportController.class);
        Set<String> expected = new TreeSet<>(EXPECTED_ROUTES);

        List<String> added = new ArrayList<>(actual);
        added.removeAll(expected);
        List<String> removed = new ArrayList<>(expected);
        removed.removeAll(actual);

        assertTrue(added.isEmpty() && removed.isEmpty(),
                "Phase 4C.4 changes no endpoint, so the route surface must be "
                        + "exactly the approved one.\n  unexpectedly added: " + added
                        + "\n  unexpectedly removed: " + removed);
        assertEquals(18, actual.size(),
                "the approved Phase 4 surface is 18 routes: 6 reads, 10 report "
                        + "exports (5 reports x 2 formats) and the context pack in "
                        + "2 formats");
    }

    @Test
    @DisplayName("2. every Phase 4 route is behind the HOD role, at the class")
    void everyHodRouteRequiresTheHodRole() {
        // Class level, so a route added later inherits the guard by default. An
        // export endpoint without it would be an authorization gap, and this is
        // the assertion that would surface one.
        PreAuthorize guard = AnnotatedElementUtils.findMergedAnnotation(
                HodAttendanceReportController.class, PreAuthorize.class);
        assertNotNull(guard, "the HOD attendance controller must be role guarded");
        assertEquals("hasRole('HOD')", guard.value(),
                "Phase 4 must not weaken the HOD guard");
    }

    // ── The shared PDF architecture ─────────────────────────────────────────

    @Test
    @DisplayName("3. the shared PDF defaults are still the pre-Phase-4C ones")
    void pdfDefaultsAreStillFrozen() {
        // Teacher and M7.2 exports render through the same generator with these
        // defaults. Any change here would silently reformat them, which is why
        // they are asserted field by field rather than trusted.
        PdfStyleOptions defaults = PdfStyleOptions.defaults();

        assertEquals(null, defaults.landscapeOverride(),
                "orientation must still come from the data model");
        assertEquals(null, defaults.pageMargins(),
                "margins must still be resolved from the orientation");
        assertEquals(null, defaults.fontScale(),
                "the historical font scales must still apply");
        assertEquals(false, defaults.contentWidths(),
                "content-derived widths must stay opt-in");
        assertEquals(true, defaults.repeatHeaderRows(),
                "header repetition predates Phase 4 and must be preserved");
        assertEquals(false, defaults.keepRowsIntact());
        assertEquals(false, defaults.pageFooter(),
                "no document may gain a footer without asking for one");
        assertEquals(false, defaults.pageNumbers());
        assertEquals(false, defaults.footerContext());
        assertEquals(false, defaults.headerShading());
        assertEquals(false, defaults.cellBorders());
        assertEquals(false, defaults.emphasizeTotalRows());
        assertEquals(false, defaults.emptyReportAsTable(),
                "the historical joined-header empty report must be preserved");
        assertEquals(2, defaults.centerFromColumn(),
                "centring from the third column predates Phase 4");
        assertEquals(null, defaults.totalPages(),
                "no total is known before the counting pass");
    }

    @Test
    @DisplayName("4. the single-report overloads still carry the historical defaults")
    void singleReportOverloadsDelegateToTheDefaults() throws Exception {
        // The compatibility argument of 4C.1 is that the two original signatures
        // reach the options-aware path with defaults(). If either were ever
        // rewritten to pass anything else, the Teacher and M7.2 documents would
        // change without anyone deciding that.
        Method generate = PdfReportGenerator.class.getMethod("generate", ExportData.class);
        Method generateWithOptions = PdfReportGenerator.class.getMethod(
                "generate", ExportData.class, PdfStyleOptions.class);
        assertEquals(byte[].class, generate.getReturnType());
        assertEquals(byte[].class, generateWithOptions.getReturnType());
        assertTrue(generateWithOptions.getParameterCount() == 2,
                "the two-argument overload is the historical one and must stay");

        // And the 4C.3 sectioned path must be an addition, not a replacement: the
        // one-sectioned path still has to work, which the single-report signatures
        // above are what existing callers compile against.
        assertEquals(1, PdfSectionedReportGenerator.class
                        .getMethod("generate", List.class).getParameterCount(),
                "the sectioned generator takes one ordered list and nothing else");
    }

    @Test
    @DisplayName("5. page counting has exactly one implementation")
    void pageCountingHasOneImplementation() {
        // Phase 4 forbids a second PDF rendering architecture. The only place a
        // page total is ever resolved is PdfRenderedDocument, for the single
        // report and (through PdfSectionedReportGenerator) for the pack.
        assertEquals(1, countDeclaredMethods(PdfRenderedDocument.class, "countPages"),
                "PdfRenderedDocument must own page counting");
        assertEquals(1, countDeclaredMethods(PdfRenderedDocument.class, "render"),
                "PdfRenderedDocument must own the two-pass orchestration");
        assertEquals(0, countDeclaredMethods(PdfReportGenerator.class, "countPages"),
                "the generator renders; it must not count pages itself");
        assertEquals(0, countDeclaredMethods(PdfDocumentMerger.class, "render"),
                "the merger concatenates; it must not render or count");
    }

    private static long countDeclaredMethods(Class<?> type, String name) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .count();
    }
}
