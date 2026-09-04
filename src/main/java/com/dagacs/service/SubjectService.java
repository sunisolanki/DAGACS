package com.dagacs.service;

import com.dagacs.dto.SubjectDTO;
import com.dagacs.entity.Section;
import com.dagacs.entity.Subject;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;

    @Autowired
    public SubjectService(SubjectRepository subjectRepository,
                          SectionRepository sectionRepository) {
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
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

        if (subjectRepository.existsByCode(code)) {
            throw new AuthException("Subject code already exists: " + code, 409);
        }

        LocalDateTime now = LocalDateTime.now();
        Subject subject = Subject.builder()
                .code(code)
                .name(name)
                .description(subjectDTO.getDescription() == null ? "" : subjectDTO.getDescription())
                .creditHours(subjectDTO.getCreditHours())
                .department(subjectDTO.getDepartment())
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

        if (!subject.getCode().equals(code) && subjectRepository.existsByCodeAndIdNot(code, id)) {
            throw new AuthException("Subject code already exists: " + code, 409);
        }

        subject.setCode(code);
        subject.setName(name);
        subject.setDescription(subjectDTO.getDescription() == null ? "" : subjectDTO.getDescription());
        subject.setCreditHours(subjectDTO.getCreditHours());
        subject.setDepartment(subjectDTO.getDepartment());
        subject.setStatus(subjectDTO.getStatus());
        subject.setUpdatedAt(LocalDateTime.now());
        subject = subjectRepository.save(subject);
        return convertToDTO(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + id, 404));

        List<Section> sections = sectionRepository.findBySubject(subject);
        if (sections != null && !sections.isEmpty()) {
            throw new AuthException("Cannot delete subject. Section(s) reference it: " +
                    sections.stream().map(Section::getName).collect(Collectors.joining(", ")), 409);
        }

        subjectRepository.delete(subject);
    }

    private SubjectDTO convertToDTO(Subject subject) {
        return SubjectDTO.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .description(subject.getDescription())
                .creditHours(subject.getCreditHours())
                .department(subject.getDepartment())
                .status(subject.getStatus())
                .createdAt(subject.getCreatedAt())
                .updatedAt(subject.getUpdatedAt())
                .build();
    }
}
