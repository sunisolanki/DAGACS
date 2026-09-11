package com.dagacs.repository;

import com.dagacs.entity.Teacher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /**
     * M9.3 admin master-data: existing teachers sorted by full name for the
     * teacher-assignment screens and the /api/admin/teachers listing.
     */
    List<Teacher> findAllByOrderByFullNameAsc();

    Optional<Teacher> findByEmail(String email);

    /**
     * Count of HODs bound to the given department. Used by the concurrency
     * invariant test to assert {@code <= 1} directly against the database.
     */
    long countByDepartmentIdAndIsHodTrue(Long departmentId);

    /**
     * Serialization point for operations on a single teacher (M6.1).
     * <p>
     * Serializes concurrent designations/clears/moves of the <em>same</em>
     * teacher so that, e.g., two simultaneous cross-department moves of one
     * teacher cannot both succeed and leave an ambiguous/inconsistent target.
     * Must be acquired BEFORE locking the target department row so that lock
     * order is deterministic (teacher row, then department row) and cannot
     * deadlock (each transaction locks only one department afterwards).
     * </p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Teacher t WHERE t.id = :id")
    Optional<Teacher> findByIdLocked(@Param("id") Long id);

    /**
     * Serialization point for the one-HOD-per-department invariant.
     * <p>
     * This is a pessimistic locking read that reads the LATEST committed state
     * (it never uses a repeatable-read snapshot). It must always be issued AFTER
     * the department row has been locked in the designation transaction so that a
     * concurrent designation blocks on the department lock and then sees the
     * first transaction's committed HOD here.
     * </p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Teacher t WHERE t.isHod = TRUE AND t.department.id = :departmentId")
    Optional<Teacher> findHodLocked(@Param("departmentId") Long departmentId);
}
