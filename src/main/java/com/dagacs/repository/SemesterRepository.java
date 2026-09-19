package com.dagacs.repository;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByName(String name);
    Optional<Semester> findByCode(String code);
    List<Semester> findByAcademicSession(AcademicSession academicSession);
    List<Semester> findByAcademicSessionIn(Collection<AcademicSession> academicSessions);
    List<Semester> findAllByOrderByName();
    boolean existsByName(String name);
    boolean existsByCode(String code);
    boolean existsByNameAndAcademicSession(String name, AcademicSession academicSession);
}