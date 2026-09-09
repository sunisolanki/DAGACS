package com.dagacs.service;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.dto.BatchDTO;
import com.dagacs.dto.SectionDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SectionService {

    private final SectionRepository sectionRepository;
    private final BatchRepository batchRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    public SectionService(SectionRepository sectionRepository,
                          BatchRepository batchRepository,
                          AttendanceSessionRepository attendanceSessionRepository,
                          AttendanceRecordRepository attendanceRecordRepository) {
        this.sectionRepository = sectionRepository;
        this.batchRepository = batchRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public SectionDTO saveSection(SectionDTO sectionDTO) {
        String name = sectionDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Section name is required", 400);
        }

        String sectionCode = sectionDTO.getSectionCode();
        if (sectionCode == null || sectionCode.trim().isEmpty()) {
            throw new AuthException("Section code is required", 400);
        }

        Long batchId = sectionDTO.getBatchId();
        if (batchId == null) {
            throw new AuthException("Batch is required", 400);
        }

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + batchId, 404));

        if (sectionRepository.existsBySectionCode(sectionCode)) {
            throw new AuthException("Section code already exists: " + sectionCode, 409);
        }
        if (sectionRepository.existsByNameAndBatch(name, batch)) {
            throw new AuthException("Section already exists in this batch: " + name, 409);
        }

        LocalDateTime now = LocalDateTime.now();
        Section section = Section.builder()
                .sectionCode(sectionCode)
                .name(name)
                .maxCapacity(sectionDTO.getMaxCapacity())
                .batch(batch)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        section = sectionRepository.save(section);
        return convertToDTO(section);
    }

    @Transactional(readOnly = true)
    public List<SectionDTO> getAllSections() {
        return sectionRepository.findAllByOrderByName().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SectionDTO getSectionById(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Section not found with ID: " + id, 404));
        return convertToDTO(section);
    }

    @Transactional
    public SectionDTO updateSection(Long id, SectionDTO sectionDTO) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Section not found with ID: " + id, 404));

        String name = sectionDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new AuthException("Section name is required", 400);
        }

        String sectionCode = sectionDTO.getSectionCode();
        if (sectionCode == null || sectionCode.trim().isEmpty()) {
            throw new AuthException("Section code is required", 400);
        }

        Long batchId = sectionDTO.getBatchId();
        if (batchId == null) {
            throw new AuthException("Batch is required", 400);
        }

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + batchId, 404));

        if (!section.getSectionCode().equals(sectionCode) && sectionRepository.existsBySectionCode(sectionCode)) {
            throw new AuthException("Section code already exists: " + sectionCode, 409);
        }
        if ((!section.getName().equals(name) || !section.getBatch().getId().equals(batchId))
                && sectionRepository.existsByNameAndBatch(name, batch)) {
            throw new AuthException("Section already exists in this batch: " + name, 409);
        }

        section.setSectionCode(sectionCode);
        section.setName(name);
        section.setMaxCapacity(sectionDTO.getMaxCapacity());
        section.setBatch(batch);
        section.setUpdatedAt(LocalDateTime.now());
        section = sectionRepository.save(section);
        return convertToDTO(section);
    }

    @Transactional(readOnly = true)
    public List<SectionDTO> getSectionsByBatch(Long batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + batchId, 404));
        return sectionRepository.findByBatch(batch).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSection(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new AuthException("Section not found with ID: " + id, 404));

        if (attendanceRecordRepository.existsBySectionId(id)) {
            throw new AuthException("Cannot delete this section because it is referenced by attendance records.", 409);
        }
        if (attendanceSessionRepository.existsBySectionEntityId(id)) {
            throw new AuthException("Cannot delete this section because it is referenced by attendance sessions.", 409);
        }

        sectionRepository.delete(section);
    }

    private SectionDTO convertToDTO(Section section) {
        return SectionDTO.builder()
                .id(section.getId())
                .sectionCode(section.getSectionCode())
                .name(section.getName())
                .maxCapacity(section.getMaxCapacity())
                .batchId(section.getBatch().getId())
                .batch(BatchDTO.builder()
                        .id(section.getBatch().getId())
                        .batchCode(section.getBatch().getBatchCode())
                        .name(section.getBatch().getName())
                        .academicSessionId(section.getBatch().getAcademicSession().getId())
                        .academicSession(AcademicSessionDTO.builder()
                                .id(section.getBatch().getAcademicSession().getId())
                                .name(section.getBatch().getAcademicSession().getName())
                                .code(section.getBatch().getAcademicSession().getCode())
                                .build())
                        .build())
                .createdAt(section.getCreatedAt())
                .updatedAt(section.getUpdatedAt())
                .build();
    }
}
