package com.dagacs.service;

import com.dagacs.dto.AttendanceAuditLogDTO;
import com.dagacs.entity.AttendanceAuditLog;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.Student;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceAuditLogRepository;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceAuditLogServiceTest {

    @Mock
    private AttendanceAuditLogRepository attendanceAuditLogRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks
    private AttendanceAuditLogService attendanceAuditLogService;

    private Student student;
    private AttendanceRecord attendanceRecord;
    private AttendanceAuditLog auditLog;

    @BeforeEach
    void setUp() {
        student = Student.builder().id(1L).rollNumber("2201CE001").name("Rahul Kumar").build();
        attendanceRecord = AttendanceRecord.builder().id(1L).student(student).build();
        auditLog = AttendanceAuditLog.builder()
                .id(1L)
                .attendance(attendanceRecord)
                .student(student)
                .rollNo("2201CE001")
                .studentName("Rahul Kumar")
                .subject("DBMS")
                .subjectName("Database Management Systems")
                .section("CS-A")
                .sectionName("CS-A")
                .date("2026-09-04")
                .previousStatus("ABSENT")
                .newStatus("PRESENT")
                .updatedBy("teacher@dagacs.local")
                .updatedAt(LocalDateTime.now())
                .reason("Doctor certificate")
                .build();
    }

    private AttendanceAuditLogDTO validDTO() {
        return AttendanceAuditLogDTO.builder()
                .attendanceId(1L)
                .studentId(1L)
                .rollNo("2201CE001")
                .studentName("Rahul Kumar")
                .subject("DBMS")
                .subjectName("Database Management Systems")
                .section("CS-A")
                .sectionName("CS-A")
                .date("2026-09-04")
                .previousStatus("ABSENT")
                .newStatus("PRESENT")
                .updatedBy("teacher@dagacs.local")
                .reason("Doctor certificate")
                .build();
    }

    @Test
    void saveAuditLog_valid_returnsDTO() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findById(1L)).thenReturn(Optional.of(attendanceRecord));
        when(attendanceAuditLogRepository.save(any(AttendanceAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceAuditLogDTO result = attendanceAuditLogService.saveAuditLog(validDTO());
        assertNotNull(result);
        assertEquals("ABSENT", result.getPreviousStatus());
        assertEquals("PRESENT", result.getNewStatus());
    }

    @Test
    void saveAuditLog_invalidStudent_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceAuditLogDTO dto = validDTO();
        dto.setStudentId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceAuditLogService.saveAuditLog(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveAuditLog_invalidAttendanceRecord_returns404() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceAuditLogDTO dto = validDTO();
        dto.setAttendanceId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceAuditLogService.saveAuditLog(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getAuditLogById_valid_returnsDTO() {
        when(attendanceAuditLogRepository.findById(1L)).thenReturn(Optional.of(auditLog));

        AttendanceAuditLogDTO result = attendanceAuditLogService.getAuditLogById(1L);
        assertEquals("2201CE001", result.getRollNo());
        assertEquals("Rahul Kumar", result.getStudentName());
    }

    @Test
    void getAuditLogById_missing_returns404() {
        when(attendanceAuditLogRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceAuditLogService.getAuditLogById(99L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getAuditLogsByStudent_returnsList() {
        when(attendanceAuditLogRepository.findByStudentId(1L)).thenReturn(List.of(auditLog));

        List<AttendanceAuditLogDTO> result = attendanceAuditLogService.getAuditLogsByStudent(1L);
        assertEquals(1, result.size());
        assertEquals("2201CE001", result.get(0).getRollNo());
    }

    @Test
    void getAuditLogsByAttendanceId_returnsList() {
        when(attendanceAuditLogRepository.findByAttendanceId(1L)).thenReturn(List.of(auditLog));

        List<AttendanceAuditLogDTO> result = attendanceAuditLogService.getAuditLogsByAttendanceId(1L);
        assertEquals(1, result.size());
    }
}
