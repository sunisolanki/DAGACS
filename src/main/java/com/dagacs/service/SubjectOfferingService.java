package com.dagacs.service;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.dto.SemesterDTO;
import com.dagacs.dto.SubjectDTO;
import com.dagacs.dto.SubjectOfferingDTO;
import com.dagacs.dto.SubjectOfferingRequestDTO;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * M9.2 curriculum applicability layer: maps a reusable {@link Subject} master
 * to a {@link Semester}. The academic context is always derived relationally
 * (Semester -> AcademicSession -> Program -> Department) and is never accepted
 * from the client payload.
 */
@Service
public class SubjectOfferingService {

    private final SubjectOfferingRepository subjectOfferingRepository;
    private final SubjectRepository subjectRepository;
    private final SemesterRepository semesterRepository;
    private final TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    public SubjectOfferingService(SubjectOfferingRepository subjectOfferingRepository,
                                  SubjectRepository subjectRepository,
                                  SemesterRepository semesterRepository,
                                  TeacherSubjectSectionAssignmentRepository assignmentRepository) {
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.subjectRepository = subjectRepository;
        this.semesterRepository = semesterRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public SubjectOfferingDTO saveSubjectOffering(SubjectOfferingRequestDTO requestDTO) {
        if (requestDTO.getSubjectId() == null) {
            throw new AuthException("Subject is required", 400);
        }
        if (requestDTO.getSemesterId() == null) {
            throw new AuthException("Semester is required", 400);
        }
        Subject subject = resolveSubject(requestDTO.getSubjectId());
        Semester semester = resolveSemester(requestDTO.getSemesterId());

        if (subjectOfferingRepository.existsBySubjectAndSemester(subject, semester)) {
            throw new AuthException("Subject is already mapped to this semester", 409);
        }

        LocalDateTime now = LocalDateTime.now();
        SubjectOffering offering = SubjectOffering.builder()
                .subject(subject)
                .semester(semester)
                .createdAt(now)
                .updatedAt(now)
                .build();
        offering = subjectOfferingRepository.save(offering);
        return convertToDTO(offering);
    }

    @Transactional(readOnly = true)
    public List<SubjectOfferingDTO> getAllSubjectOfferings() {
        return subjectOfferingRepository.findAllByOrderByIdAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubjectOfferingDTO getSubjectOfferingById(Long id) {
        SubjectOffering offering = subjectOfferingRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + id, 404));
        return convertToDTO(offering);
    }

    @Transactional
    public SubjectOfferingDTO updateSubjectOffering(Long id, SubjectOfferingRequestDTO requestDTO) {
        SubjectOffering offering = subjectOfferingRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + id, 404));

        Subject subject = resolveSubject(requestDTO.getSubjectId());
        Semester semester = resolveSemester(requestDTO.getSemesterId());

        boolean moved = !offering.getSubject().getId().equals(requestDTO.getSubjectId())
                || !offering.getSemester().getId().equals(requestDTO.getSemesterId());
        if (moved && subjectOfferingRepository.existsBySubjectAndSemester(subject, semester)) {
            throw new AuthException("Subject is already mapped to this semester", 409);
        }

        offering.setSubject(subject);
        offering.setSemester(semester);
        offering.setUpdatedAt(LocalDateTime.now());
        offering = subjectOfferingRepository.save(offering);
        return convertToDTO(offering);
    }

    @Transactional
    public void deleteSubjectOffering(Long id) {
        SubjectOffering offering = subjectOfferingRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject offering not found with ID: " + id, 404));
        if (assignmentRepository.existsBySubjectOfferingId(id)) {
            throw new AuthException(
                    "SubjectOffering cannot be deleted because teacher assignments exist for it", 409);
        }
        subjectOfferingRepository.delete(offering);
    }

    private Subject resolveSubject(Long subjectId) {
        if (subjectId == null) {
            throw new AuthException("Subject is required", 400);
        }
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + subjectId, 404));
    }

    private Semester resolveSemester(Long semesterId) {
        if (semesterId == null) {
            throw new AuthException("Semester is required", 400);
        }
        return semesterRepository.findById(semesterId)
                .orElseThrow(() -> new AuthException("Semester not found with ID: " + semesterId, 404));
    }

    private SubjectOfferingDTO convertToDTO(SubjectOffering offering) {
        Subject subject = offering.getSubject();
        Semester semester = offering.getSemester();
        return SubjectOfferingDTO.builder()
                .id(offering.getId())
                .subjectId(subject.getId())
                .subject(SubjectDTO.builder()
                        .id(subject.getId())
                        .code(subject.getCode())
                        .name(subject.getName())
                        .build())
                .semesterId(semester.getId())
                .semester(SemesterDTO.builder()
                        .id(semester.getId())
                        .name(semester.getName())
                        .code(semester.getCode())
                        .academicSessionId(semester.getAcademicSession() == null
                                ? null
                                : semester.getAcademicSession().getId())
                        .academicSession(semester.getAcademicSession() == null
                                ? null
                                : AcademicSessionDTO.builder()
                                        .id(semester.getAcademicSession().getId())
                                        .name(semester.getAcademicSession().getName())
                                        .code(semester.getAcademicSession().getCode())
                                        .build())
                        .build())
                .createdAt(offering.getCreatedAt())
                .updatedAt(offering.getUpdatedAt())
                .build();
    }
}