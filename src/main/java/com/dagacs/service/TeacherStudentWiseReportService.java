package com.dagacs.service;

import com.dagacs.dto.StudentWiseCellDTO;
import com.dagacs.dto.StudentWiseColumnDTO;
import com.dagacs.dto.StudentWiseReportDTO;
import com.dagacs.dto.StudentWiseRowDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import com.dagacs.entity.Subject;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherStudentWiseReportRepository;
import com.dagacs.repository.TeacherStudentWiseReportRepository.StudentWiseRecordAggregation;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.dagacs.security.AuthenticatedTeacherResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only student-wise attendance matrix (enrollment-ordered register +
 * per-session cells).
 * <p>
 * <b>Locked request contract (M10B):</b> the academic teaching context is always
 * identified by {@code subjectId} AND exactly one of {@code sectionId} /
 * {@code batchId}. Both, neither, or a missing subjectId are rejected with 400,
 * nonexistent IDs are rejected with 404, a batch that structurally has sections
 * is rejected with 400 (Phase-2 batch-mode rule), and a context the
 * authenticated teacher is not assigned to is rejected with 403. Self-scope is
 * always derived from the JWT via {@link AuthenticatedTeacherResolver} (401 when
 * no active Teacher profile is linked) and the same assignment authorization as
 * the frozen M7.1 report path. Percentage uses the frozen M4 semantics (private
 * copy; M4 code is never modified).
 */
@Service
public class TeacherStudentWiseReportService {

    private final AuthenticatedTeacherResolver teacherResolver;
    private final TeacherStudentWiseReportRepository reportRepository;
    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final BatchRepository batchRepository;
    private final TeacherSubjectSectionAssignmentRepository assignmentRepository;

    public TeacherStudentWiseReportService(AuthenticatedTeacherResolver teacherResolver,
                                           TeacherStudentWiseReportRepository reportRepository,
                                           SubjectRepository subjectRepository,
                                           SectionRepository sectionRepository,
                                           BatchRepository batchRepository,
                                           TeacherSubjectSectionAssignmentRepository assignmentRepository) {
        this.teacherResolver = teacherResolver;
        this.reportRepository = reportRepository;
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
        this.batchRepository = batchRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public StudentWiseReportDTO getStudentWiseReport(LocalDate startDate, LocalDate endDate,
                                                     Long subjectId, Long sectionId, Long batchId) {
        if (subjectId == null) {
            throw new AuthException("Subject ID is required for the student-wise report", 400);
        }
        if (sectionId != null && batchId != null) {
            throw new AuthException("Provide exactly one of sectionId or batchId, not both.", 400);
        }
        if (sectionId == null && batchId == null) {
            throw new AuthException("Provide exactly one of sectionId or batchId.", 400);
        }

        Teacher teacher = teacherResolver.resolve();

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + subjectId, 404));

