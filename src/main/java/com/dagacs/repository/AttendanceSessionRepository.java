package com.dagacs.repository;

import com.dagacs.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByTeacherEntityIdOrderByDateDesc(Long teacherId);

    List<AttendanceSession> findBySectionEntityIdOrderByDateDesc(Long sectionId);

    boolean existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
            Long subjectId, Long sectionId, String date, String lecturePeriod);
}
