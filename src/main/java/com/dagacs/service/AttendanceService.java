package com.dagacs.service;

import com.dagacs.dto.AttendanceMarkItemDTO;
import com.dagacs.dto.AttendanceMarkRequestDTO;
import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.dto.AttendanceUpdateRequestDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.dagacs.security.AuthenticatedTeacherResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private static final String STATUS_PRESENT = "PRESENT";
    private static final String STATUS_ABSENT = "ABSENT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_CONDUCTED = "CONDUCTED";

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final StudentRepository studentRepository;
    private final TeacherSubjectSectionAssignmentRepository assignmentRepository;
    private final AuthenticatedTeacherResolver teacherResolver;
    private final AttendanceAuditLogService auditLogService;

    @Autowired
    public AttendanceService(AttendanceRecordRepository attendanceRecordRepository,
                             AttendanceSessionRepository attendanceSessionRepository,
                             StudentRepository studentRepository,
                             TeacherSubjectSectionAssignmentRepository assignmentRepository,
                             AuthenticatedTeacherResolver teacherResolver,
                             AttendanceAuditLogService auditLogService) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.studentRepository = studentRepository;
        this.assignmentRepository = assignmentRepository;
        this.teacherResolver = teacherResolver;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public List<AttendanceRecordDTO> markAttendance(AttendanceMarkRequestDTO request) {
        if (request.getSessionId() == null) {
            throw new AuthException("Session ID is required", 400);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AuthException("At least one attendance item is required", 400);
        }

        AttendanceSession session = attendanceSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new AuthException(
                        "Attendance session not found with ID: " + request.getSessionId(), 404));

        Teacher teacher = teacherResolver.resolve();
        if (session.getSectionEntity() != null) {
            authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());
        } else {
            authorizeBatchTeacher(teacher, session.getSubjectEntity().getId(), session.getBatchEntity().getId());
        }

        if ("CANCELLED".equals(session.getStatus())) {
            throw new AuthException("Cannot mark attendance on a cancelled session", 409);
        }

        boolean batchSession = session.getSectionEntity() == null;
        if (request.getItems().stream().anyMatch(item -> item == null)) {
            throw new AuthException("Each attendance item must be provided", 400);
        }
        List<Long> studentIds = request.getItems().stream()
                .map(AttendanceMarkItemDTO::getStudentId)
                .collect(Collectors.toList());
        if (studentIds.stream().anyMatch(id -> id == null)) {
            throw new AuthException("Each attendance item must include a student ID", 400);
        }

        Map<Long, Student> studentsById = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();

        // Validate all items first; any failure rolls back the entire batch.
        Set<Long> seenStudentIds = new HashSet<>();
        for (AttendanceMarkItemDTO item : request.getItems()) {
            if (item == null) {
                throw new AuthException("Each attendance item must be provided", 400);
            }
            if (!seenStudentIds.add(item.getStudentId())) {
                throw new AuthException(
                        "Duplicate student ID " + item.getStudentId() + " in the marking request", 400);
            }
            String status = item.getStatus();
            if (status == null || status.trim().isEmpty()) {
                throw new AuthException("Status is required for each student", 400);
            }
            if (!STATUS_PRESENT.equals(status) && !STATUS_ABSENT.equals(status)) {
                throw new AuthException("Status must be PRESENT or ABSENT", 400);
            }
            Student student = studentsById.get(item.getStudentId());
            if (student == null) {
                throw new AuthException("Student not found with ID: " + item.getStudentId(), 404);
            }
            if (batchSession) {
                if (student.getBatch() == null
                        || !student.getBatch().getId().equals(session.getBatchEntity().getId())) {
                    throw new AuthException(
                            "Student " + item.getStudentId() + " does not belong to the session's batch", 400);
                }
            } else {
                if (student.getSection() == null
                        || !student.getSection().getId().equals(session.getSectionEntity().getId())) {
                    throw new AuthException(
                            "Student " + item.getStudentId() + " does not belong to the session's section", 400);
                }
            }
            if (!STATUS_ACTIVE.equals(student.getStatus())) {
                throw new AuthException(
                        "Student " + student.getRollNumber() + " is inactive and cannot be marked for attendance", 400);
            }
            if (attendanceRecordRepository.existsBySessionIdAndStudentId(
                    session.getId(), item.getStudentId())) {
                throw new AuthException(
                        "Attendance already recorded for student " + item.getStudentId()
                                + " in this session", 409);
            }
        }

        // All validation passed; persist the entire batch.
        List<AttendanceRecord> saved = request.getItems().stream()
                .map(item -> {
                    Student student = studentsById.get(item.getStudentId());
                    AttendanceRecord record = AttendanceRecord.builder()
                            .session(session)
                            .student(student)
                            .subject(session.getSubjectEntity())
                            .section(batchSession ? null : session.getSectionEntity())
                            .batch(batchSession ? session.getBatchEntity() : null)
                            .markedBy(teacher)
                            .status(item.getStatus())
                            .lecturePeriod(session.getLecturePeriod())
                            .date(session.getDate())
                            .isPresent(STATUS_PRESENT.equals(item.getStatus()))
                            .createdAt(now)
                            .build();
                    return attendanceRecordRepository.save(record);
                })
                .collect(Collectors.toList());

        // Attendance was successfully recorded: a SCHEDULED session becomes CONDUCTED.
        // Responsibilities stay untouched - the attended session remains editable and a
        // CANCELLED session remains blocked (validated above).
        if (STATUS_SCHEDULED.equals(session.getStatus())) {
            session.setStatus(STATUS_CONDUCTED);
        }

        return saved.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public AttendanceRecordDTO updateAttendance(Long id, AttendanceUpdateRequestDTO dto) {
        AttendanceRecord record = attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new AuthException("Attendance record not found with ID: " + id, 404));

        AttendanceSession session = record.getSession();
        Teacher teacher = teacherResolver.resolve();
        if (session.getSectionEntity() != null) {
            authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());
        } else {
            authorizeBatchTeacher(teacher, session.getSubjectEntity().getId(), session.getBatchEntity().getId());
        }

        if ("CANCELLED".equals(session.getStatus())) {
            throw new AuthException("Cannot update attendance on a cancelled session", 409);
        }

        String newStatus = dto.getNewStatus();
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new AuthException("New status is required", 400);
        }
        if (!STATUS_PRESENT.equals(newStatus) && !STATUS_ABSENT.equals(newStatus)) {
            throw new AuthException("Status must be PRESENT or ABSENT", 400);
        }

        String oldStatus = record.getStatus();
        if (oldStatus.equals(newStatus)) {
            // Idempotent no-op: the record is already in the requested state.
            // No save, no audit entry, and the (session_id, student_id) uniqueness
            // constraint is untouched.
            return convertToDTO(record);
        }

        record.setStatus(newStatus);
        record.setIsPresent(STATUS_PRESENT.equals(newStatus));
        record = attendanceRecordRepository.save(record);

        auditLogService.recordChange(record, oldStatus, newStatus, teacher.getFullName(), dto.getReason());

        return convertToDTO(record);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getAttendanceBySession(Long sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AuthException(
                        "Attendance session not found with ID: " + sessionId, 404));
        Teacher teacher = teacherResolver.resolve();
        if (session.getSectionEntity() != null) {
            authorizeTeacher(teacher, session.getSubjectEntity().getId(), session.getSectionEntity().getId());
        } else {
            authorizeBatchTeacher(teacher, session.getSubjectEntity().getId(), session.getBatchEntity().getId());
        }
        return attendanceRecordRepository.findBySessionId(sessionId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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

    private AttendanceRecordDTO convertToDTO(AttendanceRecord record) {
        Section section = record.getSection();
        return AttendanceRecordDTO.builder()
                .id(record.getId())
                .studentId(record.getStudent().getId())
                .subjectId(record.getSubject().getId())
                .sectionId(section != null ? section.getId() : null)
                .batchId(section == null ? record.getBatch().getId() : null)
                .status(record.getStatus())
                .lecturePeriod(record.getLecturePeriod())
                .date(record.getDate())
                .isPresent(record.getIsPresent())
                .sessionId(record.getSession().getId())
                .markedById(record.getMarkedBy().getId())
                .markedByName(record.getMarkedBy().getFullName())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
