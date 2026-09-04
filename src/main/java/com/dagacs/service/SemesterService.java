package com.dagacs.service;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.dto.SemesterDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Semester;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.SemesterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final AcademicSessionRepository academicSessionRepository;

    @Autowired
    public SemesterService(SemesterRepository semesterRepository,
                           AcademicSessionRepository academicSessionRepository) {
        this.semesterRepository = semesterRepository;
        this.academicSessionRepository = academicSessionRepository;
    }

    @Transactional
    public SemesterDTO saveSemester(SemesterDTO semesterDTO) {
        String name = semesterDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Semester name is required", 400);
        }

        Long academicSessionId = semesterDTO.getAcademicSessionId();
        if (academicSessionId == null) {
            throw new AuthException("AcademicSession is required", 400);
        }

        AcademicSession academicSession = academicSessionRepository.findById(academicSessionId)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + academicSessionId, 404));

        if (semesterRepository.existsByNameAndAcademicSession(name, academicSession)) {
            throw new AuthException("Semester already exists for this academic session: " + name, 409);
        }

        LocalDateTime now = LocalDateTime.now();
        Semester semester = Semester.builder()
                .name(name)
                .code(semesterDTO.getCode())
                .year(semesterDTO.getYear())
                .academicSession(academicSession)
                .createdAt(now)
                .updatedAt(now)
                .build();
        semester = semesterRepository.save(semester);
        return convertToDTO(semester);
    }

    @Transactional(readOnly = true)
    public List<SemesterDTO> getAllSemesters() {
        return semesterRepository.findAllByOrderByName().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SemesterDTO getSemesterById(Long id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new AuthException("Semester not found with ID: " + id, 404));
        return convertToDTO(semester);
    }

    @Transactional
    public SemesterDTO updateSemester(Long id, SemesterDTO semesterDTO) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new AuthException("Semester not found with ID: " + id, 404));

        String name = semesterDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Semester name is required", 400);
        }

        Long academicSessionId = semesterDTO.getAcademicSessionId();
        if (academicSessionId == null) {
            throw new AuthException("AcademicSession is required", 400);
        }

        AcademicSession academicSession = academicSessionRepository.findById(academicSessionId)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + academicSessionId, 404));

        if ((!semester.getName().equals(name) || !semester.getAcademicSession().getId().equals(academicSessionId))
                && semesterRepository.existsByNameAndAcademicSession(name, academicSession)) {
            throw new AuthException("Semester already exists for this academic session: " + name, 409);
        }

        semester.setName(name);
        semester.setCode(semesterDTO.getCode());
        semester.setYear(semesterDTO.getYear());
        semester.setAcademicSession(academicSession);
        semester.setUpdatedAt(LocalDateTime.now());
        semester = semesterRepository.save(semester);
        return convertToDTO(semester);
    }

    @Transactional(readOnly = true)
    public List<SemesterDTO> getSemestersBySession(Long academicSessionId) {
        AcademicSession academicSession = academicSessionRepository.findById(academicSessionId)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + academicSessionId, 404));
        return semesterRepository.findByAcademicSession(academicSession).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSemester(Long id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new AuthException("Semester not found with ID: " + id, 404));
        semesterRepository.delete(semester);
    }

    private SemesterDTO convertToDTO(Semester semester) {
        return SemesterDTO.builder()
                .id(semester.getId())
                .name(semester.getName())
                .code(semester.getCode())
                .year(semester.getYear())
                .academicSessionId(semester.getAcademicSession().getId())
                .academicSession(AcademicSessionDTO.builder()
                        .id(semester.getAcademicSession().getId())
                        .name(semester.getAcademicSession().getName())
                        .code(semester.getAcademicSession().getCode())
                        .build())
                .createdAt(semester.getCreatedAt())
                .updatedAt(semester.getUpdatedAt())
                .build();
    }
}
