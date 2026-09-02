package com.dagacs.repository;

import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    Optional<Section> findBySectionCode(String sectionCode);
    Optional<Section> findByNameAndBatch(String name, Batch batch);
    List<Section> findByBatch(Batch batch);
    List<Section> findAllByOrderByName();
    boolean existsBySectionCode(String sectionCode);
    boolean existsByNameAndBatch(String name, Batch batch);
}