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

    Optional<AttendanceRecord> findByStudentIdAndSubjectIdAndSectionIdAndDate(
            Long studentId, Long subjectId, Long sectionId, String date);

    boolean existsByStudentIdAndSubjectIdAndSectionIdAndDate(
            Long studentId, Long subjectId, Long sectionId, String date);

    List<AttendanceRecord> findBySessionId(Long sessionId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionId(Long sessionId);
}
