package com.dagacs.repository;

import com.dagacs.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByTeacherEntityIdOrderByDateDesc(Long teacherId);

    List<AttendanceSession> findBySectionEntityIdOrderByDateDesc(Long sectionId);

    boolean existsBySubjectEntityId(Long subjectId);

    boolean existsBySectionEntityId(Long sectionId);

    boolean existsBySectionEntityBatchId(Long batchId);

    boolean existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
            Long subjectId, Long sectionId, String date, String lecturePeriod);

    /**
     * M9.15: teacher-scoped historical-attendance guard. Answers "did this
     * teacher create an attendance session in this subject + section context?"
     * Scoping by teacher keeps co-teacher assignments independent — one
     * teacher's history never blocks another teacher's assignment lifecycle.
     */
    boolean existsByTeacherEntityIdAndSubjectEntityIdAndSectionEntityId(
            Long teacherId, Long subjectId, Long sectionId);
}
