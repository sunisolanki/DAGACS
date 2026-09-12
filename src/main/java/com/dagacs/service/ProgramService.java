package com.dagacs.service;

import com.dagacs.dto.DepartmentDTO;
import com.dagacs.dto.ProgramDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final AcademicSessionRepository academicSessionRepository;

    @Autowired
    public ProgramService(ProgramRepository programRepository,
                          DepartmentRepository departmentRepository,
                          AcademicSessionRepository academicSessionRepository) {
        this.programRepository = programRepository;
        this.departmentRepository = departmentRepository;
        this.academicSessionRepository = academicSessionRepository;
    }

    public ProgramDTO saveProgram(ProgramDTO programDTO) {
        String name = programDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Program name is required", 400);
        }

        Long departmentId = programDTO.getDepartmentId();
        if (departmentId == null) {
            throw new AuthException("Department is required", 400);
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + departmentId, 404));

        if (programRepository.existsByNameAndDepartment(name, department)) {
            throw new AuthException("Program name already exists in this department: " + name, 409);
        }

        Program program = Program.builder()
                .name(name)
                .code(programDTO.getCode())
                .duration(programDTO.getDuration())
                .description(programDTO.getDescription() == null ? "" : programDTO.getDescription())
                .department(department)
                .build();
        program = programRepository.save(program);
        return convertToDTO(program);
    }

    @Transactional(readOnly = true)
    public List<ProgramDTO> getAllPrograms() {
        return programRepository.findAllByOrderByName().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProgramDTO getProgramById(Long id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new AuthException("Program not found with ID: " + id, 404));
        return convertToDTO(program);
    }

    public ProgramDTO updateProgram(Long id, ProgramDTO programDTO) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new AuthException("Program not found with ID: " + id, 404));

        Long departmentId = programDTO.getDepartmentId();
        if (departmentId == null) {
            throw new AuthException("Department is required", 400);
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + departmentId, 404));

        boolean departmentChanged = !program.getDepartment().getId().equals(departmentId);
        if (departmentChanged && academicSessionRepository.existsByProgramId(program.getId())) {
            throw new AuthException(
                    "Cannot move program to a different department while academic sessions exist", 409);
        }

        program.setName(programDTO.getName());
        program.setCode(programDTO.getCode());
        program.setDuration(programDTO.getDuration());
        program.setDescription(programDTO.getDescription() == null ? "" : programDTO.getDescription());
        program.setDepartment(department);
        program = programRepository.save(program);
        return convertToDTO(program);
    }

    @Transactional
    public void deleteProgram(Long id) {
        if (!programRepository.existsById(id)) {
            throw new AuthException("Program not found with ID: " + id, 404);
        }

        Program program = programRepository.findById(id).orElseThrow();

        List<AcademicSession> sessions = academicSessionRepository.findByProgram(program);
        if (sessions != null && !sessions.isEmpty()) {
            throw new AuthException("Cannot delete program. Academic session(s) exist: " +
                    sessions.stream().map(AcademicSession::getName).collect(Collectors.joining(", ")), 409);
        }

        programRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ProgramDTO> getProgramsByDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + departmentId, 404));
        return programRepository.findByDepartment(department).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private ProgramDTO convertToDTO(Program program) {
        return ProgramDTO.builder()
                .id(program.getId())
                .name(program.getName())
                .code(program.getCode())
                .duration(program.getDuration())
                .description(program.getDescription())
                .department(DepartmentDTO.builder()
                        .id(program.getDepartment().getId())
                        .name(program.getDepartment().getName())
                        .code(program.getDepartment().getCode())
                        .build())
                .build();
    }
}