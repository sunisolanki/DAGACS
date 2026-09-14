package com.dagacs.repository;

import com.dagacs.entity.TeacherSubjectSectionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherSubjectSectionAssignmentRepository extends JpaRepository<TeacherSubjectSectionAssignment, Long> {

    /**
     * M9.3 canonical authorization check: is the teacher assigned to teach a
     * subject (resolved through their SubjectOffering) to the given section?
     * Resolves the subject identity via {@code subjectOffering.subject}. Rows
     * whose legacy {@code subject_id} did not map to a subject_offering_id are
     * never matched, so they can never authorize access.
     */
    boolean existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(Long teacherId, Long sectionId, Long subjectId);

    /**
     * Phase-2 batch-mode authorization check: is the teacher assigned to teach a
     * subject (resolved through their SubjectOffering) to the given batch
     * (zero-section batch)? Resolves the subject identity via
     * {@code subjectOffering.subject}. Rows whose legacy {@code subject_id} did
     * not map to a subject_offering_id are never matched, so they can never
     * authorize access.
     */
    boolean existsByTeacherIdAndBatchIdAndSubjectOfferingSubjectId(Long teacherId, Long batchId, Long subjectId);

    boolean existsByTeacherIdAndSubjectOfferingIdAndSectionId(Long teacherId, Long subjectOfferingId, Long sectionId);

    boolean existsByTeacherIdAndSubjectOfferingIdAndBatchId(Long teacherId, Long subjectOfferingId, Long batchId);

    boolean existsBySectionId(Long sectionId);

    boolean existsByBatchId(Long batchId);

    boolean existsBySectionBatchId(Long batchId);

    Optional<TeacherSubjectSectionAssignment> findByTeacherIdAndSubjectOfferingIdAndSectionId(
            Long teacherId, Long subjectOfferingId, Long sectionId);

    Optional<TeacherSubjectSectionAssignment> findByTeacherIdAndSubjectOfferingIdAndBatchId(
            Long teacherId, Long subjectOfferingId, Long batchId);

    boolean existsBySubjectOfferingId(Long subjectOfferingId);

    List<TeacherSubjectSectionAssignment> findByTeacherIdOrderByIdAsc(Long teacherId);

    List<TeacherSubjectSectionAssignment> findAllByOrderByIdAsc();
}