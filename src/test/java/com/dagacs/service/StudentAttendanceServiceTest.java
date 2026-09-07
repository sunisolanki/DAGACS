package com.dagacs.service;

import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAttendanceServiceTest {

    private final AuthenticatedStudentResolver resolver = mock(AuthenticatedStudentResolver.class);
    private final AttendanceRecordRepository recordRepository = mock(AttendanceRecordRepository.class);

    private final StudentAttendanceService service =
            new StudentAttendanceService(resolver, recordRepository);

    private AttendanceRecord record(long id, Student student) {
        AttendanceSession session = mock(AttendanceSession.class);
        when(session.getId()).thenReturn(99L);
        Subject subject = mock(Subject.class);
        Section section = mock(Section.class);
        Teacher teacher = mock(Teacher.class);
        when(teacher.getFullName()).thenReturn("Teacher");
        AttendanceRecord r = mock(AttendanceRecord.class);
        when(r.getId()).thenReturn(id);
        when(r.getStudent()).thenReturn(student);
        when(r.getSubject()).thenReturn(subject);
        when(r.getSection()).thenReturn(section);
        when(r.getStatus()).thenReturn("PRESENT");
        when(r.getLecturePeriod()).thenReturn("1st");
        when(r.getDate()).thenReturn("2026-09-04");
        when(r.getIsPresent()).thenReturn(true);
        when(r.getSession()).thenReturn(session);
        when(r.getMarkedBy()).thenReturn(teacher);
        return r;
    }

    @Test
    void getMyAttendance_onlyReturnsResolvedStudentsRecords() {
        Student current = new Student();
        current.setId(1L);
        when(resolver.resolve()).thenReturn(current);

        List<AttendanceRecord> records = List.of(record(1L, current));
        when(recordRepository.findByStudentId(1L)).thenReturn(records);

        List<AttendanceRecordDTO> result = service.getMyAttendance();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getStudentId());
        // Ownership is read only for the single resolved student, never a supplied id.
        verify(recordRepository).findByStudentId(1L);
    }

    @Test
    void getMyAttendance_neverUsesAnotherStudentId() {
        Student current = new Student();
        current.setId(1L);
        when(resolver.resolve()).thenReturn(current);
        when(recordRepository.findByStudentId(1L)).thenReturn(List.of());

        service.getMyAttendance();

        // No other student's repository query may ever be issued.
        verify(recordRepository, never()).findByStudentId(2L);
    }
}
