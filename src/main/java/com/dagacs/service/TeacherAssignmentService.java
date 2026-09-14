package com.dagacs.service;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.dto.TeacherAssignmentRequestDTO;
import com.dagacs.dto.TeacherDTO;
import com.dagacs.entity.*;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
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
 * and a {@link Section} (section mode) or, for zero-section batches, directly
 * to a {@link Batch} (batch mode). Exactly one of sectionId/batchId is set.
 * Subject identity is derived through the SubjectOffering canonical academic
 * chain; the mandatory compatibility rule is that the SubjectOffering's
 * AcademicSession (via its Semester) equals the Section's Batch — or the
 * Batch's — AcademicSession. Requests carry exactly {teacherId,
 * subjectOfferingId, sectionId|batchId}; every other attribute in the response
 * is derived server-side.</p>
 */
@Service
public class TeacherAssignmentService {

    private final TeacherSubjectSectionAssignmentRepository assignmentRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final SectionRepository sectionRepository;
    private final BatchRepository batchRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public TeacherAssignmentService(TeacherSubjectSectionAssignmentRepository assignmentRepository,
                                    TeacherRepository teacherRepository,
                                    SubjectOfferingRepository subjectOfferingRepository,
                                    SectionRepository sectionRepository,
                                    BatchRepository batchRepository,
                                    AttendanceSessionRepository attendanceSessionRepository,
                                    AttendanceRecordRepository attendanceRecordRepository) {
        this.assignmentRepository = assignmentRepository;
        this.teacherRepository = teacherRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.sectionRepository = sectionRepository;
        this.batchRepository = batchRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public TeacherAssignmentDTO createAssignment(TeacherAssignmentRequestDTO request) {
        Long teacherId = requireId(request.getTeacherId(), "Teacher is required");
        Long subjectOfferingId = requireId(request.getSubjectOfferingId(), "Subject offering is required");

        AssignmentTarget target = resolveTarget(request);

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new AuthException("Teacher not found with ID: " + teacherId, 404));
        requireActiveTeacher(teacher);
        SubjectOffering offering = subjectOfferingRepository.findById(subjectOfferingId)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + subjectOfferingId, 404));

        if (target.section != null) {
            requireCompatibleAcademicSessions(offering, target.section);
            if (assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndSectionId(
                    teacherId, subjectOfferingId, target.section.getId())) {
                throw new AuthException("Teacher is already assigned to this subject offering and section", 409);
            }
        } else {
            requireCompatibleAcademicSessionsForBatch(offering, target.batch);
            if (assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndBatchId(
                    teacherId, subjectOfferingId, target.batch.getId())) {
                throw new AuthException("Teacher is already assigned to this subject offering and batch", 409);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        TeacherSubjectSectionAssignment assignment = TeacherSubjectSectionAssignment.builder()
                .teacher(teacher)
                .subjectOffering(offering)
                .section(target.section)
                .batch(target.batch)
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

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new AuthException("Teacher not found with ID: " + teacherId, 404));
        SubjectOffering offering = subjectOfferingRepository.findById(subjectOfferingId)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + subjectOfferingId, 404));

        AssignmentTarget target = resolveTarget(request);

        // All change detection and guards operate on the ORIGINAL persisted
        // assignment before any entity mutation (M9.15).
        boolean teacherChanged = !teacherId.equals(assignment.getTeacher().getId());
        boolean contextChanged = !subjectOfferingId.equals(assignment.getSubjectOffering().getId())
                || targetChanged(assignment, target);

        // M9.15 RULE 2A: an INACTIVE teacher may never become the target of an
        // update. The guard fires only when the teacher actually changes; a
        // historical assignment that keeps its (possibly INACTIVE) teacher is
        // not a teacher change and is left to the other validation/history rules.
        if (teacherChanged && !"ACTIVE".equals(teacher.getStatus())) {
            throw new AuthException("Cannot assign an inactive teacher.", 409);
        }

        if (target.section != null) {
            requireCompatibleAcademicSessions(offering, target.section);
        } else {
            requireCompatibleAcademicSessionsForBatch(offering, target.batch);
        }

        // Duplicate check excludes the row being updated.
        if (target.section != null) {
            assignmentRepository
                    .findByTeacherIdAndSubjectOfferingIdAndSectionId(
                            teacherId, subjectOfferingId, target.section.getId())
                    .filter(other -> !other.getId().equals(assignment.getId()))
                    .ifPresent(other -> {
                        throw new AuthException(
                                "Teacher is already assigned to this subject offering and section", 409);
                    });
        } else {
            assignmentRepository
                    .findByTeacherIdAndSubjectOfferingIdAndBatchId(
                            teacherId, subjectOfferingId, target.batch.getId())
                    .filter(other -> !other.getId().equals(assignment.getId()))
                    .ifPresent(other -> {
                        throw new AuthException(
                                "Teacher is already assigned to this subject offering and batch", 409);
                    });
        }

        // M9.15: historical-dependency guards against the ORIGINAL persisted
        // context (teacher + subject via offering + section-or-batch), never
        // against partially mutated state, and never a generic subject+context
        // check.
        Long originalTeacherId = assignment.getTeacher().getId();
        Long originalSubjectId = assignment.getSubjectOffering().getSubject().getId();
        boolean originalIsSectionMode = assignment.getSection() != null;
        boolean hasHistory = originalIsSectionMode
                ? hasAttendanceHistory(originalTeacherId, originalSubjectId, assignment.getSection().getId())
                : hasBatchAttendanceHistory(originalTeacherId, originalSubjectId, assignment.getBatch().getId());

        if (teacherChanged && hasHistory) {
            throw new AuthException("Cannot change assignment teacher while attendance history exists.", 409);
        }
        if (contextChanged && hasHistory) {
            throw new AuthException("Cannot change assignment context while attendance history exists.", 409);
        }

        assignment.setTeacher(teacher);
        assignment.setSubjectOffering(offering);
        assignment.setSection(target.section);
        assignment.setBatch(target.batch);
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
        boolean sectionMode = assignment.getSection() != null;
        boolean hasHistory = sectionMode
                ? hasAttendanceHistory(teacherId, subjectId, assignment.getSection().getId())
                : hasBatchAttendanceHistory(teacherId, subjectId, assignment.getBatch().getId());
        if (hasHistory) {
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
     * M9.15 batch-mode historical-attendance dependency check, scoped to teacher.
     */
    private boolean hasBatchAttendanceHistory(Long teacherId, Long subjectId, Long batchId) {
        return attendanceSessionRepository.existsByTeacherEntityIdAndSubjectEntityIdAndBatchEntityId(
                teacherId, subjectId, batchId)
                || attendanceRecordRepository.existsByMarkedByIdAndSubjectIdAndBatchId(
                        teacherId, subjectId, batchId);
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

    /**
     * Mandatory compatibility gate (HTTP 400): batch-mode. The SubjectOffering's
     * AcademicSession must equal the Batch's AcademicSession.
     */
    private void requireCompatibleAcademicSessionsForBatch(SubjectOffering offering, Batch batch) {
        Long offeringSessionId = offering.getSemester() != null && offering.getSemester().getAcademicSession() != null
                ? offering.getSemester().getAcademicSession().getId()
                : null;
        Long batchSessionId = batch.getAcademicSession() != null
                ? batch.getAcademicSession().getId()
                : null;
        if (offeringSessionId == null || batchSessionId == null || !offeringSessionId.equals(batchSessionId)) {
            throw new AuthException(
                    "Academic session mismatch: the SubjectOffering belongs to a different academic session than the Batch",
                    400);
        }
    }

    /**
     * Resolve the assignment target (section or batch) from the request.
     * Enforces exactly-one-of XOR and validates batch-mode structural
     * precondition (batch has no sections — batch.id == zeroSectionTrigger).
     */
    private AssignmentTarget resolveTarget(TeacherAssignmentRequestDTO request) {
        Long sectionId = request.getSectionId();
        Long batchId = request.getBatchId();
        if (sectionId != null && batchId != null) {
            throw new AuthException("Provide exactly one of sectionId or batchId, not both.", 400);
        }
        if (sectionId == null && batchId == null) {
            throw new AuthException("Provide exactly one of sectionId or batchId.", 400);
        }
        if (sectionId != null) {
            Section section = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new AuthException("Section not found with ID: " + sectionId, 404));
            return new AssignmentTarget(section, null);
        }
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + batchId, 404));
        if (!sectionRepository.findByBatch(batch).isEmpty()) {
            throw new AuthException("Batch has sections; use sectionId instead of batchId.", 400);
        }
        return new AssignmentTarget(null, batch);
    }

    private boolean targetChanged(TeacherSubjectSectionAssignment assignment, AssignmentTarget target) {
        Long existingSectionId = assignment.getSection() != null ? assignment.getSection().getId() : null;
        Long existingBatchId = assignment.getBatch() != null ? assignment.getBatch().getId() : null;
        Long newSectionId = target.section != null ? target.section.getId() : null;
        Long newBatchId = target.batch != null ? target.batch.getId() : null;
        return !java.util.Objects.equals(existingSectionId, newSectionId)
                || !java.util.Objects.equals(existingBatchId, newBatchId);
    }

    private record AssignmentTarget(Section section, Batch batch) {}

    private TeacherAssignmentDTO convertToDTO(TeacherSubjectSectionAssignment assignment) {
        Teacher teacher = assignment.getTeacher();
        SubjectOffering offering = assignment.getSubjectOffering();
        Subject subject = offering.getSubject();
        Semester semester = offering.getSemester();
        AcademicSession session = semester.getAcademicSession();
        Program program = session.getProgram();
        Department department = program.getDepartment();
        Batch batch = assignment.getSection() != null
                ? assignment.getSection().getBatch()
                : assignment.getBatch();
        Section section = assignment.getSection();
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
                .sectionId(section != null ? section.getId() : null)
                .sectionCode(section != null ? section.getSectionCode() : null)
                .sectionName(section != null ? section.getName() : null)
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