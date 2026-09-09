package com.dagacs.repository;

import com.dagacs.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByCode(String code);
    Optional<Subject> findByName(String name);
    List<Subject> findAllByOrderByName();
    boolean existsByCode(String code);
    boolean existsByName(String name);
    boolean existsByCodeAndIdNot(String code, Long id);
}