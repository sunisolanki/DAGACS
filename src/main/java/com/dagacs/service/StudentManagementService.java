package com.dagacs.service;

import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentManagementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin student master-management service (M5.2).
 * <p>
 * Mirrors the existing master-data service pattern (AuthException with HTTP-like
 * statuses, constructor injection, transactional boundary around DTO mapping).
 * This service manages the {@code students} master-data table only; it never
 * provisions login accounts, never accepts client-supplied student identity for
 * the authenticated user, and uses only ACTIVE/INACTIVE statuses.
 * </p>
 */
@Service
public class StudentManagementService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final StudentManagementRepository studentRepository;
    private final ProgramRepository programRepository;
    private final BatchRepository batchRepository;
    private final SectionRepository sectionRepository;

    @Autowired
    public StudentManagementService(StudentManagementRepository studentRepository,
                                    ProgramRepository programRepository,
                                    BatchRepository batchRepository,
                                    SectionRepository sectionRepository) {
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.batchRepository = batchRepository;
        this.sectionRepository = sectionRepository;
    }

    @Transactional
    public StudentManagementDTO createStudent(StudentManagementRequestDTO request) {
        Student student = applyFields(new Student(), request);
        String status = normalizeStatus(request.getStatus());
        student.setStatus(status != null ? status : STATUS_ACTIVE);
        ensureUniqueOnCreate(request);

        LocalDateTime now = LocalDateTime.now();
        student.setCreatedAt(now);
        student.setUpdatedAt(now);
        student = studentRepository.save(student);
        return convertToDTO(student);
    }

    @Transactional(readOnly = true)
    public List<StudentManagementDTO> getAllStudents() {
        return studentRepository.findAllByOrderByNameAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StudentManagementDTO getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with ID: " + id, 404));
        return convertToDTO(student);
    }

    @Transactional
    public StudentManagementDTO updateStudent(Long id, StudentManagementRequestDTO request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with ID: " + id, 404));

        String originalRollNumber = student.getRollNumber();
        String originalEmail = student.getEmail();

        applyFields(student, request);
        String requestedStatus = normalizeStatus(request.getStatus());
        if (requestedStatus != null) {
            student.setStatus(requestedStatus);
        }
        ensureUniqueOnUpdate(student, request, originalRollNumber, originalEmail);

        student.setUpdatedAt(LocalDateTime.now());
        student = studentRepository.save(student);
        return convertToDTO(student);
    }

    @Transactional
    public StudentManagementDTO setStudentStatus(Long id, String rawStatus) {
        String status = normalizeStatus(rawStatus);
        if (status == null) {
            throw new AuthException("Status is required", 400);
        }

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with ID: " + id, 404));

        student.setStatus(status);
        student.setUpdatedAt(LocalDateTime.now());
        student = studentRepository.save(student);
        return convertToDTO(student);
    }

    private Student applyFields(Student student, StudentManagementRequestDTO request) {
        String rollNumber = trimToNull(request.getRollNumber());
        if (rollNumber == null) {
            throw new AuthException("Roll number is required", 400);
        }
        String name = trimToNull(request.getName());
        if (name == null) {
            throw new AuthException("Student name is required", 400);
        }

        student.setRollNumber(rollNumber);
        student.setName(name);
        student.setEmail(trimToNull(request.getEmail()));
        student.setGender(defaultValue(request.getGender()));
        student.setFatherName(defaultValue(request.getFatherName()));
        student.setMotherName(defaultValue(request.getMotherName()));
        student.setPhotoUrl(defaultValue(request.getPhotoUrl()));
        student.setEnrollmentNumber(defaultValue(request.getEnrollmentNumber()));
        student.setAge(request.getAge() != null ? request.getAge() : 0);
        student.setAdmissionDate(defaultValue(request.getAdmissionDate()));
        student.setProgram(resolveProgram(request.getProgramId()));
        student.setBatch(resolveBatch(request.getBatchId()));
        student.setSection(resolveSection(request.getSectionId()));
        return student;
    }

    /**
     * Approved M5.2 optional fields are nullable at the API boundary, but the
     * frozen {@link Student} entity maps them as NOT NULL. Mirroring the existing
     * {@code photoUrl} default already in this class (and the project's neutral
     * {@code ""} defaults for optional {@code User} columns), blank/null values
     * collapse to {@code ""} so a minimal M5.2 payload persists cleanly.
     */
    private String defaultValue(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : "";
    }

    private Program resolveProgram(Long programId) {
        if (programId == null) {
            throw new AuthException("Program is required", 400);
        }
        return programRepository.findById(programId)
                .orElseThrow(() -> new AuthException("Program not found with ID: " + programId, 404));
    }

    private Batch resolveBatch(Long batchId) {
        if (batchId == null) {
            throw new AuthException("Batch is required", 400);
        }
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new AuthException("Batch not found with ID: " + batchId, 404));
    }

    private Section resolveSection(Long sectionId) {
        if (sectionId == null) {
            throw new AuthException("Section is required", 400);
        }
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new AuthException("Section not found with ID: " + sectionId, 404));
    }

    private void ensureUniqueOnCreate(StudentManagementRequestDTO request) {
        String rollNumber = trimToNull(request.getRollNumber());
        if (studentRepository.existsByRollNumber(rollNumber)) {
            throw new AuthException("Roll number already exists: " + rollNumber, 409);
        }
        String email = trimToNull(request.getEmail());
        if (email != null && studentRepository.existsByEmail(email)) {
            throw new AuthException("Email already exists: " + email, 409);
        }
    }

    private void ensureUniqueOnUpdate(Student student, StudentManagementRequestDTO request,
                                      String originalRollNumber, String originalEmail) {
        String rollNumber = trimToNull(request.getRollNumber());
        if (!rollNumber.equals(originalRollNumber)
                && studentRepository.existsByRollNumber(rollNumber)) {
            throw new AuthException("Roll number already exists: " + rollNumber, 409);
        }
        String email = trimToNull(request.getEmail());
        String normalizedOriginalEmail = trimToNull(originalEmail);
        if (email != null && !email.equalsIgnoreCase(normalizedOriginalEmail)
                && studentRepository.existsByEmail(email)) {
            throw new AuthException("Email already exists: " + email, 409);
        }
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return null;
        }
        String status = rawStatus.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new AuthException("Status must be ACTIVE or INACTIVE", 400);
        }
        return status;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private StudentManagementDTO convertToDTO(Student student) {
        return StudentManagementDTO.builder()
                .id(student.getId())
                .rollNumber(student.getRollNumber())
                .email(student.getEmail())
                .name(student.getName())
                .gender(student.getGender())
                .fatherName(student.getFatherName())
                .motherName(student.getMotherName())
                .photoUrl(student.getPhotoUrl())
                .enrollmentNumber(student.getEnrollmentNumber())
                .age(student.getAge())
                .admissionDate(student.getAdmissionDate())
                .status(student.getStatus())
                .programId(student.getProgram().getId())
                .programName(student.getProgram().getName())
                .batchId(student.getBatch().getId())
                .batchName(student.getBatch().getName())
                .sectionId(student.getSection().getId())
                .sectionName(student.getSection().getName())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }
}