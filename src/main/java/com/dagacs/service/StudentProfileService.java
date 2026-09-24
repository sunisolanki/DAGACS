package com.dagacs.service;

import com.dagacs.dto.StudentProfileDTO;
import com.dagacs.dto.StudentProfileRequestDTO;
import com.dagacs.entity.Student;
import com.dagacs.repository.StudentRepository;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Student profile API (M5.1).
 * <p>
 * Identity always comes from the JWT security context via
 * {@link AuthenticatedStudentResolver}; the client can never request a profile
 * for another student by supplying a studentId.
 * </p>
 */
@Service
public class StudentProfileService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private final AuthenticatedStudentResolver studentResolver;
    private final StudentRepository studentRepository;

    public StudentProfileService(AuthenticatedStudentResolver studentResolver,
                                 StudentRepository studentRepository) {
        this.studentResolver = studentResolver;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public StudentProfileDTO getMyProfile() {
        Student student = studentResolver.resolve();
        return StudentProfileDTO.builder()
                .rollNumber(student.getRollNumber())
                .enrollmentNumber(student.getEnrollmentNumber())
                .personalEmail(student.getPersonalEmail())
                .name(student.getName())
                .gender(student.getGender())
                .fatherName(student.getFatherName())
                .motherName(student.getMotherName())
                .photoUrl(student.getPhotoUrl())
                .age(student.getAge())
                .admissionDate(student.getAdmissionDate())
                .status(student.getStatus())
                .batchName(student.getBatch() != null ? student.getBatch().getName() : null)
                .programName(student.getProgram().getName())
                .sectionName(student.getSection() != null ? student.getSection().getName() : null)
                .academicSessionName(student.getAcademicSession() != null ? student.getAcademicSession().getName() : null)
                .build();
    }

    @Transactional
    public StudentProfileDTO updateMyProfile(StudentProfileRequestDTO request) {
        Student student = studentResolver.resolve();
        if (request.getFatherName() != null) {
            student.setFatherName(request.getFatherName().trim());
        }
        if (request.getMotherName() != null) {
            student.setMotherName(request.getMotherName().trim());
        }
        if (request.getGender() != null) {
            student.setGender(request.getGender().trim());
        }
        if (request.getPersonalEmail() != null) {
            String email = request.getPersonalEmail().trim();
            if (email.isEmpty()) {
                student.setPersonalEmail(null);
            } else if (EMAIL_PATTERN.matcher(email).matches()) {
                student.setPersonalEmail(email);
            }
        }
        student.setUpdatedAt(LocalDateTime.now());
        student = studentRepository.save(student);
        return getMyProfile();
    }
}

