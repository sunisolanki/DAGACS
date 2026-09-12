package com.dagacs.service;

import com.dagacs.dto.AttendanceSessionCreateRequestDTO;
import com.dagacs.dto.AttendanceSessionDTO;
import com.dagacs.dto.AttendanceSessionUpdateRequestDTO;
import com.dagacs.dto.StudentDTO;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttendanceSessionService {

    private static final Set<String> VALID_STATUSES =
            Set.of("SCHEDULED", "CONDUCTED", "CANCELLED");

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final TeacherSubjectSectionAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final AuthenticatedTeacherResolver teacherResolver;
    private final AttendanceRecordRepository attendanceRecordRepository;

    private Clock clock = Clock.systemDefaultZone();

    /** Test seam: pin the clock for deterministic future-date validation. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Autowired
    public AttendanceSessionService(AttendanceSessionRepository attendanceSessionRepository,
                                    SubjectRepository subjectRepository,
                                    SectionRepository sectionRepository,
                                    TeacherSubjectSectionAssignmentRepository assignmentRepository,
                                    StudentRepository studentRepository,
                                    AuthenticatedTeacherResolver teacherResolver,
                                    AttendanceRecordRepository attendanceRecordRepository) {
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.teacherResolver = teacherResolver;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public AttendanceSessionDTO createSession(AttendanceSessionCreateRequestDTO dto) {
        if (dto.getSubjectId() == null || dto.getSectionId() == null) {
            throw new AuthException("Subject and section are required", 400);
        }
        if (dto.getLecturePeriod() == null || dto.getLecturePeriod().trim().isEmpty()) {
            throw new AuthException("Lecture period is required", 400);
        }
        if (dto.getDate() == null || dto.getDate().trim().isEmpty()) {
            throw new AuthException("Date is required", 400);
        }
        rejectFutureDate(dto.getDate());

        Teacher teacher = teacherResolver.resolve();

        Subject subject = subjectRepository.findById(dto.getSubjectId())
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + dto.getSubjectId(), 404));
        Section section = sectionRepository.findById(dto.getSectionId())
                .orElseThrow(() -> new AuthException("Section not found with ID: " + dto.getSectionId(), 404));

        authorizeTeacher(teacher, subject.getId(), section.getId());

        if (attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                subject.getId(), section.getId(), dto.getDate(), dto.getLecturePeriod())) {
            throw new AuthException("An attendance session already exists for this subject, section, date, and period", 409);
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceSession session = AttendanceSession.builder()
                .subjectEntity(subject)
                .sectionEntity(section)
                .teacherEntity(teacher)
                .subject(subject.getName())
                .section(section.getName())
                .teacher(teacher.getFullName())
                .lecturePeriod(dto.getLecturePeriod())
                .date(dto.getDate())
                .status("SCHEDULED")
                .createdAt(now)
                .updatedAt(now)
                .build();
        session = attendanceSessionRepository.save(session);
        return convertToDTO(session);
    }

    @Transactional(readOnly = true)
    public AttendanceSessionDTO getSessionById(Long id) {
        AttendanceSession session = attendanceSessionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Attendance session not found with ID: " + id, 404));
        return convertToDTO(session);
    }

    @Transactional(readOnly = true)
    public List<AttendanceSessionDTO> listTeacherSessions() {
        Teacher teacher = teacherResolver.resolve();
        return attendanceSessionRepository.findByTeacherEntityIdOrderByDateDesc(teacher.getId()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentDTO> getStudentsForSession(Long sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AuthException("Attendance session not found with ID: " + sessionId, 404));
        Teacher teacher = teacherResolver.resolve();
        authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());

        List<Student> students = studentRepository.findBySectionIdAndStatusOrderByNameAsc(
                session.getSectionEntity().getId(), "ACTIVE");
        return students.stream()
                .map(s -> StudentDTO.builder()
                        .id(s.getId())
                        .rollNumber(s.getRollNumber())
                        .name(s.getName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceSessionDTO updateSession(Long id, AttendanceSessionUpdateRequestDTO dto) {
        AttendanceSession session = attendanceSessionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Attendance session not found with ID: " + id, 404));

        Teacher teacher = teacherResolver.resolve();
        authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());

        if (dto.getLecturePeriod() == null || dto.getLecturePeriod().trim().isEmpty()) {
            throw new AuthException("Lecture period is required", 400);
        }
        if (dto.getDate() == null || dto.getDate().trim().isEmpty()) {
            throw new AuthException("Date is required", 400);
        }
        if (dto.getStatus() == null || dto.getStatus().trim().isEmpty()) {
            throw new AuthException("Status is required", 400);
        }
        if (!VALID_STATUSES.contains(dto.getStatus())) {
            throw new AuthException("Status must be SCHEDULED, CONDUCTED, or CANCELLED", 400);
        }

        if (!session.getLecturePeriod().equals(dto.getLecturePeriod())
                || !session.getDate().equals(dto.getDate())) {
            // AttendanceRecord retains date/lecturePeriod as synchronized snapshots; changing
            // them once records exist would desynchronize the session from its records.
            if (!session.getDate().equals(dto.getDate())) {
                rejectFutureDate(dto.getDate());
            }
            if (attendanceRecordRepository.existsBySessionId(session.getId())) {
                throw new AuthException(
                        "Cannot change session date or lecture period after attendance has been recorded", 409);
            }
            if (attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                    session.getSubjectEntity().getId(), session.getSectionEntity().getId(),
                    dto.getDate(), dto.getLecturePeriod())) {
                throw new AuthException("An attendance session already exists for this subject, section, date, and period", 409);
            }
        }

        session.setLecturePeriod(dto.getLecturePeriod());
        session.setDate(dto.getDate());
        session.setStatus(dto.getStatus());
        session.setUpdatedAt(LocalDateTime.now());
        session = attendanceSessionRepository.save(session);
        return convertToDTO(session);
    }

    private void authorizeTeacher(Teacher teacher, Long subjectId, Long sectionId) {
        if (!assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(teacher.getId(), sectionId, subjectId)) {
            throw new AuthException("Teacher is not assigned to this subject and section", 403);
        }
    }

    private void rejectFutureDate(String date) {
        if (LocalDate.parse(date).isAfter(LocalDate.now(clock))) {
            throw new AuthException("Attendance date cannot be in the future", 400);
        }
    }

    private AttendanceSessionDTO convertToDTO(AttendanceSession session) {
        return AttendanceSessionDTO.builder()
                .id(session.getId())
                .subjectId(session.getSubjectEntity().getId())
                .subjectName(session.getSubjectEntity().getName())
                .sectionId(session.getSectionEntity().getId())
                .sectionName(session.getSectionEntity().getName())
                .teacherId(session.getTeacherEntity().getId())
                .lecturePeriod(session.getLecturePeriod())
                .date(session.getDate())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
