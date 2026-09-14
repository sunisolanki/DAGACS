package com.dagacs.service;

import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.entity.AttendanceRecord;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only attendance service for students.
 * <p>
 * The authenticated student is resolved exclusively from the JWT security context via
 * {@link AuthenticatedStudentResolver}. Attendance is queried only for that student, so a
 * client can never read another student's records by supplying a studentId in the request.
 * </p>
 */
@Service
public class StudentAttendanceService {

    private final AuthenticatedStudentResolver studentResolver;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public StudentAttendanceService(AuthenticatedStudentResolver studentResolver,
                                    AttendanceRecordRepository attendanceRecordRepository) {
        this.studentResolver = studentResolver;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDTO> getMyAttendance() {
        Student student = studentResolver.resolve();
        return attendanceRecordRepository.findByStudentId(student.getId()).stream()
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
                .sessionId(record.getSession().getId())
                .markedById(record.getMarkedBy().getId())
                .markedByName(record.getMarkedBy().getFullName())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
