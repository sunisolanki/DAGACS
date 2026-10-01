package com.dagacs.service;

import com.dagacs.dto.HodHierarchyOptionDTO;
import com.dagacs.dto.HodHierarchyRootDTO;
import com.dagacs.dto.HodHierarchySubjectDTO;
import com.dagacs.dto.HodLowAttendanceDTO;
import com.dagacs.dto.HodSectionAttendanceDTO;
import com.dagacs.dto.HodStudentAttendanceDTO;
import com.dagacs.dto.HodSubjectAttendanceDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Program;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthErrorCode;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.HodHierarchyRepository;
import com.dagacs.security.AuthenticatedHodResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HOD-scoped academic hierarchy and academic-context-scoped HOD data views.
 *
 * <p><b>Authorization model.</b> The department scope is derived exclusively
 * from the authenticated identity via {@link AuthenticatedHodResolver} — the
 * same mechanism {@code HodAnalyticsService} and {@code HodReportService}
 * already use. No method accepts a department id, and no request parameter is
 * ever treated as an authority. Every hierarchy identifier supplied by a client
 * is proven to belong to that department before it is used, and every scoped
 * query additionally carries {@code p.department.id = :deptId} in its WHERE
 * clause, so a bypass would still return no data.</p>
 *
 * <p><b>Context filters are optional.</b> When no academic context is selected
 * the caller gets the department-wide behaviour; when one is selected the
 * scoped query runs. Attendance arithmetic is never recomputed here: the same
 * {@code present / totalRecorded * 100} helper with a null result for
 * {@code totalRecorded <= 0} is reused, and the low-attendance threshold stays
 * the backend's fixed 75% rule expressed in the query itself.</p>
 */
@Service
public class HodHierarchyService {

    private final AuthenticatedHodResolver hodResolver;
    private final HodHierarchyRepository hierarchyRepository;

    public HodHierarchyService(AuthenticatedHodResolver hodResolver,
                               HodHierarchyRepository hierarchyRepository) {
        this.hodResolver = hodResolver;
        this.hierarchyRepository = hierarchyRepository;
    }

    // ── Hierarchy ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HodHierarchyRootDTO getRoot() {
        Teacher hod = hodResolver.resolve();
        Long deptId = hod.getDepartment().getId();

        List<HodHierarchyOptionDTO> programs = hierarchyRepository
                .findProgramOptions(deptId).stream()
                .map(row -> HodHierarchyOptionDTO.builder()
                        .id(row.getId())
                        .name(row.getName())
                        .code(row.getCode())
                        .build())
                .toList();

        List<HodHierarchyOptionDTO> sessions = hierarchyRepository
                .findAcademicSessionOptions(deptId).stream()
                .map(row -> HodHierarchyOptionDTO.builder()
                        .id(row.getId())
                        .name(row.getName())
                        .code(row.getCode())
                        .programId(row.getProgramId())
                        .programName(row.getProgramName())
                        .build())
                .toList();

