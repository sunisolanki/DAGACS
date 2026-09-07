package com.dagacs.service;

import com.dagacs.dto.AttendanceSessionCreateRequestDTO;
import com.dagacs.dto.AttendanceSessionDTO;
import com.dagacs.dto.AttendanceSessionUpdateRequestDTO;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Section;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceSessionServiceTest {

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AuthenticatedTeacherResolver teacherResolver;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks
    private AttendanceSessionService attendanceSessionService;

    private Teacher teacher;
    private Subject subject;
    private Section section;

    @BeforeEach
    void setUp() {
        teacher = Teacher.builder().id(1L).email("t@dagacs.local").fullName("T").build();
        subject = Subject.builder().id(1L).code("CS101").name("DBMS").build();
        section = Section.builder().id(1L).sectionCode("CSE-A").name("CSE-A").build();
    }

    private AttendanceSessionCreateRequestDTO validDTO() {
        return AttendanceSessionCreateRequestDTO.builder()
                .subjectId(1L).sectionId(1L).lecturePeriod("1st").date("2026-09-04").build();
    }

    @Test
    void createSession_valid_returnsDTO() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSectionId(1L, 1L, 1L)).thenReturn(true);
        when(attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                1L, 1L, "2026-09-04", "1st")).thenReturn(false);
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(inv -> {
            AttendanceSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        AttendanceSessionDTO result = attendanceSessionService.createSession(validDTO());
        assertNotNull(result);
        assertEquals("SCHEDULED", result.getStatus());
        assertEquals("1st", result.getLecturePeriod());
    }

    @Test
    void createSession_invalidSubject_returns404() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.createSession(validDTO()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void createSession_invalidSection_returns404() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(1L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.createSession(validDTO()));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void createSession_notAssigned_returns403() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSectionId(1L, 1L, 1L)).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.createSession(validDTO()));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void createSession_duplicate_returns409() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSectionId(1L, 1L, 1L)).thenReturn(true);
        when(attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                1L, 1L, "2026-09-04", "1st")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.createSession(validDTO()));
        assertEquals(409, ex.getStatus());
        verify(attendanceSessionRepository, never()).save(any());
    }

    @Test
    void getSessionById_valid_returnsDTO() {
        AttendanceSession session = AttendanceSession.builder().id(1L)
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .lecturePeriod("1st").date("2026-09-04").status("SCHEDULED").build();
        when(attendanceSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        AttendanceSessionDTO result = attendanceSessionService.getSessionById(1L);
        assertEquals("1st", result.getLecturePeriod());
    }

    @Test
    void getSessionById_missing_returns404() {
        when(attendanceSessionRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.getSessionById(99L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void listTeacherSessions_returnsSessions() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(attendanceSessionRepository.findByTeacherEntityIdOrderByDateDesc(1L)).thenReturn(
                List.of(AttendanceSession.builder().id(1L)
                        .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                        .lecturePeriod("1st").date("2026-09-04").status("SCHEDULED").build()));

        List<AttendanceSessionDTO> result = attendanceSessionService.listTeacherSessions();
        assertEquals(1, result.size());
        assertEquals("1st", result.get(0).getLecturePeriod());
    }

    private AttendanceSession session(String period, String date) {
        return AttendanceSession.builder().id(1L)
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .lecturePeriod(period).date(date).status("SCHEDULED").build();
    }

    private AttendanceSessionUpdateRequestDTO updateDTO(String period, String date, String status) {
        return AttendanceSessionUpdateRequestDTO.builder()
                .lecturePeriod(period).date(date).status(status).build();
    }

    private void stubUpdateAuthorized() {
        when(teacherResolver.resolve()).thenReturn(teacher);
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSectionId(1L, 1L, 1L)).thenReturn(true);
    }

    @Test
    void updateSession_dateChange_withRecords_returns409() {
        AttendanceSession s = session("1st", "2026-09-04");
        when(attendanceSessionRepository.findById(1L)).thenReturn(Optional.of(s));
        stubUpdateAuthorized();
        when(attendanceRecordRepository.existsBySessionId(1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.updateSession(1L, updateDTO("1st", "2026-09-05", "SCHEDULED")));
        assertEquals(409, ex.getStatus());
        verify(attendanceSessionRepository, never()).save(any(AttendanceSession.class));
        assertEquals("1st", s.getLecturePeriod());
        assertEquals("2026-09-04", s.getDate());
    }

    @Test
    void updateSession_lecturePeriodChange_withRecords_returns409() {
        AttendanceSession s = session("1st", "2026-09-04");
        when(attendanceSessionRepository.findById(1L)).thenReturn(Optional.of(s));
        stubUpdateAuthorized();
        when(attendanceRecordRepository.existsBySessionId(1L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> attendanceSessionService.updateSession(1L, updateDTO("2nd", "2026-09-04", "SCHEDULED")));
        assertEquals(409, ex.getStatus());
        verify(attendanceSessionRepository, never()).save(any(AttendanceSession.class));
    }

    @Test
    void updateSession_dateChange_noRecords_succeeds() {
        AttendanceSession s = session("1st", "2026-09-04");
        when(attendanceSessionRepository.findById(1L)).thenReturn(Optional.of(s));
        stubUpdateAuthorized();
        when(attendanceRecordRepository.existsBySessionId(1L)).thenReturn(false);
        when(attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                1L, 1L, "2026-09-05", "1st")).thenReturn(false);
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceSessionDTO result = attendanceSessionService.updateSession(
                1L, updateDTO("1st", "2026-09-05", "SCHEDULED"));
        assertEquals("2026-09-05", result.getDate());
        assertEquals("1st", result.getLecturePeriod());
    }

    @Test
    void updateSession_lecturePeriodChange_noRecords_succeeds() {
        AttendanceSession s = session("1st", "2026-09-04");
        when(attendanceSessionRepository.findById(1L)).thenReturn(Optional.of(s));
        stubUpdateAuthorized();
        when(attendanceRecordRepository.existsBySessionId(1L)).thenReturn(false);
        when(attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                1L, 1L, "2026-09-04", "2nd")).thenReturn(false);
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceSessionDTO result = attendanceSessionService.updateSession(
                1L, updateDTO("2nd", "2026-09-04", "SCHEDULED"));
        assertEquals("2nd", result.getLecturePeriod());
        assertEquals("2026-09-04", result.getDate());
    }

    @Test
    void updateSession_statusOnly_withRecords_succeeds() {
        AttendanceSession s = session("1st", "2026-09-04");
        when(attendanceSessionRepository.findById(1L)).thenReturn(Optional.of(s));
        stubUpdateAuthorized();
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceSessionDTO result = attendanceSessionService.updateSession(
                1L, updateDTO("1st", "2026-09-04", "CONDUCTED"));
        assertEquals("CONDUCTED", result.getStatus());
        verify(attendanceRecordRepository, never()).existsBySessionId(anyLong());
    }
}
