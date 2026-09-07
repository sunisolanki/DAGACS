package com.dagacs.service;

import com.dagacs.dto.HodDesignationRequestDTO;
import com.dagacs.dto.HodIdentityDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.TeacherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ADMIN-only designation, clearing, and cross-department move of an HOD
 * (M6.1).
 * <p>
 * <b>Concurrency strategy</b> (reviewed in the M6.1 scoping plan):
 * <ol>
 *   <li><b>Normal transactional enforcement:</b> all existence checks and
 *       validations happen inside one {@code @Transactional} method; a failure
 *       throws before any {@code save}.</li>
 *   <li><b>Concurrency protection:</b>
 *     <ol type="a">
 *       <li><em>Teacher row lock</em> (acquired first): concurrent operations on
 *           the <em>same</em> teacher are serialized via
 *           {@link TeacherRepository#findByIdLocked(Long)}. Without this, two
 *           simultaneous cross-department moves of one teacher could both succeed
 *           and leave an ambiguous/inconsistent target department.</li>
 *       <li><em>Target department row lock</em>: a
 *           {@link LockModeType#PESSIMISTIC_WRITE} lock on the department row,
 *           which always exists, serializes every designation for that department.
 *           The existing-HOD check then runs via
 *           {@link TeacherRepository#findHodLocked(Long)} - a locking read that sees
 *           the latest committed state, so a concurrent create cannot slip through
 *           a stale repeatable-read snapshot.</li>
 *     </ol>
 *   <li><b>Source department on a move is intentionally NOT locked:</b> moving a
 *       teacher out of its source department only ever <em>removes</em> that
 *       department's HOD; no operation can add a second HOD to the source without
 *       locking it, so the per-department cap cannot be violated there.</li>
 * </ol>
 * <p>Lock ordering is deterministic (teacher row, then a single target department
 * row), which avoids lock cycles and therefore deadlocks. No naive unique
 * constraint is used because many non-HOD teachers may share a department.</p>
 */
@Service
public class HodDesignationService {

    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final EntityManager entityManager;

    public HodDesignationService(TeacherRepository teacherRepository,
                                 DepartmentRepository departmentRepository,
                                 EntityManager entityManager) {
        this.teacherRepository = teacherRepository;
        this.departmentRepository = departmentRepository;
        this.entityManager = entityManager;
    }

    /**
     * Designates, clears, or (when the target department is free) moves an HOD.
     *
     * @return the resulting HOD identity
     */
    @Transactional
    public HodIdentityDTO setHod(Long teacherId, HodDesignationRequestDTO request) {
        if (request == null || request.getDesignated() == null) {
            throw new AuthException("designated must be true or false", 400);
        }

        // Lock the teacher row FIRST so concurrent designate/clear/move operations
        // on the same teacher serialize. This is acquired before any department row
        // lock, giving a deterministic lock order (teacher -> target department).
        Teacher teacher = teacherRepository.findByIdLocked(teacherId)
                .orElseThrow(() -> new AuthException("Teacher not found with ID: " + teacherId, 404));

        boolean designated = request.getDesignated();
        if (!designated) {
            return clear(teacher);
        }
        return designate(teacher, request.getDepartmentId());
    }

    private HodIdentityDTO designate(Teacher teacher, Long departmentId) {
        if (departmentId == null) {
            throw new AuthException("A department is required to designate an HOD", 400);
        }

        // Serialize all designations for this department on the always-present
        // department row. find() with a lock returns null if the department is
        // missing; using entityManager directly here avoids a snapshot read.
        Department department = entityManager.find(Department.class, departmentId,
                LockModeType.PESSIMISTIC_WRITE);
        if (department == null) {
            throw new AuthException("Department not found with ID: " + departmentId, 404);
        }

        // Read the latest committed HOD for this department (after the lock).
        teacherRepository.findHodLocked(departmentId).ifPresent(existingHod -> {
            if (!existingHod.getId().equals(teacher.getId())) {
                throw new AuthException(
                        "Department '" + department.getName() + "' already has an HOD designated; "
                                + "clear it first to avoid silent replacement", 409);
            }
            // Re-designating the same teacher as HOD of the same department is
            // idempotent; nothing else to change below.
        });

        // Atomic move: teacher is bound to exactly one department (single FK).
        // If the teacher was HOD of a different department, that department's HOD
        // slot is implicitly released because this teacher is the only HOD.
        teacher.setDepartment(department);
        teacher.setIsHod(true);
        teacher.setUpdatedAt(LocalDateTime.now());
        teacherRepository.save(teacher);
        return toIdentityDTO(teacher);
    }

    private HodIdentityDTO clear(Teacher teacher) {
        boolean wasHod = teacher.getIsHod() != null && teacher.getIsHod();
        if (wasHod) {
            // The teacher keeps its department association; only the HOD
            // designation is removed (see M6.1 scope decision 4D).
            teacher.setIsHod(false);
            teacher.setUpdatedAt(LocalDateTime.now());
            teacherRepository.save(teacher);
        }
        return toIdentityDTO(teacher);
    }

    private HodIdentityDTO toIdentityDTO(Teacher teacher) {
        HodIdentityDTO.HodIdentityDTOBuilder builder = HodIdentityDTO.builder()
                .email(teacher.getEmail())
                .teacherName(teacher.getFullName())
                .designation(teacher.getDesignation())
                .hod(teacher.getIsHod() != null && teacher.getIsHod());
        Department department = teacher.getDepartment();
        if (department != null) {
            builder.departmentName(department.getName())
                    .departmentCode(department.getCode());
        }
        return builder.build();
    }
}