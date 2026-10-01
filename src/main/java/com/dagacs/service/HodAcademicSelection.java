package com.dagacs.service;

/**
 * The academic context a HOD has selected, as supplied by the request.
 *
 * <p>Every field is optional: a fully empty selection means "department-wide",
 * which is the pre-existing behaviour and must keep working. Any field that
 * <em>is</em> supplied is an untrusted <em>selection</em>, never an authority —
 * {@link #assertOwned} proves each one belongs to the authenticated HOD's
 * department before it can influence a query.</p>
 *
 * <p>A selection is only rejected when two supplied levels <em>contradict</em>
 * each other (a program that is not the session's program, or a semester that
 * is not the session's semester). A single supplied level is accepted on its
 * own, because every level is single-valued in this schema — a semester
 * determines its session and program, and a section determines its batch and
 * therefore its program — so a lone level is unambiguous and already proven
 * in-department by the ownership assertions.</p>
 */
public record HodAcademicSelection(Long sessionId,
                                   Long programId,
                                   Long semesterId,
                                   Long sectionId) {

    /** True when the HOD selected at least one academic-context level. */
    public boolean isPresent() {
        return sessionId != null || programId != null || semesterId != null || sectionId != null;
    }

    /**
     * Verifies every supplied id belongs to {@code deptId}.
     *
     * <p>A department mismatch is an authorization failure (403) and yields no
     * data whatsoever. Two supplied levels that contradict each other are an
     * invalid selection (400).</p>
     */
    public void assertOwned(Long deptId, HodHierarchyService service) {
        if (programId != null) {
            service.assertProgramInDepartment(programId, deptId);
        }
        if (sessionId != null) {
            service.assertAcademicSessionInDepartment(sessionId, deptId);
        }
        if (semesterId != null) {
            service.assertSemesterInDepartment(semesterId, deptId);
        }
        if (sectionId != null) {
            service.assertSectionInDepartment(sectionId, deptId);
        }
        if (programId != null && sessionId != null) {
            service.assertProgramMatchesSession(programId, sessionId);
        }
        if (semesterId != null && sessionId != null) {
            service.assertSemesterBelongsToSession(semesterId, sessionId);
        }
    }
}
