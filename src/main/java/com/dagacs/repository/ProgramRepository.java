package com.dagacs.repository;

import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
    Optional<Program> findByName(String name);
    Optional<Program> findByCode(String code);
    List<Program> findByDepartment(Department department);
    List<Program> findAllByOrderByName();
    boolean existsByName(String name);
    boolean existsByCode(String code);
    boolean existsByNameAndDepartment(String name, Department department);
}