package com.dagacs.repository;

import com.dagacs.entity.TeacherSubjectSectionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherSubjectSectionAssignmentRepository extends JpaRepository<TeacherSubjectSectionAssignment, Long> {

    boolean existsByTeacherIdAndSubjectIdAndSectionId(Long teacherId, Long subjectId, Long sectionId);

    List<TeacherSubjectSectionAssignment> findByTeacherId(Long teacherId);
}
