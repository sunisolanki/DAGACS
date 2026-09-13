package com.dagacs.repository;

import com.dagacs.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * M5.2-specific repository over the shared {@code students} master-data table.
 * <p>
 * Deliberately separate from the frozen {@link StudentRepository} so the M5.2
 * admin student-management feature is purely additive. Both repositories manage
 * the same {@link Student} entity/table; this one adds the lookups required by
 * the Admin master-management API without modifying any frozen file.
 * </p>
 */
@Repository
public interface StudentManagementRepository extends JpaRepository<Student, Long> {

    boolean existsByRollNumber(String rollNumber);

    boolean existsByEmail(String email);

    Optional<Student> findByRollNumber(String rollNumber);

    List<Student> findAllByOrderByNameAsc();
}