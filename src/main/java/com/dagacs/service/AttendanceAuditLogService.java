package com.dagacs.service;

import com.dagacs.dto.AttendanceAuditLogDTO;
import com.dagacs.entity.AttendanceAuditLog;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.Student;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceAuditLogRepository;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceAuditLogService {

    private final AttendanceAuditLogRepository attendanceAuditLogRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    public AttendanceAuditLogService(AttendanceAuditLogRepository attendanceAuditLogRepository,
                                     StudentRepository studentRepository,
                                     AttendanceRecordRepository attendanceRecordRepository) {
        this.attendanceAuditLogRepository = attendanceAuditLogRepository;
        this.studentRepository = studentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public AttendanceAuditLogDTO saveAuditLog(AttendanceAuditLogDTO dto) {
        if (dto.getAttendanceId() == null) {
            throw new AuthException("Attendance record ID is required", 400);
        }
        if (dto.getStudentId() == null) {
            throw new AuthException("Student ID is required", 400);
        }
        if (dto.getRollNo() == null || dto.getRollNo().trim().isEmpty()) {
            throw new AuthException("Roll number snapshot is required", 400);
        }
        if (dto.getStudentName() == null || dto.getStudentName().trim().isEmpty()) {
            throw new AuthException("Student name snapshot is required", 400);
        }
        if (dto.getSubjectName() == null || dto.getSubjectName().trim().isEmpty()) {
            throw new AuthException("Subject name snapshot is required", 400);
        }
        if (dto.getSectionName() == null || dto.getSectionName().trim().isEmpty()) {
            throw new AuthException("Section name snapshot is required", 400);
        }
        if (dto.getSubject() == null || dto.getSubject().trim().isEmpty()) {
            throw new AuthException("Subject is required", 400);
        }
        if (dto.getSection() == null || dto.getSection().trim().isEmpty()) {
            throw new AuthException("Section is required", 400);
        }
        if (dto.getDate() == null || dto.getDate().trim().isEmpty()) {
            throw new AuthException("Date is required", 400);
        }
        if (dto.getPreviousStatus() == null || dto.getPreviousStatus().trim().isEmpty()) {
            throw new AuthException("Previous status is required", 400);
        }
        if (dto.getNewStatus() == null || dto.getNewStatus().trim().isEmpty()) {
            throw new AuthException("New status is required", 400);
        }
        if (dto.getUpdatedBy() == null || dto.getUpdatedBy().trim().isEmpty()) {
            throw new AuthException("Updated by is required", 400);
        }

        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new AuthException("Student not found with ID: " + dto.getStudentId(), 404));
        AttendanceRecord attendanceRecord = attendanceRecordRepository.findById(dto.getAttendanceId())
                .orElseThrow(() -> new AuthException("Attendance record not found with ID: " + dto.getAttendanceId(), 404));

        LocalDateTime now = LocalDateTime.now();
        AttendanceAuditLog auditLog = AttendanceAuditLog.builder()
                .attendance(attendanceRecord)
                .student(student)
                .rollNo(dto.getRollNo())
                .studentName(dto.getStudentName())
                .subject(dto.getSubject())
                .subjectName(dto.getSubjectName())
                .section(dto.getSection())
                .sectionName(dto.getSectionName())
                .date(dto.getDate())
                .previousStatus(dto.getPreviousStatus())
                .newStatus(dto.getNewStatus())
                .updatedBy(dto.getUpdatedBy())
                .updatedAt(now)
                .reason(dto.getReason())
                .build();
        auditLog = attendanceAuditLogRepository.save(auditLog);
        return convertToDTO(auditLog);
    }

    /**
     * Appends an immutable audit entry recording an attendance status change.
     * All snapshot values are derived from the existing {@code AttendanceRecord}
     * and its associated entities — the client never supplies snapshots or the
     * previous status for this pathway.
     *
     * @param record        the attendance record that changed
     * @param previousStatus the status before the change (from the database)
     * @param newStatus     the new status applied
     * @param updatedBy     the acting teacher's identity
     * @param reason        optional correction reason
     */
    @Transactional
    public AttendanceAuditLogDTO recordChange(AttendanceRecord record,
                                              String previousStatus,
                                              String newStatus,
                                              String updatedBy,
                                              String reason) {
        Student student = record.getStudent();
        LocalDateTime now = LocalDateTime.now();
        AttendanceAuditLog auditLog = AttendanceAuditLog.builder()
                .attendance(record)
                .student(student)
                .rollNo(student.getRollNumber())
                .studentName(student.getName())
                .subject(record.getSession().getSubjectEntity().getCode())
                .subjectName(record.getSession().getSubjectEntity().getName())
                .section(record.getSession().getSectionEntity().getName())
                .sectionName(record.getSession().getSectionEntity().getName())
                .date(record.getSession().getDate())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .updatedBy(updatedBy)
                .updatedAt(now)
                .reason(reason)
                .build();
        auditLog = attendanceAuditLogRepository.save(auditLog);
        return convertToDTO(auditLog);
    }

    @Transactional(readOnly = true)
    public AttendanceAuditLogDTO getAuditLogById(Long id) {
        AttendanceAuditLog auditLog = attendanceAuditLogRepository.findById(id)
                .orElseThrow(() -> new AuthException("Audit log not found with ID: " + id, 404));
        return convertToDTO(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AttendanceAuditLogDTO> getAuditLogsByStudent(Long studentId) {
        return attendanceAuditLogRepository.findByStudentId(studentId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceAuditLogDTO> getAuditLogsByAttendanceId(Long attendanceId) {
        return attendanceAuditLogRepository.findByAttendanceId(attendanceId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private AttendanceAuditLogDTO convertToDTO(AttendanceAuditLog auditLog) {
        return AttendanceAuditLogDTO.builder()
                .id(auditLog.getId())
                .attendanceId(auditLog.getAttendance().getId())
                .studentId(auditLog.getStudent().getId())
                .rollNo(auditLog.getRollNo())
                .studentName(auditLog.getStudentName())
                .subject(auditLog.getSubject())
                .subjectName(auditLog.getSubjectName())
                .section(auditLog.getSection())
                .sectionName(auditLog.getSectionName())
                .date(auditLog.getDate())
                .previousStatus(auditLog.getPreviousStatus())
                .newStatus(auditLog.getNewStatus())
                .updatedBy(auditLog.getUpdatedBy())
                .updatedAt(auditLog.getUpdatedAt())
                .reason(auditLog.getReason())
                .build();
    }
}
