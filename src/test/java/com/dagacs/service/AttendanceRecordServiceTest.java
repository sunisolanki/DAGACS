package com.dagacs.service;

import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceRecordServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private AttendanceRecordService attendanceRecordService;

    private Student student;
    private Subject subject;
    private Section section;
    private AttendanceRecord record;

    @BeforeEach
    void setUp() {
        student = Student.builder().id(1L).rollNumber("2201CE001").name("Rahul").build();
        subject = Subject.builder().id(1L).code("CS101").name("Data Structures").build();
        section = Section.builder().id(1L).sectionCode("SEC-A").name("A").build();
        record = AttendanceRecord.builder()
                .id(1L).student(student).subject(subject).section(section)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .build();
    }

    private AttendanceRecordDTO validDTO() {
        return AttendanceRecordDTO.builder()
                .studentId(1L).subjectId(1L).sectionId(1L)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .build();
    }

    @Test
    void saveAttendanceRecord_valid_returnsDTO() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(attendanceRecordRepository.existsByStudentIdAndSubjectIdAndSectionIdAndDate(1L, 1L, 1L, "2026-09-04"))
                .thenReturn(false);
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecordDTO result = attendanceRecordService.saveAttendanceRecord(validDTO());
        assertNotNull(result);
        assertEquals("PRESENT", result.getStatus());
        assertEquals("2026-09-04", result.getDate());
    }

    @Test
    void saveAttendanceRecord_duplicate_returns409() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(attendanceRecordRepository.existsByStudentIdAndSubjectIdAndSectionIdAndDate(1L, 1L, 1L, "2026-09-04"))
                .thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceRecordService.saveAttendanceRecord(validDTO()));
        assertEquals(409, ex.getStatus());
        verify(attendanceRecordRepository, never()).save(any());
    }

    @Test
    void saveAttendanceRecord_invalidStudent_returns404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceRecordDTO dto = validDTO();
        dto.setStudentId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceRecordService.saveAttendanceRecord(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveAttendanceRecord_invalidSubject_returns404() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(subjectRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceRecordDTO dto = validDTO();
        dto.setSubjectId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceRecordService.saveAttendanceRecord(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void saveAttendanceRecord_invalidSection_returns404() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(99L)).thenReturn(Optional.empty());

        AttendanceRecordDTO dto = validDTO();
        dto.setSectionId(99L);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceRecordService.saveAttendanceRecord(dto));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getAttendanceRecordById_valid_returnsDTO() {
        when(attendanceRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        AttendanceRecordDTO result = attendanceRecordService.getAttendanceRecordById(1L);
        assertEquals("PRESENT", result.getStatus());
    }

    @Test
    void getAttendanceRecordById_missing_returns404() {
        when(attendanceRecordRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceRecordService.getAttendanceRecordById(99L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getAttendanceRecordsByStudent_returnsList() {
        when(attendanceRecordRepository.findByStudentId(1L)).thenReturn(List.of(record));

        List<AttendanceRecordDTO> result = attendanceRecordService.getAttendanceRecordsByStudent(1L);
        assertEquals(1, result.size());
        assertEquals("PRESENT", result.get(0).getStatus());
    }
}
