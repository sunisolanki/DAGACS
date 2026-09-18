package com.dagacs.service;

import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudentManagementService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String TEMPORARY_PASSWORD = "Dagacs@123";

    private final StudentManagementRepository studentRepository;
    private final ProgramRepository programRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final BatchRepository batchRepository;
    private final SectionRepository sectionRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final AccountProvisioningService accountProvisioningService;

    @Autowired
    public StudentManagementService(StudentManagementRepository studentRepository,
                                     ProgramRepository programRepository,
                                     AcademicSessionRepository academicSessionRepository,
                                     BatchRepository batchRepository,
                                     SectionRepository sectionRepository,
                                     TeacherRepository teacherRepository,
                                     UserRepository userRepository,
                                     AccountProvisioningService accountProvisioningService) {
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.academicSessionRepository = academicSessionRepository;
        this.batchRepository = batchRepository;
        this.sectionRepository = sectionRepository;
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.accountProvisioningService = accountProvisioningService;
    }

    @Transactional
    public StudentManagementDTO createStudent(StudentManagementRequestDTO request) {
        String rollNumber = trim(request.getRollNumber());
        String name = trim(request.getName());

        if (rollNumber == null || rollNumber.isBlank()) {
            throw new AuthException("Roll number is required", 400);
        }
        if (name == null || name.isBlank()) {
            throw new AuthException("Name is required", 400);
        }

        if (studentRepository.existsByRollNumber(rollNumber)) {
            throw new AuthException("Roll number already exists: " + rollNumber, 409);
        }

        String email = normalizeEmail(request.getEmail());
        if (email != null && studentRepository.existsByEmail(email)) {
            throw new AuthException("Email already exists: " + email, 409);
        }
        if (email != null && teacherRepository.findByEmail(email).isPresent()) {
            throw new AuthException("Email already used by a teacher: " + email, 409);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new AuthException("Email already used by a login account: " + email, 409);
        }

        String status = trim(request.getStatus());
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
        if (!STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new AuthException("Status must be ACTIVE or INACTIVE", 400);
        }

        AcademicSession academicSession = resolveAcademicSession(request);
        Program program = resolveProgram(request);

        if (academicSession.getProgram() != null
                && !academicSession.getProgram().getId().equals(program.getId())) {
            throw new AuthException("Academic session program does not match student program", 400);
        }

        Batch batch = resolveBatch(request, academicSession);
        Section section = resolveSection(request, batch);

        if (batch != null && section != null
                && !section.getBatch().getId().equals(batch.getId())) {
            throw new AuthException("Section does not belong to the specified batch", 400);
        }

        LocalDateTime now = LocalDateTime.now();
        Student student = Student.builder()
                .rollNumber(rollNumber)
                .email(email)
                .name(name)
                .gender(request.getGender())
                .fatherName(request.getFatherName())
                .motherName(request.getMotherName())
                .photoUrl(request.getPhotoUrl())
                .enrollmentNumber(request.getEnrollmentNumber())
                .age(request.getAge())
                .admissionDate(request.getAdmissionDate())
                .status(status)
                .academicSession(academicSession)
                .program(program)
                .batch(batch)
                .section(section)
                .createdAt(now)
                .updatedAt(now)
                .build();
        student = studentRepository.save(student);

        boolean loginLinked = false;
        if (email != null && !email.isBlank()) {
            User provisioned = accountProvisioningService.provisionTemporaryLogin(
                    email, name, ROLE_STUDENT, status, TEMPORARY_PASSWORD);
            student.setEmail(provisioned.getEmail());
            loginLinked = true;
        }

        StudentManagementDTO dto = convertToDTO(student, loginLinked);
        if (loginLinked) {
            dto.setTemporaryPassword(TEMPORARY_PASSWORD);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public void assertValidForCreate(StudentManagementRequestDTO request) {
        String rollNumber = trim(request.getRollNumber());
        String name = trim(request.getName());

        if (rollNumber == null || rollNumber.isBlank()) {
            throw new AuthException("Roll number is required", 400);
        }
        if (name == null || name.isBlank()) {
            throw new AuthException("Name is required", 400);
        }

        String email = normalizeEmail(request.getEmail());

        if (studentRepository.existsByRollNumber(rollNumber)) {
            throw new AuthException("Roll number already exists: " + rollNumber, 409);
        }

        if (email != null && studentRepository.existsByEmail(email)) {
            throw new AuthException("Email already exists: " + email, 409);
        }
        if (email != null && teacherRepository.findByEmail(email).isPresent()) {
            throw new AuthException("Email already used by a teacher: " + email, 409);
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new AuthException("Email already used by a login account: " + email, 409);
        }

        String status = trim(request.getStatus());
        if (status != null && !status.isBlank()
                && !STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new AuthException("Status must be ACTIVE or INACTIVE", 400);
        }

        AcademicSession academicSession = resolveAcademicSession(request);
        Program program = resolveProgram(request);

        if (academicSession.getProgram() != null
                && !academicSession.getProgram().getId().equals(program.getId())) {
            throw new AuthException("Academic session program does not match student program", 400);
        }

        Batch batch = resolveBatch(request, academicSession);
        Section section = resolveSection(request, batch);

        if (batch != null && section != null
                && !section.getBatch().getId().equals(batch.getId())) {
            throw new AuthException("Section does not belong to the specified batch", 400);
        }
    }

    @Transactional(readOnly = true)
    public List<StudentManagementDTO> getAllStudents() {
        List<Student> students = studentRepository.findAllByOrderByNameAsc();
        return students.stream().map(s -> convertToDTO(s, false)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StudentManagementDTO getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with id: " + id, 404));
        boolean loginLinked = student.getEmail() != null
                && userRepository.findByEmail(student.getEmail()).isPresent();
        return convertToDTO(student, loginLinked);
    }

    @Transactional
    public StudentManagementDTO updateStudent(Long id, StudentManagementRequestDTO request) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with id: " + id, 404));

        String rollNumber = trim(request.getRollNumber());
        if (rollNumber == null || rollNumber.isBlank()) {
            throw new AuthException("Roll number is required", 400);
        }

        if (!rollNumber.equals(existing.getRollNumber())
                && studentRepository.existsByRollNumber(rollNumber)) {
            throw new AuthException("Roll number already exists: " + rollNumber, 409);
        }

        String email = normalizeEmail(request.getEmail());
        if (existing.getEmail() != null
                && userRepository.findByEmail(existing.getEmail()).isPresent()
                && !emailMatchesExisting(email, existing.getEmail())) {
            throw new AuthException("Email cannot be changed while a login account is linked", 409);
        }
        if (email != null && !email.equals(existing.getEmail())
                && studentRepository.existsByEmail(email)) {
            throw new AuthException("Email already exists: " + email, 409);
        }

        String status = trim(request.getStatus());
        if (status != null && !status.isBlank()
                && !STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new AuthException("Status must be ACTIVE or INACTIVE", 400);
        }

        AcademicSession academicSession = resolveAcademicSession(request);
        Program program = resolveProgram(request);

        if (academicSession.getProgram() != null
                && !academicSession.getProgram().getId().equals(program.getId())) {
            throw new AuthException("Academic session program does not match student program", 400);
        }

        Batch batch = resolveBatch(request, academicSession);
        Section section = resolveSection(request, batch);

        if (batch != null && section != null
                && !section.getBatch().getId().equals(batch.getId())) {
            throw new AuthException("Section does not belong to the specified batch", 400);
        }

        existing.setRollNumber(rollNumber);
        existing.setEmail(email);
        existing.setName(trim(request.getName()));
        existing.setGender(request.getGender());
        existing.setFatherName(request.getFatherName());
        existing.setMotherName(request.getMotherName());
        existing.setPhotoUrl(request.getPhotoUrl());
        existing.setEnrollmentNumber(request.getEnrollmentNumber());
        existing.setAge(request.getAge());
        existing.setAdmissionDate(request.getAdmissionDate());
        existing.setStatus(status != null && !status.isBlank() ? status : STATUS_ACTIVE);
        existing.setAcademicSession(academicSession);
        existing.setProgram(program);
        existing.setBatch(batch);
        existing.setSection(section);
        existing.setUpdatedAt(LocalDateTime.now());
        studentRepository.save(existing);

        return convertToDTO(existing, existing.getEmail() != null
                && userRepository.findByEmail(existing.getEmail()).isPresent());
    }

    @Transactional
    public StudentManagementDTO setStudentStatus(Long id, String status) {
        String normalized = AccountProvisioningService.normalizeStatus(status);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with id: " + id, 404));
        student.setStatus(normalized);
        student.setUpdatedAt(LocalDateTime.now());
        studentRepository.save(student);
        return convertToDTO(student, false);
    }

    @Transactional
    public StudentManagementDTO provisionLogin(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with id: " + id, 404));

        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            throw new AuthException("Student has no email linked", 400);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new AuthException("A login account already exists for this student", 409);
        }

        User provisioned = accountProvisioningService.provisionTemporaryLogin(
                email, student.getName(), ROLE_STUDENT,
                student.getStatus() != null ? student.getStatus() : STATUS_ACTIVE,
                TEMPORARY_PASSWORD);
        student.setEmail(provisioned.getEmail());

        StudentManagementDTO dto = convertToDTO(student, true);
        dto.setTemporaryPassword(TEMPORARY_PASSWORD);
        return dto;
    }

    @Transactional
    public StudentManagementDTO setLoginStatus(Long id, String status) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with id: " + id, 404));
        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            throw new AuthException("No linked login account", 404);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("No linked login account", 404));
        accountProvisioningService.changeStatus(user, status);
        return convertToDTO(student, true);
    }

    @Transactional
    public StudentManagementDTO setLoginPassword(Long id, String rawPassword) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new AuthException("Student not found with id: " + id, 404));
        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            throw new AuthException("No linked login account", 404);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("No linked login account", 404));
        accountProvisioningService.resetPassword(user, rawPassword);
        return convertToDTO(student, true);
    }

    private AcademicSession resolveAcademicSession(StudentManagementRequestDTO request) {
        AcademicSession session = null;

        if (request.getAcademicSessionId() != null) {
            session = academicSessionRepository.findById(request.getAcademicSessionId())
                    .orElseThrow(() -> new AuthException("Academic session not found with id: " + request.getAcademicSessionId(), 404));
        }

        if (session == null && request.getAcademicSession() != null && !request.getAcademicSession().isBlank()) {
            session = academicSessionRepository.findByName(request.getAcademicSession())
                    .orElseThrow(() -> new AuthException("Academic session not found: " + request.getAcademicSession(), 404));
        }

        if (session == null) {
            throw new AuthException("Academic session is required", 400);
        }

        return session;
    }

    private Program resolveProgram(StudentManagementRequestDTO request) {
        Program program = null;

        if (request.getProgramId() != null) {
            program = programRepository.findById(request.getProgramId())
                    .orElseThrow(() -> new AuthException("Program not found with id: " + request.getProgramId(), 404));
        }

        if (program == null && request.getProgram() != null && !request.getProgram().isBlank()) {
            program = programRepository.findByName(request.getProgram())
                    .orElseThrow(() -> new AuthException("Program not found: " + request.getProgram(), 404));
        }

        if (program == null) {
            throw new AuthException("Program is required", 400);
        }

        return program;
    }

    private Batch resolveBatch(StudentManagementRequestDTO request, AcademicSession academicSession) {
        if (request.getBatchId() != null) {
            Batch batch = batchRepository.findById(request.getBatchId())
                    .orElseThrow(() -> new AuthException("Batch not found with id: " + request.getBatchId(), 404));
            if (!batch.getAcademicSession().getId().equals(academicSession.getId())) {
                throw new AuthException("Batch does not belong to the specified academic session", 400);
            }
            return batch;
        }

        if (request.getBatch() != null && !request.getBatch().isBlank()) {
            AcademicSession session = academicSession;
            Program program = resolveProgram(request);
            Batch matching = batchRepository.findByAcademicSession(session).stream()
                    .filter(b -> b.getName().equals(request.getBatch())
                            && b.getAcademicSession().getId().equals(session.getId())
                            && b.getAcademicSession().getProgram().getId().equals(program.getId()))
                    .findFirst()
                    .orElse(null);
            if (matching != null) {
                return matching;
            }
        }

        return null;
    }

    private Section resolveSection(StudentManagementRequestDTO request, Batch batch) {
        if (request.getSectionId() != null) {
            Section section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new AuthException("Section not found with id: " + request.getSectionId(), 404));
            if (batch != null && !section.getBatch().getId().equals(batch.getId())) {
                throw new AuthException("Section does not belong to the specified batch", 400);
            }
            return section;
        }

        if (request.getSection() != null && !request.getSection().isBlank() && batch != null) {
            Section matching = sectionRepository.findByNameAndBatch(request.getSection(), batch)
                    .orElse(null);
            if (matching != null) {
                return matching;
            }
        }

        return null;
    }

    private StudentManagementDTO convertToDTO(Student student, boolean loginLinked) {
        StudentManagementDTO dto = StudentManagementDTO.builder()
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
                .programId(student.getProgram() != null ? student.getProgram().getId() : null)
                .programName(student.getProgram() != null ? student.getProgram().getName() : null)
                .batchId(student.getBatch() != null ? student.getBatch().getId() : null)
                .batchName(student.getBatch() != null ? student.getBatch().getName() : null)
                .sectionId(student.getSection() != null ? student.getSection().getId() : null)
                .sectionName(student.getSection() != null ? student.getSection().getName() : null)
                .academicSessionId(student.getAcademicSession() != null ? student.getAcademicSession().getId() : null)
                .academicSessionName(student.getAcademicSession() != null ? student.getAcademicSession().getName() : null)
                .loginLinked(loginLinked)
                .mustChangePassword(loginLinked && student.getEmail() != null
                        && userRepository.findByEmail(student.getEmail()).map(User::isMustChangePassword).orElse(false))
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();

        if (loginLinked && student.getEmail() != null) {
            User user = userRepository.findByEmail(student.getEmail()).orElse(null);
            if (user != null) {
                dto.setLoginStatus(user.getStatus());
                dto.setMustChangePassword(user.isMustChangePassword());
            }
        }

        return dto;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private boolean emailMatchesExisting(String email, String existingEmail) {
        return email != null && email.equals(existingEmail);
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
