package com.dagacs.controller;

import com.dagacs.dto.HodHierarchyOptionDTO;
import com.dagacs.dto.HodHierarchyRootDTO;
import com.dagacs.dto.HodHierarchySubjectDTO;
import com.dagacs.service.HodHierarchyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HOD-scoped read-only academic hierarchy.
 *
 * <p>The department scope is derived exclusively from the authenticated HOD
 * identity (see {@code AuthenticatedHodResolver} /
 * {@link HodHierarchyService}); <b>no endpoint here accepts a
 * {@code departmentId}</b>, and a supplied hierarchy id that does not belong to
 * the HOD's department is rejected with 403 before it can influence a query.</p>
 *
 * <p>The hierarchy is exposed as a cascade so each level is filtered
 * <em>server-side</em> by its parent and only the dependent level is refetched
 * when a parent changes:</p>
 * <pre>
 *   GET /api/hod/hierarchy
 *   GET /api/hod/hierarchy/semesters?academicSessionId=
 *   GET /api/hod/hierarchy/sections?academicSessionId=&amp;programId=&amp;semesterId=
 *   GET /api/hod/hierarchy/subjects?semesterId=&amp;sectionId=
 * </pre>
 */
@RestController
@RequestMapping("/api/hod/hierarchy")
@PreAuthorize("hasRole('HOD')")
public class HodHierarchyController {

    private final HodHierarchyService hierarchyService;

    public HodHierarchyController(HodHierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    /** Department identity plus the two cascade roots: Programs and Academic Sessions. */
    @GetMapping
    public ResponseEntity<HodHierarchyRootDTO> getRoot() {
        return ResponseEntity.ok(hierarchyService.getRoot());
    }

    @GetMapping("/semesters")
    public ResponseEntity<List<HodHierarchyOptionDTO>> getSemesters(
            @RequestParam Long academicSessionId) {
        return ResponseEntity.ok(hierarchyService.getSemesters(academicSessionId));
    }

    @GetMapping("/sections")
    public ResponseEntity<List<HodHierarchyOptionDTO>> getSections(
            @RequestParam Long academicSessionId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long semesterId) {
        return ResponseEntity.ok(
                hierarchyService.getSections(academicSessionId, programId, semesterId));
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<HodHierarchySubjectDTO>> getSubjects(
            @RequestParam Long semesterId,
            @RequestParam(required = false) Long sectionId) {
        return ResponseEntity.ok(hierarchyService.getSubjects(semesterId, sectionId));
    }
}
