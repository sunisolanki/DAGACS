package com.dagacs.service;

import com.dagacs.dto.StudentProfileDTO;
import com.dagacs.entity.Student;
import com.dagacs.security.AuthenticatedStudentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service exposing the authenticated student's own academic profile.
 * <p>
 * Identity always comes from the JWT security context via
 * {@link AuthenticatedStudentResolver}; the client can never request a profile
 * for another student by supplying a studentId.
 * </p>
 */
@Service
public class StudentProfileService {

    private final AuthenticatedStudentResolver studentResolver;

    public StudentProfileService(AuthenticatedStudentResolver studentResolver) {
        this.studentResolver = studentResolver;
    }

    @Transactional(readOnly = true)
    public StudentProfileDTO getMyProfile() {
        Student student = studentResolver.resolve();
        return StudentProfileDTO.builder()
                .rollNumber(student.getRollNumber())
                .enrollmentNumber(student.getEnrollmentNumber())
                .email(student.getEmail())
                .name(student.getName())
                .gender(student.getGender())
                .fatherName(student.getFatherName())
                .motherName(student.getMotherName())
                .photoUrl(student.getPhotoUrl())
                .age(student.getAge())
                .admissionDate(student.getAdmissionDate())
                .status(student.getStatus())
                .batchName(student.getBatch().getName())
                .programName(student.getProgram().getName())
                .sectionName(student.getSection().getName())
                .build();
    }
}