package com.dagacs.service;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.dto.BatchDTO;
import com.dagacs.dto.SectionDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final SectionRepository sectionRepository;
    private final StudentRepository studentRepository;
    private final TeacherSubjectSectionAssignmentRepository teacherSubjectSectionAssignmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    public BatchService(BatchRepository batchRepository,
                        AcademicSessionRepository academicSessionRepository,
                        SectionRepository sectionRepository,
                        StudentRepository studentRepository,
                        TeacherSubjectSectionAssignmentRepository teacherSubjectSectionAssignmentRepository,
                        AttendanceSessionRepository attendanceSessionRepository,
                        AttendanceRecordRepository attendanceRecordRepository) {
        this.batchRepository = batchRepository;
        this.academicSessionRepository = academicSessionRepository;
        this.sectionRepository = sectionRepository;
        this.studentRepository = studentRepository;
        this.teacherSubjectSectionAssignmentRepository = teacherSubjectSectionAssignmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public BatchDTO saveBatch(BatchDTO batchDTO) {
        String name = batchDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Batch name is required", 400);
        }

        String batchCode = batchDTO.getBatchCode();
        if (batchCode == null || batchCode.trim().isEmpty()) {
            throw new AuthException("Batch code is required", 400);
        }

        Long academicSessionId = batchDTO.getAcademicSessionId();
        if (academicSessionId == null) {
            throw new AuthException("AcademicSession is required", 400);
        }

        AcademicSession academicSession = academicSessionRepository.findById(academicSessionId)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + academicSessionId, 404));

        if (batchRepository.existsByBatchCode(batchCode)) {
            throw new AuthException("Batch code already exists: " + batchCode, 409);
        }
        if (batchRepository.existsByAcademicSessionAndName(academicSession, name)) {
            throw new AuthException("Batch already exists for this academic session: " + name, 409);
        }

        LocalDateTime now = LocalDateTime.now();
        Batch batch = Batch.builder()
                .batchCode(batchCode)
                .name(name)
                .year(batchDTO.getYear())
                .academicSession(academicSession)
                .program(academicSession.getProgram().getName())
                .maxCapacity(batchDTO.getMaxCapacity())
                .createdAt(now)
                .updatedAt(now)
                .build();
        batch = batchRepository.save(batch);
        return convertToDTO(batch, List.of());
    }

    @Transactional(readOnly = true)
    public List<BatchDTO> getAllBatches() {
        List<Batch> batches = batchRepository.findAllByOrderByName();
        Map<Long, List<Section>> sectionsByBatch = sectionRepository.findByBatchIn(batches).stream()
                .collect(Collectors.groupingBy(section -> section.getBatch().getId()));
        return batches.stream()
                .map(batch -> convertToDTO(batch,
                        sectionsByBatch.getOrDefault(batch.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchDTO getBatchById(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + id, 404));
        return convertToDTO(batch, sectionRepository.findByBatch(batch));
    }

    @Transactional
    public BatchDTO updateBatch(Long id, BatchDTO batchDTO) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + id, 404));

        String name = batchDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Batch name is required", 400);
        }

        String batchCode = batchDTO.getBatchCode();
        if (batchCode == null || batchCode.trim().isEmpty()) {
            throw new AuthException("Batch code is required", 400);
        }

        Long academicSessionId = batchDTO.getAcademicSessionId();
        if (academicSessionId == null) {
            throw new AuthException("AcademicSession is required", 400);
        }

        AcademicSession academicSession = academicSessionRepository.findById(academicSessionId)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + academicSessionId, 404));

        if (!batch.getBatchCode().equals(batchCode) && batchRepository.existsByBatchCode(batchCode)) {
            throw new AuthException("Batch code already exists: " + batchCode, 409);
        }
        if ((!batch.getName().equals(name) || !batch.getAcademicSession().getId().equals(academicSessionId))
                && batchRepository.existsByAcademicSessionAndName(academicSession, name)) {
            throw new AuthException("Batch already exists for this academic session: " + name, 409);
        }

        boolean programChanged = !batch.getAcademicSession().getProgram().getId()
                .equals(academicSession.getProgram().getId());
        if (programChanged && studentRepository.countByBatchId(batch.getId()) > 0) {
            throw new AuthException(
                    "Cannot move batch to a different program while students are assigned", 409);
        }

        boolean sessionChanged = !batch.getAcademicSession().getId().equals(academicSession.getId());
        if (sessionChanged) {
            boolean hasAssignments = teacherSubjectSectionAssignmentRepository
                    .existsBySectionBatchId(batch.getId())
                    || teacherSubjectSectionAssignmentRepository.existsByBatchId(batch.getId());
            boolean hasAttendanceSessions = attendanceSessionRepository
                    .existsBySectionEntityBatchId(batch.getId())
                    || attendanceSessionRepository.existsByBatchEntityId(batch.getId());
            if (hasAssignments || hasAttendanceSessions) {
                throw new AuthException(
                        "Cannot move batch to a different academic session while teacher assignments or attendance sessions exist",
                        409);
            }
        }

        batch.setBatchCode(batchCode);
        batch.setName(name);
        batch.setYear(batchDTO.getYear());
        batch.setAcademicSession(academicSession);
        batch.setProgram(academicSession.getProgram().getName());
        batch.setMaxCapacity(batchDTO.getMaxCapacity());
        batch.setUpdatedAt(LocalDateTime.now());
        batch = batchRepository.save(batch);
        return convertToDTO(batch, sectionRepository.findByBatch(batch));
    }

    @Transactional(readOnly = true)
    public List<BatchDTO> getBatchesBySession(Long academicSessionId) {
        AcademicSession academicSession = academicSessionRepository.findById(academicSessionId)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + academicSessionId, 404));
        List<Batch> batches = batchRepository.findByAcademicSession(academicSession);
        Map<Long, List<Section>> sectionsByBatch = sectionRepository.findByBatchIn(batches).stream()
                .collect(Collectors.groupingBy(section -> section.getBatch().getId()));
        return batches.stream()
                .map(batch -> convertToDTO(batch,
                        sectionsByBatch.getOrDefault(batch.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + id, 404));

        List<Section> sections = sectionRepository.findByBatch(batch);
        if (sections != null && !sections.isEmpty()) {
            throw new AuthException("Cannot delete batch. Section(s) exist: " +
                    sections.stream().map(Section::getName).collect(Collectors.joining(", ")), 409);
        }

        // Phase-2 batch-mode guards: a zero-section batch can still be the target
        // of batch-mode assignments, sessions, and attendance records.
        if (teacherSubjectSectionAssignmentRepository.existsByBatchId(id)) {
            throw new AuthException("Cannot delete batch while batch-level teacher assignments exist", 409);
        }
        if (attendanceSessionRepository.existsByBatchEntityId(id)) {
            throw new AuthException("Cannot delete batch while batch-level attendance sessions exist", 409);
        }
        if (attendanceRecordRepository.existsByBatchId(id)) {
            throw new AuthException("Cannot delete batch while batch-level attendance records exist", 409);
        }
        if (studentRepository.countByBatchId(id) > 0) {
            throw new AuthException("Cannot delete batch while students are assigned", 409);
        }

        batchRepository.delete(batch);
    }

    private BatchDTO convertToDTO(Batch batch, List<Section> sections) {
        return BatchDTO.builder()
                .id(batch.getId())
                .batchCode(batch.getBatchCode())
                .name(batch.getName())
                .year(batch.getYear())
                .academicSessionId(batch.getAcademicSession().getId())
                .academicSession(AcademicSessionDTO.builder()
                        .id(batch.getAcademicSession().getId())
                        .name(batch.getAcademicSession().getName())
                        .code(batch.getAcademicSession().getCode())
                        .build())
                .program(batch.getProgram())
                .maxCapacity(batch.getMaxCapacity())
                .sections(sections.stream()
                        .map(section -> SectionDTO.builder()
                                .id(section.getId())
                                .sectionCode(section.getSectionCode())
                                .name(section.getName())
                                .maxCapacity(section.getMaxCapacity())
                                .batchId(section.getBatch().getId())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}
