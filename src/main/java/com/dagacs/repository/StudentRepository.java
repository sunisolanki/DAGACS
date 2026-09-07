package com.dagacs.repository;

import com.dagacs.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findBySectionIdOrderByNameAsc(Long sectionId);

    Optional<Student> findByEmail(String email);
}
