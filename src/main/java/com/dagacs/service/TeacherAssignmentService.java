package com.dagacs.service;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.dto.TeacherAssignmentRequestDTO;
import com.dagacs.dto.TeacherDTO;
import com.dagacs.entity.*;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M9.3 teacher assignment management.
 *
 * <p>An assignment binds an existing {@link Teacher} to a {@link SubjectOffering}
 * and a {@link Section}. Subject identity is derived through the SubjectOffering
 * canonical academic chain; the mandatory compatibility rule is that the
 * SubjectOffering's AcademicSession (via its Semester) equals the Section's Batch
 * AcademicSession. Requests carry exactly {teacherId, subjectOfferingId,
 * sectionId}; every other attribute in the response is derived server-side.</p>
 */
@Service
public class TeacherAssignmentService {

    private final TeacherSubjectSectionAssignmentRepository assignmentRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final SectionRepository sectionRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public TeacherAssignmentService(TeacherSubjectSectionAssignmentRepository assignmentRepository,
                                    TeacherRepository teacherRepository,
                                    SubjectOfferingRepository subjectOfferingRepository,
                                    SectionRepository sectionRepository,
                                    AttendanceSessionRepository attendanceSessionRepository,
                                    AttendanceRecordRepository attendanceRecordRepository) {
        this.assignmentRepository = assignmentRepository;
        this.teacherRepository = teacherRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.sectionRepository = sectionRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public TeacherAssignmentDTO createAssignment(TeacherAssignmentRequestDTO request) {
        Long teacherId = requireId(request.getTeacherId(), "Teacher is required");
        Long subjectOfferingId = requireId(request.getSubjectOfferingId(), "Subject offering is required");
        Long sectionId = requireId(request.getSectionId(), "Section is required");

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new AuthException("Teacher not found with ID: " + teacherId, 404));
        requireActiveTeacher(teacher);
        SubjectOffering offering = subjectOfferingRepository.findById(subjectOfferingId)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + subjectOfferingId, 404));
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new AuthException("Section not found with ID: " + sectionId, 404));

        requireCompatibleAcademicSessions(offering, section);
        if (assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndSectionId(teacherId, subjectOfferingId, sectionId)) {
            throw new AuthException("Teacher is already assigned to this subject offering and section", 409);
        }

        LocalDateTime now = LocalDateTime.now();
        TeacherSubjectSectionAssignment assignment = TeacherSubjectSectionAssignment.builder()
                .teacher(teacher)
                .subjectOffering(offering)
                .section(section)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return convertToDTO(assignmentRepository.save(assignment));
    }

    @Transactional
    public TeacherAssignmentDTO updateAssignment(Long id, TeacherAssignmentRequestDTO request) {
        TeacherSubjectSectionAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Teacher assignment not found with ID: " + id, 404));

        Long teacherId = requireId(request.getTeacherId(), "Teacher is required");
        Long subjectOfferingId = requireId(request.getSubjectOfferingId(), "Subject offering is required");
        Long sectionId = requireId(request.getSectionId(), "Section is required");

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new AuthException("Teacher not found with ID: " + teacherId, 404));
        SubjectOffering offering = subjectOfferingRepository.findById(subjectOfferingId)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + subjectOfferingId, 404));
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new AuthException("Section not found with ID: " + sectionId, 404));

        // All change detection and guards operate on the ORIGINAL persisted
        // assignment before any entity mutation (M9.15).
        boolean teacherChanged = !teacherId.equals(assignment.getTeacher().getId());
        boolean contextChanged = !subjectOfferingId.equals(assignment.getSubjectOffering().getId())
                || !sectionId.equals(assignment.getSection().getId());

        // M9.15 RULE 2A: an INACTIVE teacher may never become the target of an
        // update. The guard fires only when the teacher actually changes; a
        // historical assignment that keeps its (possibly INACTIVE) teacher is
        // not a teacher change and is left to the other validation/history rules.
        if (teacherChanged && !"ACTIVE".equals(teacher.getStatus())) {
            throw new AuthException("Cannot assign an inactive teacher.", 409);
        }

        requireCompatibleAcademicSessions(offering, section);

        // Duplicate check excludes the row being updated.
        assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(teacherId, subjectOfferingId, sectionId)
                .filter(other -> !other.getId().equals(assignment.getId()))
                .ifPresent(other -> {
                    throw new AuthException("Teacher is already assigned to this subject offering and section", 409);
                });

        // M9.15: historical-dependency guards against the ORIGINAL persisted
        // context (teacher + subject via offering + section), never against
        // partially mutated state, and never a generic subject+section check.
        Long originalTeacherId = assignment.getTeacher().getId();
        Long originalSubjectId = assignment.getSubjectOffering().getSubject().getId();
        Long originalSectionId = assignment.getSection().getId();
        boolean hasHistory = hasAttendanceHistory(originalTeacherId, originalSubjectId, originalSectionId);

        if (teacherChanged && hasHistory) {
            throw new AuthException("Cannot change assignment teacher while attendance history exists.", 409);
        }
        if (contextChanged && hasHistory) {
            throw new AuthException("Cannot change assignment context while attendance history exists.", 409);
        }

        assignment.setTeacher(teacher);
        assignment.setSubjectOffering(offering);
        assignment.setSection(section);
        assignment.setUpdatedAt(LocalDateTime.now());
        return convertToDTO(assignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<TeacherAssignmentDTO> getAllAssignments() {
        return assignmentRepository.findAllByOrderByIdAsc().stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional
    public void deleteAssignment(Long id) {
        TeacherSubjectSectionAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Teacher assignment not found with ID: " + id, 404));
        Long teacherId = assignment.getTeacher().getId();
        Long subjectId = assignment.getSubjectOffering().getSubject().getId();
        Long sectionId = assignment.getSection().getId();
        if (hasAttendanceHistory(teacherId, subjectId, sectionId)) {
            throw new AuthException("Cannot delete assignment while attendance history exists.", 409);
        }
        assignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public List<TeacherAssignmentDTO> getAssignmentsForTeacher(Long teacherId) {
        return assignmentRepository.findByTeacherIdOrderByIdAsc(teacherId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherDTO> listTeachers() {
        return teacherRepository.findAllByOrderByFullNameAsc().stream()
                .map(this::convertToTeacherDTO)
                .toList();
    }

    private Long requireId(Long id, String message) {
        if (id == null) {
            throw new AuthException(message, 400);
        }
        return id;
    }

    private void requireActiveTeacher(Teacher teacher) {
        if (!"ACTIVE".equals(teacher.getStatus())) {
            throw new AuthException("Cannot assign an inactive teacher.", 409);
        }
    }

    /**
     * M9.15 teacher-scoped historical-attendance dependency check. History for
     * an assignment context counts either as sessions the teacher created in
     * that subject + section, or attendance records the teacher marked there.
     * Both checks are scoped to the specific teacher so co-teacher assignments
     * (many teachers, one SubjectOffering + Section) stay independent.
     */
    private boolean hasAttendanceHistory(Long teacherId, Long subjectId, Long sectionId) {
        return attendanceSessionRepository.existsByTeacherEntityIdAndSubjectEntityIdAndSectionEntityId(
                teacherId, subjectId, sectionId)
                || attendanceRecordRepository.existsByMarkedByIdAndSubjectIdAndSectionId(
                        teacherId, subjectId, sectionId);
    }

    /**
     * Mandatory compatibility gate (HTTP 400): the SubjectOffering's AcademicSession
     * (resolved through its Semester) must equal the Section's Batch AcademicSession.
     */
    private void requireCompatibleAcademicSessions(SubjectOffering offering, Section section) {
        Long offeringSessionId = offering.getSemester() != null && offering.getSemester().getAcademicSession() != null
                ? offering.getSemester().getAcademicSession().getId()
                : null;
        Long sectionSessionId = section.getBatch() != null && section.getBatch().getAcademicSession() != null
                ? section.getBatch().getAcademicSession().getId()
                : null;
        if (offeringSessionId == null || sectionSessionId == null || !offeringSessionId.equals(sectionSessionId)) {
            throw new AuthException(
                    "Academic session mismatch: the SubjectOffering belongs to a different academic session than the Section's batch",
                    400);
        }
    }

    private TeacherAssignmentDTO convertToDTO(TeacherSubjectSectionAssignment assignment) {
        Teacher teacher = assignment.getTeacher();
        SubjectOffering offering = assignment.getSubjectOffering();
        Subject subject = offering.getSubject();
        Semester semester = offering.getSemester();
        AcademicSession session = semester.getAcademicSession();
        Program program = session.getProgram();
        Department department = program.getDepartment();
        Section section = assignment.getSection();
        Batch batch = section.getBatch();
        return TeacherAssignmentDTO.builder()
                .id(assignment.getId())
                .teacherId(teacher.getId())
                .teacherName(teacher.getFullName())
                .teacherEmail(teacher.getEmail())
                .subjectOfferingId(offering.getId())
                .subjectId(subject.getId())
                .subjectCode(subject.getCode())
                .subjectName(subject.getName())
                .semesterId(semester.getId())
                .semesterName(semester.getName())
                .sessionId(session.getId())
                .sessionName(session.getName())
                .programId(program.getId())
                .programName(program.getName())
                .departmentId(department.getId())
                .departmentName(department.getName())
                .sectionId(section.getId())
                .sectionCode(section.getSectionCode())
                .sectionName(section.getName())
                .batchId(batch.getId())
                .batchCode(batch.getBatchCode())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    private TeacherDTO convertToTeacherDTO(Teacher teacher) {
        Department department = teacher.getDepartment();
        return TeacherDTO.builder()
                .id(teacher.getId())
                .email(teacher.getEmail())
                .fullName(teacher.getFullName())
                .designation(teacher.getDesignation())
                .status(teacher.getStatus())
                .departmentId(department == null ? null : department.getId())
                .departmentName(department == null ? null : department.getName())
                .build();
    }
}