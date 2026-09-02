package com.dagacs.service;

import com.dagacs.dto.DepartmentDTO;
import com.dagacs.entity.Department;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Autowired
    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public DepartmentDTO saveDepartment(DepartmentDTO departmentDTO) {
        String name = departmentDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Department name is required", 400);
        }
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new AuthException("Department name already exists: " + name, 409);
        }
        Department department = Department.builder()
                .name(name)
                .code(departmentDTO.getCode())
                .description(departmentDTO.getDescription())
                .createdBy(departmentDTO.getCreatedBy() != null ? departmentDTO.getCreatedBy() : "SYSTEM")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    public List<DepartmentDTO> getAllDepartments() {
        return departmentRepository.findAllByOrderByName().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DepartmentDTO getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + id, 404));
        return convertToDTO(department);
    }

    public DepartmentDTO updateDepartment(Long id, DepartmentDTO departmentDTO) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + id, 404));

        String name = departmentDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Department name is required", 400);
        }

        if (!department.getName().equalsIgnoreCase(name) && departmentRepository.existsByNameIgnoreCase(name)) {
            throw new AuthException("Department name already exists: " + name, 409);
        }

        department.setName(name);
        department.setCode(departmentDTO.getCode());
        department.setDescription(departmentDTO.getDescription());
        department.setUpdatedAt(LocalDateTime.now());
        department = departmentRepository.save(department);
        return convertToDTO(department);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + id, 404));

        if (department.getPrograms() != null && !department.getPrograms().isEmpty()) {
            throw new AuthException("Cannot delete department. Program(s) exist: " +
                    department.getPrograms().stream()
                            .map(p -> p.getName())
                            .collect(Collectors.joining(", ")), 409);
        }

        departmentRepository.delete(department);
    }

    private DepartmentDTO convertToDTO(Department department) {
        return DepartmentDTO.builder()
                .id(department.getId())
                .name(department.getName())
                .code(department.getCode())
                .description(department.getDescription())
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }
}