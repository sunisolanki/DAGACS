package com.dagacs.service;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.dto.ProgramDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Program;
import com.dagacs.entity.Semester;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SemesterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AcademicSessionService {

    private final AcademicSessionRepository academicSessionRepository;
    private final ProgramRepository programRepository;
    private final SemesterRepository semesterRepository;
    private final BatchRepository batchRepository;

    @Autowired
    public AcademicSessionService(AcademicSessionRepository academicSessionRepository,
                                  ProgramRepository programRepository,
                                  SemesterRepository semesterRepository,
                                  BatchRepository batchRepository) {
        this.academicSessionRepository = academicSessionRepository;
        this.programRepository = programRepository;
        this.semesterRepository = semesterRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public AcademicSessionDTO saveSession(AcademicSessionDTO sessionDTO) {
        String name = sessionDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Session name is required", 400);
        }

        String code = sessionDTO.getCode();
        if (code == null || code.trim().isEmpty()) {
            throw new AuthException("Session code is required", 400);
        }

        Long programId = sessionDTO.getProgramId();
        if (programId == null) {
            throw new AuthException("Program is required", 400);
        }

        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new AuthException("Program not found with ID: " + programId, 404));

        if (academicSessionRepository.existsByNameAndProgram(name, program)) {
            throw new AuthException("Academic session already exists for this program: " + name, 409);
        }

        LocalDateTime now = LocalDateTime.now();
        AcademicSession session = AcademicSession.builder()
                .name(name)
                .code(code)
                .program(program)
                .description(sessionDTO.getDescription())
                .createdAt(now)
                .updatedAt(now)
                .build();
        session = academicSessionRepository.save(session);
        return convertToDTO(session);
    }

    @Transactional(readOnly = true)
    public List<AcademicSessionDTO> getAllSessions() {
        return academicSessionRepository.findAllByOrderByName().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AcademicSessionDTO getSessionById(Long id) {
        AcademicSession session = academicSessionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + id, 404));
        return convertToDTO(session);
    }

    @Transactional
    public AcademicSessionDTO updateSession(Long id, AcademicSessionDTO sessionDTO) {
        AcademicSession session = academicSessionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + id, 404));

        String name = sessionDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Session name is required", 400);
        }

        String code = sessionDTO.getCode();
        if (code == null || code.trim().isEmpty()) {
            throw new AuthException("Session code is required", 400);
        }

        Long programId = sessionDTO.getProgramId();
        if (programId == null) {
            throw new AuthException("Program is required", 400);
        }

        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new AuthException("Program not found with ID: " + programId, 404));

        if ((!session.getName().equals(name) || !session.getProgram().getId().equals(programId))
                && academicSessionRepository.existsByNameAndProgram(name, program)) {
            throw new AuthException("Academic session already exists for this program: " + name, 409);
        }

        session.setName(name);
        session.setCode(code);
        session.setProgram(program);
        session.setDescription(sessionDTO.getDescription());
        session.setUpdatedAt(LocalDateTime.now());
        session = academicSessionRepository.save(session);
        return convertToDTO(session);
    }

    @Transactional(readOnly = true)
    public List<AcademicSessionDTO> getSessionsByProgram(Long programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new AuthException("Program not found with ID: " + programId, 404));
        return academicSessionRepository.findByProgram(program).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(Long id) {
        AcademicSession session = academicSessionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Academic session not found with ID: " + id, 404));

        List<Semester> semesters = semesterRepository.findByAcademicSession(session);
        if (semesters != null && !semesters.isEmpty()) {
            throw new AuthException("Cannot delete academic session. Semester(s) exist: " +
                    semesters.stream().map(Semester::getName).collect(Collectors.joining(", ")), 409);
        }

        if (batchRepository.findByAcademicSession(session) != null
                && !batchRepository.findByAcademicSession(session).isEmpty()) {
            throw new AuthException("Cannot delete academic session. Batch(es) exist for this session.", 409);
        }

        academicSessionRepository.delete(session);
    }

    private AcademicSessionDTO convertToDTO(AcademicSession session) {
        return AcademicSessionDTO.builder()
                .id(session.getId())
                .name(session.getName())
                .code(session.getCode())
                .description(session.getDescription())
                .programId(session.getProgram().getId())
                .program(ProgramDTO.builder()
                        .id(session.getProgram().getId())
                        .name(session.getProgram().getName())
                        .code(session.getProgram().getCode())
                        .build())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
