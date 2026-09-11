package com.dagacs.repository;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    Optional<Batch> findByBatchCode(String batchCode);
    List<Batch> findByAcademicSession(AcademicSession academicSession);
    List<Batch> findAllByOrderByName();
    boolean existsByBatchCode(String batchCode);
    boolean existsByAcademicSessionAndName(AcademicSession academicSession, String name);
}