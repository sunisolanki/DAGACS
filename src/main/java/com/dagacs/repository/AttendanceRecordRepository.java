package com.dagacs.repository;

import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByStudentId(Long studentId);

    List<AttendanceRecord> findBySubjectId(Long subjectId);

    List<AttendanceRecord> findBySectionId(Long sectionId);

    boolean existsBySubjectId(Long subjectId);

    boolean existsBySectionId(Long sectionId);

    Optional<AttendanceRecord> findByStudentIdAndSubjectIdAndSectionIdAndDate(
            Long studentId, Long subjectId, Long sectionId, String date);

    boolean existsByStudentIdAndSubjectIdAndSectionIdAndDate(
            Long studentId, Long subjectId, Long sectionId, String date);

    boolean existsByStudentIdAndSubjectIdAndBatchIdAndDate(
            Long studentId, Long subjectId, Long batchId, String date);

    List<AttendanceRecord> findBySessionId(Long sessionId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionId(Long sessionId);

    boolean existsByBatchId(Long batchId);

    /**
     * M9.15: teacher-scoped historical-attendance guard covering the "marked
     * by" dimension. Answers "did this teacher mark an attendance record in
     * this subject + section context?" Scoped by {markedBy, subject, section}
     * so co-teacher assignments stay independent.
     */
    boolean existsByMarkedByIdAndSubjectIdAndSectionId(
            Long teacherId, Long subjectId, Long sectionId);

    /**
     * Phase-2 batch-mode M9.15 guard covering the "marked by" dimension for
     * batch-level sessions. Scoped by {markedBy, subject, batch} so co-teacher
     * batch assignments stay independent.
     */
    boolean existsByMarkedByIdAndSubjectIdAndBatchId(
            Long teacherId, Long subjectId, Long batchId);
}
