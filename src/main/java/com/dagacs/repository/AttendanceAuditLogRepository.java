package com.dagacs.repository;

import com.dagacs.entity.AttendanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceAuditLogRepository extends JpaRepository<AttendanceAuditLog, Long> {

    List<AttendanceAuditLog> findByStudentId(Long studentId);

    List<AttendanceAuditLog> findByAttendanceId(Long attendanceId);

    List<AttendanceAuditLog> findAllByOrderByUpdatedAtDesc();
}