        // Exactly one of sectionId/batchId identifies the teaching target. The
        // assignment check keeps the report inside the teacher's own authorized
        // teaching scope (HOD access included) — an incompatible/unassigned
        // context is rejected, never silently emptied.
        if (sectionId != null) {
            sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new AuthException("Section not found with ID: " + sectionId, 404));
            if (!assignmentRepository.existsByTeacherIdAndSectionIdAndSubjectOfferingSubjectId(
                    teacher.getId(), sectionId, subjectId)) {
                throw new AuthException("Teacher is not assigned to this subject and section", 403);
            }
        } else {
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new AuthException("Batch not found with ID: " + batchId, 404));
            if (!sectionRepository.findByBatch(batch).isEmpty()) {
                throw new AuthException("Batch has sections; use sectionId instead of batchId.", 400);
            }
            if (!assignmentRepository.existsByTeacherIdAndBatchIdAndSubjectOfferingSubjectId(
                    teacher.getId(), batchId, subjectId)) {
                throw new AuthException("Teacher is not assigned to this subject and batch", 403);
            }
        }

        String start = toCanonicalString(startDate);
        String end = toCanonicalString(endDate);

        List<StudentWiseRecordAggregation> records = reportRepository
                .findStudentWiseByTeacherAndDateRange(teacher.getId(), start, end,
                        subjectId, sectionId, batchId);
        if (records.isEmpty()) {
            if (sectionId != null) {
                Section section = sectionRepository.findById(sectionId).orElse(null);
                return StudentWiseReportDTO.builder()
                        .subjectId(subjectId)
                        .subjectName(subject.getName())
                        .sectionId(sectionId)
                        .sectionName(section != null ? section.getName() : null)
                        .columns(List.of())
                        .rows(List.of())
                        .build();
            }
            Batch batch = batchRepository.findById(batchId).orElse(null);
            return StudentWiseReportDTO.builder()
                    .subjectId(subjectId)
                    .subjectName(subject.getName())
                    .batchId(batchId)
                    .batchCode(batch != null ? batch.getBatchCode() : null)
                    .columns(List.of())
                    .rows(List.of())
                    .build();
        }

        // Distinct (date, lecturePeriod) sessions in the query's own order.
        Map<Long, StudentWiseColumnDTO> columnsById = new LinkedHashMap<>();
        for (StudentWiseRecordAggregation r : records) {
            columnsById.computeIfAbsent(r.getSessionId(), id -> StudentWiseColumnDTO.builder()
                    .sessionId(id)
                    .date(r.getDate())
                    .lecturePeriod(r.getLecturePeriod())
                    .build());
        }
        List<StudentWiseColumnDTO> columns = new ArrayList<>(columnsById.values());

        // Rows grouped per student in the query's enrollment ASC order.
        Map<Long, StudentWiseRowDTO.StudentWiseRowDTOBuilder> rowsById = new LinkedHashMap<>();
        Map<Long, List<StudentWiseCellDTO>> cellsByStudent = new LinkedHashMap<>();
        Map<Long, Long> presentById = new LinkedHashMap<>();
        Map<Long, Long> totalById = new LinkedHashMap<>();

        StudentWiseReportDTO.StudentWiseReportDTOBuilder report = StudentWiseReportDTO.builder();

        for (StudentWiseRecordAggregation r : records) {
            Long studentId = r.getStudentId();
            rowsById.computeIfAbsent(studentId, id -> StudentWiseRowDTO.builder()
                    .studentId(id)
                    .rollNumber(r.getRollNumber())
                    .enrollmentNumber(r.getEnrollmentNumber())
                    .name(r.getStudentName()));

            cellsByStudent.computeIfAbsent(studentId, k -> new ArrayList<>()).add(
                    StudentWiseCellDTO.builder()
                            .sessionId(r.getSessionId())
                            .status(r.getStatus())
                            .isPresent(r.getIsPresent())
                            .build());
            totalById.merge(studentId, 1L, Long::sum);
            if (Boolean.TRUE.equals(r.getIsPresent())) {
                presentById.merge(studentId, 1L, Long::sum);
            }

            report.subjectId(r.getSubjectId());
            report.subjectName(r.getSubjectName());
            report.sectionId(r.getSectionId());
            report.sectionName(r.getSectionName());
            report.batchId(r.getBatchId());
            report.batchCode(r.getBatchCode());
        }

        List<StudentWiseRowDTO> rows = new ArrayList<>();
        for (Map.Entry<Long, StudentWiseRowDTO.StudentWiseRowDTOBuilder> entry : rowsById.entrySet()) {
            Long studentId = entry.getKey();
            long total = totalById.getOrDefault(studentId, 0L);
            long present = presentById.getOrDefault(studentId, 0L);
            StudentWiseRowDTO.StudentWiseRowDTOBuilder builder = entry.getValue();
            builder.presentCount(present)
                    .totalRecordedCount(total)
                    .percentage(computePercentage(present, total))
                    .cells(cellsByStudent.getOrDefault(studentId, List.of()));
            rows.add(builder.build());
        }

        return report.columns(columns).rows(rows).build();
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /**
     * Private duplication of the M4 percentage semantics — M4 code is not
     * modified and not extracted.
     */
    private static Double computePercentage(long presentCount, long totalRecorded) {
        if (totalRecorded <= 0) {
            return null;
        }
        return (double) presentCount / totalRecorded * 100.0;
    }
}