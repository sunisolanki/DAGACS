package com.dagacs.service;

import com.dagacs.dto.TeacherCreateRequestDTO;
import com.dagacs.dto.TeacherLoginRequestDTO;
import com.dagacs.dto.TeacherManagementDTO;
import com.dagacs.dto.TeacherUpdateRequestDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin teacher-profile management service (M9.5.1).
 * <p>
 * Separates the two D2 lifecycles:
 * <ul>
 *   <li><em>Teacher profile</em> — created/edited/activated/deactivated by
 *       {@link #createTeacher}, {@link #updateTeacher}, {@link #setTeacherStatus}.</li>
 *   <li><em>Login account</em> — provisioned/reset/deactivated by
 *       {@link #provisionLogin}, {@link #setLoginPassword}, {@link #setLoginStatus}
 *       through {@link AccountProvisioningService}.</li>
 * </ul>
 * The two states never cascade (D4) and are linked by email only (D1): this
 * service stamps a login with the exact teacher email and forbids changing the
 * teacher email after creation, so {@code User.email == Teacher.email} cannot
 * drift. Any email collision across teachers, students or logins returns 409.
 * </p>
 */
@Service
public class TeacherManagementService {

    private static final String ROLE_TEACHER = "TEACHER";

    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final StudentManagementRepository studentRepository;
    private final AccountProvisioningService accountProvisioningService;

    @Autowired
    public TeacherManagementService(TeacherRepository teacherRepository,
                                    DepartmentRepository departmentRepository,
                                    UserRepository userRepository,
                                    StudentManagementRepository studentRepository,
                                    AccountProvisioningService accountProvisioningService) {
        this.teacherRepository = teacherRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.accountProvisioningService = accountProvisioningService;
    }

    @Transactional(readOnly = true)
    public List<TeacherManagementDTO> listTeachers() {
        return teacherRepository.findAllByOrderByFullNameAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TeacherManagementDTO getTeacher(Long id) {
        return convertToDTO(findTeacherOrThrow(id));
    }

    @Transactional
    public TeacherManagementDTO createTeacher(TeacherCreateRequestDTO request) {
        String email = requiredValue(request.getEmail(), "Email is required").toLowerCase();
        ensureEmailAvailable(email);

        Department department = request.getDepartmentId() != null
                ? resolveDepartment(request.getDepartmentId())
                : null;
        String status = AccountProvisioningService.normalizeStatus(request.getStatus());

        LocalDateTime now = LocalDateTime.now();
        Teacher teacher = Teacher.builder()
                .email(email)
                .department(department)
                .isHod(false)
                .password("")
                .fullName(requiredValue(request.getFullName(), "Full name is required"))
                .phone(defaultValue(request.getPhone()))
                .designation(defaultValue(request.getDesignation()))
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .avatarUrl("")
                .build();
        teacherRepository.save(teacher);

        accountProvisioningService.provisionLogin(email, teacher.getFullName(),
                request.getPassword(), ROLE_TEACHER, status);
        return convertToDTO(teacher);
    }

    @Transactional
    public TeacherManagementDTO updateTeacher(Long id, TeacherUpdateRequestDTO request) {
        Teacher teacher = findTeacherOrThrow(id);

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            teacher.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            teacher.setPhone(defaultValue(request.getPhone()));
        }
        if (request.getDesignation() != null) {
            teacher.setDesignation(defaultValue(request.getDesignation()));
        }
        if (request.getDepartmentId() != null) {
            Department requested = resolveDepartment(request.getDepartmentId());
            if (teacher.getDepartment() == null
                    || !teacher.getDepartment().getId().equals(requested.getId())) {
                assertNotUnassociatedHodOnMove(teacher);
                teacher.setDepartment(requested);
            }
        }

        teacher.setUpdatedAt(LocalDateTime.now());
        teacher = teacherRepository.save(teacher);
        return convertToDTO(teacher);
    }

    @Transactional
    public TeacherManagementDTO setTeacherStatus(Long id, String rawStatus) {
        Teacher teacher = findTeacherOrThrow(id);
        String status = AccountProvisioningService.normalizeStatus(rawStatus);
        teacher.setStatus(status);
        teacher.setUpdatedAt(LocalDateTime.now());
        teacher = teacherRepository.save(teacher);
        return convertToDTO(teacher);
    }

    @Transactional
    public TeacherManagementDTO provisionLogin(Long id, TeacherLoginRequestDTO request) {
        Teacher teacher = findTeacherOrThrow(id);
        String email = teacher.getEmail();
        if (email == null || email.trim().isEmpty()) {
            throw new AuthException("The teacher has no email to link a login to", 400);
        }

        Optional<User> existing = userRepository.findByEmail(email.toLowerCase());
        if (existing.isPresent()) {
            if (ROLE_TEACHER.equals(existing.get().getRole().getName())) {
                throw new AuthException("A login account is already linked to this teacher", 409);
            }
            throw new AuthException("Email is already in use by another login account", 409);
        }

        accountProvisioningService.provisionLogin(email, teacher.getFullName(),
                request.getPassword(), ROLE_TEACHER, request.getStatus());
        return convertToDTO(teacher);
    }

    @Transactional
    public TeacherManagementDTO setLoginStatus(Long id, String rawStatus) {
        Teacher teacher = findTeacherOrThrow(id);
        User user = linkedUserOrThrow(teacher);
        accountProvisioningService.changeStatus(user, rawStatus);
        return convertToDTO(teacher);
    }

    @Transactional
    public TeacherManagementDTO setLoginPassword(Long id, String rawPassword) {
        Teacher teacher = findTeacherOrThrow(id);
        User user = linkedUserOrThrow(teacher);
        accountProvisioningService.resetPassword(user, rawPassword);
        return convertToDTO(teacher);
    }

    private Teacher findTeacherOrThrow(Long id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new AuthException("Teacher not found with ID: " + id, 404));
    }

    private User linkedUserOrThrow(Teacher teacher) {
        String email = teacher.getEmail();
        if (email == null || email.trim().isEmpty()) {
            throw new AuthException("The teacher has no email to link a login to", 400);
        }
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new AuthException(
                        "No login account is linked to this teacher", 404));
    }

    private Department resolveDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new AuthException("Department not found with ID: " + departmentId, 404));
    }

    private void ensureEmailAvailable(String email) {
        if (teacherRepository.findByEmail(email).isPresent()) {
            throw new AuthException("A teacher with this email already exists", 409);
        }
        if (studentRepository.existsByEmail(email)) {
            throw new AuthException("Email is already registered to a student profile", 409);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AuthException("Email is already in use by a login account", 409);
        }
    }

    private void assertNotUnassociatedHodOnMove(Teacher teacher) {
        if (Boolean.TRUE.equals(teacher.getIsHod())) {
            throw new AuthException(
                    "Move the HOD designation before changing this teacher's department", 409);
        }
    }

    private TeacherManagementDTO convertToDTO(Teacher teacher) {
        User user = null;
        String email = teacher.getEmail();
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email.toLowerCase()).orElse(null);
        }
        return TeacherManagementDTO.builder()
                .id(teacher.getId())
                .email(teacher.getEmail())
                .fullName(teacher.getFullName())
                .phone(teacher.getPhone())
                .designation(teacher.getDesignation())
                .status(teacher.getStatus())
                .departmentId(teacher.getDepartment() != null ? teacher.getDepartment().getId() : null)
                .departmentName(teacher.getDepartment() != null ? teacher.getDepartment().getName() : null)
                .isHod(Boolean.TRUE.equals(teacher.getIsHod()))
                .loginLinked(user != null)
                .loginStatus(user != null ? user.getStatus() : null)
                .createdAt(teacher.getCreatedAt())
                .updatedAt(teacher.getUpdatedAt())
                .build();
    }

    private static String requiredValue(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new AuthException(message, 400);
        }
        return value.trim();
    }

    private static String defaultValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }
}