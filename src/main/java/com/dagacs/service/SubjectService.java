package com.dagacs.service;

import com.dagacs.dto.DepartmentDTO;
import com.dagacs.dto.SubjectDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Subject;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubjectService {

    private static final String CREDIT_HOURS_PATTERN = "\\d+(\\.\\d+)?";

    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    public SubjectService(SubjectRepository subjectRepository,
                          DepartmentRepository departmentRepository,
                          AttendanceSessionRepository attendanceSessionRepository,
                          AttendanceRecordRepository attendanceRecordRepository) {
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public SubjectDTO saveSubject(SubjectDTO subjectDTO) {
        String code = subjectDTO.getCode();
        if (code == null || code.trim().isEmpty()) {
            throw new AuthException("Subject code is required", 400);
        }

        String name = subjectDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Subject name is required", 400);
        }

        validateCreditHours(subjectDTO.getCreditHours());

        Department department = resolveDepartment(subjectDTO.getDepartmentId());

        if (subjectRepository.existsByCode(code)) {
            throw new AuthException("Subject code already exists: " + code, 409);
        }

        LocalDateTime now = LocalDateTime.now();
        Subject subject = Subject.builder()
                .code(code)
                .name(name)
                .description(subjectDTO.getDescription() == null ? "" : subjectDTO.getDescription())
                .creditHours(subjectDTO.getCreditHours())
                .department(department)
                .status(subjectDTO.getStatus())
                .createdAt(now)
                .updatedAt(now)
                .build();
        subject = subjectRepository.save(subject);
        return convertToDTO(subject);
    }

    @Transactional(readOnly = true)
    public List<SubjectDTO> getAllSubjects() {
        return subjectRepository.findAllByOrderByName().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubjectDTO getSubjectById(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + id, 404));
        return convertToDTO(subject);
    }

    @Transactional
    public SubjectDTO updateSubject(Long id, SubjectDTO subjectDTO) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + id, 404));

        String code = subjectDTO.getCode();
        if (code == null || code.trim().isEmpty()) {
            throw new AuthException("Subject code is required", 400);
        }

        String name = subjectDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Subject name is required", 400);
        }

        validateCreditHours(subjectDTO.getCreditHours());

        Department department = resolveDepartment(subjectDTO.getDepartmentId());

        if (!subject.getCode().equals(code) && subjectRepository.existsByCodeAndIdNot(code, id)) {
            throw new AuthException("Subject code already exists: " + code, 409);
        }

        subject.setCode(code);
        subject.setName(name);
        subject.setDescription(subjectDTO.getDescription() == null ? "" : subjectDTO.getDescription());
        subject.setCreditHours(subjectDTO.getCreditHours());
        subject.setDepartment(department);
        subject.setStatus(subjectDTO.getStatus());
        subject.setUpdatedAt(LocalDateTime.now());
        subject = subjectRepository.save(subject);
        return convertToDTO(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + id, 404));

        if (attendanceRecordRepository.existsBySubjectId(id)) {
            throw new AuthException("Cannot delete this subject because it is referenced by attendance records.", 409);
        }
        if (attendanceSessionRepository.existsBySubjectEntityId(id)) {
            throw new AuthException("Cannot delete this subject because it is referenced by attendance sessions.", 409);
        }

        subjectRepository.delete(subject);
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            throw new AuthException("Department is required", 400);
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + departmentId, 404));
    }

    private void validateCreditHours(String creditHours) {
        if (creditHours == null || creditHours.trim().isEmpty()) {
            throw new AuthException("Credit hours is required", 400);
        }
        if (!creditHours.trim().matches(CREDIT_HOURS_PATTERN)) {
            throw new AuthException("Credit hours must be a positive number", 400);
        }
        if (Double.parseDouble(creditHours.trim()) <= 0) {
            throw new AuthException("Credit hours must be a positive number", 400);
        }
    }

    private DepartmentDTO toDepartmentDTO(Department department) {
        if (department == null) {
            return null;
        }
        return DepartmentDTO.builder()
                .id(department.getId())
                .name(department.getName())
                .code(department.getCode())
                .build();
    }

    private SubjectDTO convertToDTO(Subject subject) {
        Department department = subject.getDepartment();
        return SubjectDTO.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .description(subject.getDescription())
                .creditHours(subject.getCreditHours())
                .departmentId(department == null ? null : department.getId())
                .department(toDepartmentDTO(department))
                .status(subject.getStatus())
                .createdAt(subject.getCreatedAt())
                .updatedAt(subject.getUpdatedAt())
                .build();
    }
}