        return HodHierarchyRootDTO.builder()
                .departmentId(deptId)
                .departmentName(hod.getDepartment().getName())
                .departmentCode(hod.getDepartment().getCode())
                .programs(programs)
                .academicSessions(sessions)
                .build();
    }

    @Transactional(readOnly = true)
    public List<HodHierarchyOptionDTO> getSemesters(Long academicSessionId) {
        Long deptId = departmentId();
        assertAcademicSessionInDepartment(academicSessionId, deptId);
        return hierarchyRepository.findSemesterOptions(academicSessionId).stream()
                .map(HodHierarchyService::toOption)
                .toList();
    }

    /**
     * Sections for an academic session, optionally restricted to one semester.
     *
     * <p>When both a program and a session are supplied they must agree: a
     * session belongs to exactly one program, so a mismatch is an invalid
     * selection rather than an authorization problem and is rejected as 400.</p>
     */
    @Transactional(readOnly = true)
    public List<HodHierarchyOptionDTO> getSections(Long academicSessionId,
                                                   Long programId,
                                                   Long semesterId) {
        Long deptId = departmentId();
        assertAcademicSessionInDepartment(academicSessionId, deptId);
        if (programId != null) {
            assertProgramInDepartment(programId, deptId);
            assertProgramMatchesSession(programId, academicSessionId);
        }
        if (semesterId != null) {
            assertSemesterInDepartment(semesterId, deptId);
            assertSemesterBelongsToSession(semesterId, academicSessionId);
        }
        return hierarchyRepository.findSectionOptions(academicSessionId, semesterId)
                .stream()
                .map(HodHierarchyService::toOption)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HodHierarchySubjectDTO> getSubjects(Long semesterId, Long sectionId) {
        Long deptId = departmentId();
        assertSemesterInDepartment(semesterId, deptId);
        if (sectionId != null) {
            assertSectionInDepartment(sectionId, deptId);
        }

        Map<Long, List<String>> faculty = sectionId == null
                ? Map.of()
                : facultyBySubject(sectionId, semesterId);

        List<HodHierarchySubjectDTO> subjects = new ArrayList<>();
        for (HodHierarchyRepository.SubjectProjection row
                : hierarchyRepository.findSubjectOptions(semesterId)) {
            subjects.add(HodHierarchySubjectDTO.builder()
                    .id(row.getId())
                    .code(row.getCode())
                    .name(row.getName())
                    .semesterId(row.getSemesterId())
                    .semesterName(row.getSemesterName())
                    .programId(row.getProgramId())
                    .programName(row.getProgramName())
                    .facultyNames(faculty.getOrDefault(row.getId(), List.of()))
                    .build());
        }
        return subjects;
    }

    // ── Scoped data views ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HodSectionAttendanceDTO> getSectionAttendance(HodAcademicSelection selection,
                                                              String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, this);

        List<HodSectionAttendanceDTO> rows = new ArrayList<>();
        for (HodHierarchyRepository.SectionContextProjection row
                : hierarchyRepository.aggregateSectionAttendanceForContext(
                deptId, selection.sessionId(), selection.programId(), selection.semesterId(),
                selection.sectionId(), startDate, endDate)) {
            rows.add(HodSectionAttendanceDTO.builder()
                    .sectionName(row.getSectionName())
                    .sectionCode(row.getSectionCode())
                    .studentCount(row.getStudentCount())
                    .presentCount(row.getPresentCount())
                    .totalRecordedCount(row.getTotalRecorded())
                    .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                    .build());
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<HodStudentAttendanceDTO> getStudentAttendance(HodAcademicSelection selection,
                                                              String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, this);

        List<HodStudentAttendanceDTO> rows = new ArrayList<>();
        for (HodHierarchyRepository.StudentContextProjection row
                : hierarchyRepository.aggregateStudentAttendanceForContext(
                deptId, selection.sessionId(), selection.programId(), selection.semesterId(),
                selection.sectionId(), startDate, endDate)) {
            rows.add(HodStudentAttendanceDTO.builder()
                    .studentId(row.getStudentId())
                    .rollNumber(row.getRollNumber())
                    .enrollmentNumber(row.getEnrollmentNumber())
                    .studentName(row.getStudentName())
                    .sectionName(row.getSectionName())
                    .semesterName(row.getSemesterName())
                    .academicSessionName(row.getAcademicSessionName())
                    .programName(row.getProgramName())
                    .presentCount(row.getPresentCount())
                    .totalRecordedCount(row.getTotalRecorded())
                    .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                    .build());
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<HodLowAttendanceDTO> getLowAttendance(HodAcademicSelection selection,
                                                       String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, this);

        List<HodLowAttendanceDTO> rows = new ArrayList<>();
        for (HodHierarchyRepository.StudentContextProjection row
                : hierarchyRepository.aggregateLowAttendanceForContext(
                deptId, selection.sessionId(), selection.programId(), selection.semesterId(),
                selection.sectionId(), startDate, endDate)) {
            rows.add(HodLowAttendanceDTO.builder()
                    .rollNumber(row.getRollNumber())
                    .enrollmentNumber(row.getEnrollmentNumber())
                    .studentName(row.getStudentName())
                    .sectionName(row.getSectionName())
                    .semesterName(row.getSemesterName())
                    .academicSessionName(row.getAcademicSessionName())
                    .programName(row.getProgramName())
                    .presentCount(row.getPresentCount())
                    .totalRecordedCount(row.getTotalRecorded())
                    .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                    .build());
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<HodSubjectAttendanceDTO> getSubjectAttendance(HodAcademicSelection selection,
                                                               String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, this);

        List<HodSubjectAttendanceDTO> rows = new ArrayList<>();
        for (HodHierarchyRepository.SubjectContextProjection row
                : hierarchyRepository.aggregateSubjectAttendanceForContext(
                deptId, selection.sessionId(), selection.programId(), selection.semesterId(),
                selection.sectionId(), startDate, endDate)) {
            rows.add(HodSubjectAttendanceDTO.builder()
                    .subjectId(row.getSubjectId())
                    .subjectCode(row.getSubjectCode())
                    .subjectName(row.getSubjectName())
                    .programName(row.getProgramName())
                    .semesterName(row.getSemesterName())
                    .presentCount(row.getPresentCount())
                    .totalRecordedCount(row.getTotalRecorded())
                    .percentage(computePercentage(row.getPresentCount(), row.getTotalRecorded()))
                    .build());
        }
        return rows;
    }

    // ── Ownership assertions (single place; no controller duplicates these) ─

    private Long departmentId() {
        return hodResolver.resolve().getDepartment().getId();
    }

    void assertProgramInDepartment(Long programId, Long deptId) {
        if (hierarchyRepository.countProgramInDepartment(programId, deptId) == 0) {
            throw scopeViolation("program");
        }
    }

    void assertAcademicSessionInDepartment(Long sessionId, Long deptId) {
        if (hierarchyRepository.countAcademicSessionInDepartment(sessionId, deptId) == 0) {
            throw scopeViolation("academic session");
        }
    }

    void assertSemesterInDepartment(Long semesterId, Long deptId) {
        if (hierarchyRepository.countSemesterInDepartment(semesterId, deptId) == 0) {
            throw scopeViolation("semester");
        }
    }

    void assertSectionInDepartment(Long sectionId, Long deptId) {
        if (hierarchyRepository.countSectionInDepartment(sectionId, deptId) == 0) {
            throw scopeViolation("section");
        }
    }

    void assertProgramMatchesSession(Long programId, Long sessionId) {
        for (HodHierarchyRepository.OptionProjection row
                : hierarchyRepository.findAcademicSessionOptions(departmentId())) {
            if (row.getId().equals(sessionId) && !row.getProgramId().equals(programId)) {
                throw new AuthException(
                        "The selected program does not belong to the selected academic session",
                        400, AuthErrorCode.HOD_SCOPE_VIOLATION);
            }
        }
    }

    void assertSemesterBelongsToSession(Long semesterId, Long sessionId) {
        // A semester's session is single-valued, so proving the session is in
        // scope and that the semester is in scope is enough to reject a
        // cross-session pairing without a second round trip.
        List<HodHierarchyOptionDTO> semesters = hierarchyRepository
                .findSemesterOptions(sessionId).stream()
                .map(HodHierarchyService::toOption)
                .toList();
        boolean belongs = semesters.stream().anyMatch(o -> o.getId().equals(semesterId));
        if (!belongs) {
            throw new AuthException(
                    "The selected semester does not belong to the selected academic session",
                    400, AuthErrorCode.HOD_SCOPE_VIOLATION);
        }
    }

    /**
     * Phase 3 (additive): a student must belong to the HOD's department
     * <b>and</b> to the exact academic context of the request.
     *
     * <p>This is what stops a caller from swapping a student id in the URL and
     * reading another class's, another semester's or another department's
     * attendance. Because the level predicates are conjunctive, a student that
     * legitimately belongs to the same department but a different section
     * cannot satisfy the count either - a 403 with no data, not an empty report.</p>
     */
    void assertStudentInContext(Long studentId, Long deptId, HodAcademicSelection selection) {
        if (hierarchyRepository.countStudentInContext(
                studentId, deptId, selection.sessionId(), selection.programId(),
                selection.semesterId(), selection.sectionId()) == 0) {
            throw new AuthException(
                    "The selected student does not belong to the selected academic context",
                    403, AuthErrorCode.HOD_SCOPE_VIOLATION);
        }
    }

    /**
     * Phase 3 (additive): a subject must belong to the HOD's department
     * <b>and</b> to the academic context - offered in the selected semester, or
     * actually assigned to teach the selected section.
     *
     * <p>Mirrors the dynamic-column query exactly, so a subject that can be
     * opened on its own is a subject that could have been a matrix column. A
     * subject of another department, program, semester or session is rejected
     * with 403 before it can influence a query.</p>
     */
    void assertSubjectInContext(Long subjectId, Long deptId, HodAcademicSelection selection) {
        if (hierarchyRepository.countSubjectInContext(
                subjectId, deptId, selection.sessionId(), selection.programId(),
                selection.semesterId(), selection.sectionId()) == 0) {
            throw new AuthException(
                    "The selected subject does not belong to the selected academic context",
                    403, AuthErrorCode.HOD_SCOPE_VIOLATION);
        }
    }

    private static AuthException scopeViolation(String level) {
        return new AuthException(
                "The selected " + level + " does not belong to your department",
                403, AuthErrorCode.HOD_SCOPE_VIOLATION);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Map<Long, List<String>> facultyBySubject(Long sectionId, Long semesterId) {
        Map<Long, Set<String>> grouped = new LinkedHashMap<>();
        for (HodHierarchyRepository.FacultyProjection row
                : hierarchyRepository.findFacultyForSectionAndSemester(sectionId, semesterId)) {
            grouped.computeIfAbsent(row.getSubjectId(), key -> new LinkedHashSet<>())
                    .add(row.getTeacherName());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((subjectId, names) -> result.put(subjectId, List.copyOf(names)));
        return result;
    }

    private static HodHierarchyOptionDTO toOption(HodHierarchyRepository.OptionProjection row) {
        return HodHierarchyOptionDTO.builder()
                .id(row.getId())
                .name(row.getName())
                .code(row.getCode())
                .programId(row.getProgramId())
                .programName(row.getProgramName())
                .batchId(row.getBatchId())
                .batchName(row.getBatchName())
                .studentCount(row.getStudentCount())
                .build();
    }

    /** Identical semantics to the frozen analytics percentage helper. */
    private static Double computePercentage(Long presentCount, Long totalRecorded) {
        if (totalRecorded == null || totalRecorded <= 0) {
            return null;
        }
        return (double) presentCount / totalRecorded * 100.0;
    }
}
