package com.dagacs.repository;

import com.dagacs.entity.Semester;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, Long> {
    boolean existsBySubjectAndSemester(Subject subject, Semester semester);
    boolean existsBySemesterId(Long semesterId);
    List<SubjectOffering> findAllByOrderByIdAsc();
}