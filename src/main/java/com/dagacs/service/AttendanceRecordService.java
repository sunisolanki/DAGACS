package com.dagacs.service;

import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Subject;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentRepository;
import com.dagacs.repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceRecordService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final BatchRepository batchRepository;

    @Autowired
    public AttendanceRecordService(AttendanceRecordRepository attendanceRecordRepository,
                                   StudentRepository studentRepository,
                                   SubjectRepository subjectRepository,
                                   SectionRepository sectionRepository,
                                   BatchRepository batchRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public AttendanceRecordDTO saveAttendanceRecord(AttendanceRecordDTO dto) {
        if (dto.getStudentId() == null) {
            throw new AuthException("Student ID is required", 400);
        }
        if (dto.getSubjectId() == null) {
            throw new AuthException("Subject ID is required", 400);
        }
        if (dto.getSectionId() != null && dto.getBatchId() != null) {
            throw new AuthException("Provide exactly one of sectionId or batchId, not both.", 400);
        }
        if (dto.getSectionId() == null && dto.getBatchId() == null) {
            throw new AuthException("Provide exactly one of sectionId or batchId.", 400);
        }
        if (dto.getStatus() == null || dto.getStatus().trim().isEmpty()) {
            throw new AuthException("Status is required", 400);
        }
        if (dto.getDate() == null || dto.getDate().trim().isEmpty()) {
            throw new AuthException("Date is required", 400);
        }
        if (dto.getIsPresent() == null) {
            throw new AuthException("isPresent flag is required", 400);
        }

        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new AuthException("Student not found with ID: " + dto.getStudentId(), 404));
        Subject subject = subjectRepository.findById(dto.getSubjectId())
                .orElseThrow(() -> new AuthException("Subject not found with ID: " + dto.getSubjectId(), 404));

        Section section = null;
        Batch batch = null;
        if (dto.getSectionId() != null) {
            section = sectionRepository.findById(dto.getSectionId())
                    .orElseThrow(() -> new AuthException("Section not found with ID: " + dto.getSectionId(), 404));
            if (attendanceRecordRepository.existsByStudentIdAndSubjectIdAndSectionIdAndDate(
                    dto.getStudentId(), dto.getSubjectId(), dto.getSectionId(), dto.getDate())) {
                throw new AuthException("Attendance already recorded for this student, subject, section, and date", 409);
            }
        } else {
            batch = batchRepository.findById(dto.getBatchId())
                    .orElseThrow(() -> new AuthException("Batch not found with ID: " + dto.getBatchId(), 404));
            if (!sectionRepository.findByBatch(batch).isEmpty()) {
                throw new AuthException("Batch has sections; use sectionId instead of batchId.", 400);
            }
            if (attendanceRecordRepository.existsByStudentIdAndSubjectIdAndBatchIdAndDate(
                    dto.getStudentId(), dto.getSubjectId(), dto.getBatchId(), dto.getDate())) {
                throw new AuthException("Attendance already recorded for this student, subject, batch, and date", 409);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceRecord record = AttendanceRecord.builder()
                .student(student)
                .subject(subject)
                .section(section)
                .batch(batch)
                .status(dto.getStatus())
                .lecturePeriod(dto.getLecturePeriod() != null ? dto.getLecturePeriod() : "")
                .date(dto.getDate())
                .isPresent(dto.getIsPresent())
                .createdAt(now)
                .build();
        record = attendanceRecordRepository.save(record);
        return convertToDTO(record);
    }

    @Transactional(readOnly = true)
    public AttendanceRecordDTO getAttendanceRecordById(Long id) {
        AttendanceRecord record = attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new AuthException("Attendance record not found with ID: " + id, 404));
        return convertToDTO(record);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getAttendanceRecordsByStudent(Long studentId) {
        return attendanceRecordRepository.findByStudentId(studentId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getAttendanceRecordsBySubject(Long subjectId) {
        return attendanceRecordRepository.findBySubjectId(subjectId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getAttendanceRecordsBySection(Long sectionId) {
        return attendanceRecordRepository.findBySectionId(sectionId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private AttendanceRecordDTO convertToDTO(AttendanceRecord record) {
        Section section = record.getSection();
        return AttendanceRecordDTO.builder()
                .id(record.getId())
                .studentId(record.getStudent().getId())
                .subjectId(record.getSubject().getId())
                .sectionId(section != null ? section.getId() : null)
                .batchId(section == null ? record.getBatch().getId() : null)
                .status(record.getStatus())
                .lecturePeriod(record.getLecturePeriod())
                .date(record.getDate())
                .isPresent(record.getIsPresent())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
