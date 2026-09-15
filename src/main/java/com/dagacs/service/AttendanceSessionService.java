package com.dagacs.service;

import com.dagacs.dto.AttendanceSessionCreateRequestDTO;
import com.dagacs.dto.AttendanceSessionDTO;
import com.dagacs.dto.AttendanceSessionUpdateRequestDTO;
import com.dagacs.dto.StudentDTO;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
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
    private final BatchRepository batchRepository;
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
                                    BatchRepository batchRepository,
                                    TeacherSubjectSectionAssignmentRepository assignmentRepository,
                                    StudentRepository studentRepository,
                                    AuthenticatedTeacherResolver teacherResolver,
                                    AttendanceRecordRepository attendanceRecordRepository) {
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
        this.batchRepository = batchRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.teacherResolver = teacherResolver;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public AttendanceSessionDTO createSession(AttendanceSessionCreateRequestDTO dto) {
        if (dto.getSubjectId() == null) {
            throw new AuthException("Subject is required", 400);
        }
        if (dto.getSectionId() != null && dto.getBatchId() != null) {
            throw new AuthException("Provide exactly one of sectionId or batchId, not both.", 400);
        }
        if (dto.getSectionId() == null && dto.getBatchId() == null) {
            throw new AuthException("Provide exactly one of sectionId or batchId.", 400);
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
        SessionContext context = resolveContext(dto);

        if (context.section != null) {
            authorizeTeacher(teacher, subject.getId(), context.section.getId());
            if (attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                    subject.getId(), context.section.getId(), dto.getDate(), dto.getLecturePeriod())) {
                throw new AuthException(
                        "An attendance session already exists for this subject, section, date, and period", 409);
            }
        } else {
            authorizeBatchTeacher(teacher, subject.getId(), context.batch.getId());
            if (attendanceSessionRepository.existsBySubjectEntityIdAndBatchEntityIdAndDateAndLecturePeriod(
                    subject.getId(), context.batch.getId(), dto.getDate(), dto.getLecturePeriod())) {
                throw new AuthException(
                        "An attendance session already exists for this subject, batch, date, and period", 409);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceSession session = AttendanceSession.builder()
                .subjectEntity(subject)
                .sectionEntity(context.section)
                .batchEntity(context.batch)
                .teacherEntity(teacher)
                .subject(subject.getName())
                .section(context.section != null ? context.section.getName() : null)
                .batch(context.batch != null ? context.batch.getBatchCode() : null)
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
        if (session.getSectionEntity() != null) {
            authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());
        } else {
            authorizeBatchTeacher(teacher, session.getSubjectEntity().getId(), session.getBatchEntity().getId());
        }

        List<Student> students = session.getSectionEntity() != null
                ? studentRepository.findBySectionIdAndStatusOrderByEnrollmentNumberAsc(
                        session.getSectionEntity().getId(), "ACTIVE")
                : studentRepository.findByBatchIdAndStatusOrderByEnrollmentNumberAsc(
                        session.getBatchEntity().getId(), "ACTIVE");
        return students.stream()
                .map(s -> StudentDTO.builder()
                        .id(s.getId())
                        .rollNumber(s.getRollNumber())
                        .name(s.getName())
                        .enrollmentNumber(s.getEnrollmentNumber())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceSessionDTO updateSession(Long id, AttendanceSessionUpdateRequestDTO dto) {
        AttendanceSession session = attendanceSessionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Attendance session not found with ID: " + id, 404));

        Teacher teacher = teacherResolver.resolve();
        if (session.getSectionEntity() != null) {
            authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());
        } else {
            authorizeBatchTeacher(teacher, session.getSubjectEntity().getId(), session.getBatchEntity().getId());
        }

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
            if (session.getSectionEntity() != null) {
                if (attendanceSessionRepository.existsBySubjectEntityIdAndSectionEntityIdAndDateAndLecturePeriod(
                        session.getSubjectEntity().getId(), session.getSectionEntity().getId(),
                        dto.getDate(), dto.getLecturePeriod())) {
                    throw new AuthException(
                            "An attendance session already exists for this subject, section, date, and period", 409);
                }
            } else {
                if (attendanceSessionRepository.existsBySubjectEntityIdAndBatchEntityIdAndDateAndLecturePeriod(
                        session.getSubjectEntity().getId(), session.getBatchEntity().getId(),
                        dto.getDate(), dto.getLecturePeriod())) {
                    throw new AuthException(
                            "An attendance session already exists for this subject, batch, date, and period", 409);
                }
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

    private void authorizeBatchTeacher(Teacher teacher, Long subjectId, Long batchId) {
        if (!assignmentRepository.existsByTeacherIdAndBatchIdAndSubjectOfferingSubjectId(teacher.getId(), batchId, subjectId)) {
            throw new AuthException("Teacher is not assigned to this subject and batch", 403);
        }
    }

    /**
     * Resolve the section-or-batch session context. Batch mode requires the
     * structural precondition that the batch has no sections.
     */
    private SessionContext resolveContext(AttendanceSessionCreateRequestDTO dto) {
        if (dto.getSectionId() != null) {
            Section section = sectionRepository.findById(dto.getSectionId())
                    .orElseThrow(() -> new AuthException("Section not found with ID: " + dto.getSectionId(), 404));
            return new SessionContext(section, null);
        }
        Batch batch = batchRepository.findById(dto.getBatchId())
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + dto.getBatchId(), 404));
        if (!sectionRepository.findByBatch(batch).isEmpty()) {
            throw new AuthException("Batch has sections; use sectionId instead of batchId.", 400);
        }
        return new SessionContext(null, batch);
    }

    private void rejectFutureDate(String date) {
        if (LocalDate.parse(date).isAfter(LocalDate.now(clock))) {
            throw new AuthException("Attendance date cannot be in the future", 400);
        }
    }

    private AttendanceSessionDTO convertToDTO(AttendanceSession session) {
        Section section = session.getSectionEntity();
        Batch batch = session.getBatchEntity() != null
                ? session.getBatchEntity()
                : (section != null ? section.getBatch() : null);
        return AttendanceSessionDTO.builder()
                .id(session.getId())
                .subjectId(session.getSubjectEntity().getId())
                .subjectName(session.getSubjectEntity().getName())
                .sectionId(section != null ? section.getId() : null)
                .sectionName(section != null ? section.getName() : null)
                .batchId(batch != null ? batch.getId() : null)
                .batchName(batch != null ? batch.getBatchCode() : null)
                .teacherId(session.getTeacherEntity().getId())
                .lecturePeriod(session.getLecturePeriod())
                .date(session.getDate())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .build();
    }

    private record SessionContext(Section section, Batch batch) {}
}
