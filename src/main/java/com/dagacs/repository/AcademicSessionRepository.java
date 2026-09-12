package com.dagacs.repository;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {
    Optional<AcademicSession> findByName(String name);
    Optional<AcademicSession> findByCode(String code);
    List<AcademicSession> findByProgram(Program program);
    List<AcademicSession> findAllByOrderByName();
    boolean existsByName(String name);
    boolean existsByCode(String code);
    boolean existsByNameAndProgram(String name, Program program);
    boolean existsByProgramId(Long programId);
}