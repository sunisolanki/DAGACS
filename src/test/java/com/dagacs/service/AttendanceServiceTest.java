package com.dagacs.service;

import com.dagacs.dto.AttendanceMarkItemDTO;
import com.dagacs.dto.AttendanceMarkRequestDTO;
import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.dto.AttendanceUpdateRequestDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.dagacs.security.AuthenticatedTeacherResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Mock
    private AuthenticatedTeacherResolver teacherResolver;

    @Mock
    private AttendanceAuditLogService auditLogService;

    @InjectMocks
    private AttendanceService attendanceService;

    private Teacher teacher;
    private Subject subject;
    private Section section;
    private AttendanceSession session;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setUp() {
        teacher = Teacher.builder().id(1L).email("t@dagacs.local").fullName("T").build();
        subject = Subject.builder().id(1L).code("CS101").name("DBMS").build();
        section = Section.builder().id(1L).sectionCode("CSE-A").name("CSE-A").build();
        session = AttendanceSession.builder().id(10L)
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .lecturePeriod("1st").date("2026-09-04").status("CONDUCTED").build();
        student1 = Student.builder().id(1L).rollNumber("R1").name("S1").section(section).status("ACTIVE").build();
        student2 = Student.builder().id(2L).rollNumber("R2").name("S2").section(section).status("ACTIVE").build();
    }

    private AttendanceMarkRequestDTO validRequest() {
        return AttendanceMarkRequestDTO.builder()
                .sessionId(10L)
                .items(List.of(
                        new AttendanceMarkItemDTO(1L, "PRESENT"),
                        new AttendanceMarkItemDTO(2L, "ABSENT")))
                .build();
    }

    @Test
    void markAttendance_valid_savesAllRecords() {
        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(studentRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(student1, student2));
        when(attendanceRecordRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<AttendanceRecordDTO> result = attendanceService.markAttendance(validRequest());
        assertEquals(2, result.size());
        verify(attendanceRecordRepository, times(2)).save(any(AttendanceRecord.class));
    }

    @Test
    void markAttendance_duplicate_rollsBackAndThrows409() {
        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(studentRepository.findAllById(anyList())).thenAnswer(inv ->
                ((List<Long>) inv.getArgument(0)).stream()
                        .map(id -> id.equals(1L) ? student1 : student2)
                        .collect(Collectors.toList()));
        when(attendanceRecordRepository.existsBySessionIdAndStudentId(10L, 1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(validRequest()));
        assertEquals(409, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_wrongSectionStudent_returns400() {
        Student other = Student.builder().id(3L).rollNumber("R3").name("S3")
                .section(Section.builder().id(99L).name("CSE-B").build()).status("ACTIVE").build();
        AttendanceMarkRequestDTO request = AttendanceMarkRequestDTO.builder()
                .sessionId(10L)
                .items(List.of(new AttendanceMarkItemDTO(3L, "PRESENT")))
                .build();

        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(studentRepository.findAllById(List.of(3L))).thenReturn(List.of(other));

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(request));
        assertEquals(400, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_inactiveStudent_returns400() {
        Student inactive = Student.builder().id(9L).rollNumber("R9").name("S9")
                .section(section).status("INACTIVE").build();
        AttendanceMarkRequestDTO request = AttendanceMarkRequestDTO.builder()
                .sessionId(10L)
                .items(List.of(new AttendanceMarkItemDTO(9L, "PRESENT")))
                .build();

        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(studentRepository.findAllById(List.of(9L))).thenReturn(List.of(inactive));

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(request));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("R9"));
        assertTrue(ex.getMessage().contains("is inactive"));
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_batchWithInactiveStudent_rollsBackAll() {
        Student inactive = Student.builder().id(8L).rollNumber("R8").name("S8")
                .section(section).status("INACTIVE").build();
        AttendanceMarkRequestDTO request = AttendanceMarkRequestDTO.builder()
                .sessionId(10L)
                .items(List.of(
                        new AttendanceMarkItemDTO(1L, "PRESENT"),
                        new AttendanceMarkItemDTO(8L, "ABSENT")))
                .build();

        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(studentRepository.findAllById(List.of(1L, 8L))).thenReturn(List.of(student1, inactive));

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(request));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("R8"));
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_unassignedTeacher_returns403() {
        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(validRequest()));
        assertEquals(403, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_cancelledSession_returns409() {
        AttendanceSession cancelled = AttendanceSession.builder().id(10L)
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .lecturePeriod("1st").date("2026-09-04").status("CANCELLED").build();
        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(cancelled));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(validRequest()));
        assertEquals(409, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_missingSession_returns404() {
        when(attendanceSessionRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceMarkRequestDTO request = AttendanceMarkRequestDTO.builder()
                .sessionId(99L).items(List.of(new AttendanceMarkItemDTO(1L, "PRESENT"))).build();

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(request));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void markAttendance_duplicateStudentWithinRequest_returns400() {
        AttendanceMarkRequestDTO request = AttendanceMarkRequestDTO.builder()
                .sessionId(10L)
                .items(List.of(
                        new AttendanceMarkItemDTO(1L, "PRESENT"),
                        new AttendanceMarkItemDTO(1L, "ABSENT")))
                .build();

        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(studentRepository.findAllById(List.of(1L, 1L))).thenReturn(List.of(student1));

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(request));
        assertEquals(400, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void markAttendance_nullItem_returns400() {
        AttendanceMarkRequestDTO request = AttendanceMarkRequestDTO.builder()
                .sessionId(10L)
                .items(java.util.Arrays.asList(new AttendanceMarkItemDTO(1L, "PRESENT"), null))
                .build();

        when(attendanceSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.markAttendance(request));
        assertEquals(400, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    private AttendanceRecord buildRecord(String status) {
        return AttendanceRecord.builder().id(5L)
                .session(session).student(student1).markedBy(teacher)
                .subject(subject).section(section)
                .status(status).lecturePeriod("1st").date("2026-09-04")
                .isPresent("PRESENT".equals(status)).build();
    }

    @Test
    void updateAttendance_statusChange_createsAudit() {
        AttendanceRecord record = buildRecord("ABSENT");
        when(attendanceRecordRepository.findById(5L)).thenReturn(Optional.of(record));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogService.recordChange(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder()
                .newStatus("PRESENT").reason("doc").build();
        AttendanceRecordDTO result = attendanceService.updateAttendance(5L, dto);

        assertEquals("PRESENT", result.getStatus());
        assertTrue(result.getIsPresent());
        verify(auditLogService).recordChange(record, "ABSENT", "PRESENT", teacher.getFullName(), "doc");
    }

    @Test
    void updateAttendance_unchangedStatus_returns400() {
        AttendanceRecord record = buildRecord("PRESENT");
        when(attendanceRecordRepository.findById(5L)).thenReturn(Optional.of(record));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);

        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder().newStatus("PRESENT").build();
        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.updateAttendance(5L, dto));
        assertEquals(400, ex.getStatus());
        verify(auditLogService, never()).recordChange(any(), any(), any(), any(), any());
    }

    @Test
    void updateAttendance_unassignedTeacher_returns403() {
        AttendanceRecord record = buildRecord("ABSENT");
        when(attendanceRecordRepository.findById(5L)).thenReturn(Optional.of(record));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(false);

        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder().newStatus("PRESENT").build();
        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.updateAttendance(5L, dto));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void updateAttendance_missingRecord_returns404() {
        when(attendanceRecordRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder().newStatus("PRESENT").build();
        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceService.updateAttendance(99L, dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void updateAttendance_historicalRecordOfInactiveStudent_remainsCorrigible() {
        Student inactive = Student.builder().id(9L).rollNumber("R9").name("S9")
                .section(section).status("INACTIVE").build();
        AttendanceRecord record = AttendanceRecord.builder().id(5L)
                .session(session).student(inactive).markedBy(teacher)
                .subject(subject).section(section)
                .status("ABSENT").lecturePeriod("1st").date("2026-09-04")
                .isPresent(false).build();
        when(attendanceRecordRepository.findById(5L)).thenReturn(Optional.of(record));
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(1L, 1L, 1L)).thenReturn(true);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogService.recordChange(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder()
                .newStatus("PRESENT").reason("doc").build();
        AttendanceRecordDTO result = attendanceService.updateAttendance(5L, dto);

        assertEquals("PRESENT", result.getStatus());
        assertTrue(result.getIsPresent());
        verify(auditLogService).recordChange(record, "ABSENT", "PRESENT", teacher.getFullName(), "doc");
    }
}
