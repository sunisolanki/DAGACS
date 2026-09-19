package com.dagacs.service;

import com.dagacs.dto.StudentFilterOption;
import com.dagacs.dto.StudentFilterOptionsResponse;
import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.dto.StudentPageResponse;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Role;
import com.dagacs.entity.Section;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Student;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudentManagementService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String TEMPORARY_PASSWORD = "Dagacs@123";
    private static final String LOGIN_STATUS_ACTIVE = "ACTIVE";
    private static final String LOGIN_STATUS_NONE = "NONE";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final StudentManagementRepository studentRepository;
    private final ProgramRepository programRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final BatchRepository batchRepository;
    private final SectionRepository sectionRepository;
    private final SemesterRepository semesterRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final AccountProvisioningService accountProvisioningService;

    @Autowired
    public StudentManagementService(StudentManagementRepository studentRepository,
                                     ProgramRepository programRepository,
                                     AcademicSessionRepository academicSessionRepository,
                                     BatchRepository batchRepository,
                                     SectionRepository sectionRepository,
                                     SemesterRepository semesterRepository,
                                     TeacherRepository teacherRepository,
                                     UserRepository userRepository,
                                     AccountProvisioningService accountProvisioningService) {
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.academicSessionRepository = academicSessionRepository;
        this.batchRepository = batchRepository;
        this.sectionRepository = sectionRepository;
        this.semesterRepository = semesterRepository;
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

        Semester semester = resolveSemester(request, academicSession);

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
                .semester(semester)
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

        resolveSemester(request, academicSession);
    }

    @Transactional(readOnly = true)
    public StudentPageResponse searchStudents(String search,
                                              Long programId,
                                              Long academicSessionId,
                                              Long semesterId,
                                              Long batchId,
                                              Long sectionId,
                                              String status,
                                              String loginStatus,
                                              int page,
                                              int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Specification<Student> filters = buildFilterSpec(
                search, programId, academicSessionId, semesterId,
                batchId, sectionId, status, loginStatus);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "name"));

        Page<Student> studentPage = studentRepository.findAll(filters, pageable);
        Map<String, User> usersByEmail = loadUsersByEmail(studentPage.getContent());
        List<StudentManagementDTO> content = studentPage.getContent().stream()
                .map(s -> convertToDTO(s, isLoginLinked(s, usersByEmail), usersByEmail))
                .collect(Collectors.toList());

        return StudentPageResponse.builder()
                .content(content)
                .page(studentPage.getNumber())
                .size(studentPage.getSize())
                .totalElements(studentPage.getTotalElements())
                .totalPages(studentPage.getTotalPages())
                .build();
    }

    /**
     * Cascaded academic filter options sourced from the master-data entities.
     *
     * <p>Each currently-selected id (session / program / semester / batch /
     * section) maps to the set of academic sessions it implies; the
     * intersection of those sets is the "consistent academic context". Every
     * option list in the response is derived from that context, so options stay
     * selectable even with zero matching students and each level only exposes
     * options valid for the selection above it. An incoherent selection (e.g. a
     * session and a batch from different programs) yields empty lists.</p>
     *
     * <p>This concerns options ONLY - student-query filtering never re-applies
     * this cascade.</p>
     */
    @Transactional(readOnly = true)
    public StudentFilterOptionsResponse getFilterOptions(Long academicSessionId,
                                                         Long programId,
                                                         Long semesterId,
                                                         Long batchId,
                                                         Long sectionId) {
        List<Set<AcademicSession>> impliedSets = new ArrayList<>();

        if (academicSessionId != null) {
            AcademicSession session = academicSessionRepository.findById(academicSessionId)
                    .orElseThrow(() -> new AuthException("Academic session not found with id: " + academicSessionId, 404));
            impliedSets.add(Collections.singleton(session));
        }
        if (programId != null) {
            Program program = programRepository.findById(programId)
                    .orElseThrow(() -> new AuthException("Program not found with id: " + programId, 404));
            impliedSets.add(new LinkedHashSet<>(academicSessionRepository.findByProgram(program)));
        }
        if (semesterId != null) {
            Semester semester = semesterRepository.findById(semesterId)
                    .orElseThrow(() -> new AuthException("Semester not found with id: " + semesterId, 404));
            impliedSets.add(Collections.singleton(semester.getAcademicSession()));
        }
        if (batchId != null) {
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new AuthException("Batch not found with id: " + batchId, 404));
            impliedSets.add(Collections.singleton(batch.getAcademicSession()));
        }
        if (sectionId != null) {
            Section section = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new AuthException("Section not found with id: " + sectionId, 404));
            impliedSets.add(Collections.singleton(section.getBatch().getAcademicSession()));
        }

        Set<AcademicSession> consistent;
        if (impliedSets.isEmpty()) {
            consistent = new LinkedHashSet<>(academicSessionRepository.findAllByOrderByName());
        } else {
            consistent = intersectBySession(impliedSets, impliedSets.get(0));
        }

        List<StudentFilterOption> sessionOptions = consistent.stream()
                .sorted(Comparator.comparing(AcademicSession::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> StudentFilterOption.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .programId(s.getProgram() != null ? s.getProgram().getId() : null)
                        .programName(s.getProgram() != null ? s.getProgram().getName() : null)
                        .build())
                .collect(Collectors.toList());

        List<StudentFilterOption> programOptions = consistent.stream()
                .map(AcademicSession::getProgram)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Program::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> StudentFilterOption.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .build())
                .collect(Collectors.toList());

        List<StudentFilterOption> semesterOptions = semesterRepository
                .findByAcademicSessionIn(consistent).stream()
                .sorted(Comparator.comparing(Semester::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> StudentFilterOption.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .academicSessionId(m.getAcademicSession() != null ? m.getAcademicSession().getId() : null)
                        .academicSessionName(m.getAcademicSession() != null ? m.getAcademicSession().getName() : null)
                        .programId(m.getAcademicSession() != null && m.getAcademicSession().getProgram() != null
                                ? m.getAcademicSession().getProgram().getId() : null)
                        .programName(m.getAcademicSession() != null && m.getAcademicSession().getProgram() != null
                                ? m.getAcademicSession().getProgram().getName() : null)
                        .build())
                .collect(Collectors.toList());

        List<StudentFilterOption> batchOptions = batchRepository
                .findByAcademicSessionIn(consistent).stream()
                .sorted(Comparator.comparing(Batch::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(b -> StudentFilterOption.builder()
                        .id(b.getId())
                        .name(b.getName())
                        .academicSessionId(b.getAcademicSession() != null ? b.getAcademicSession().getId() : null)
                        .academicSessionName(b.getAcademicSession() != null ? b.getAcademicSession().getName() : null)
                        .programId(b.getAcademicSession() != null && b.getAcademicSession().getProgram() != null
                                ? b.getAcademicSession().getProgram().getId() : null)
                        .programName(b.getAcademicSession() != null && b.getAcademicSession().getProgram() != null
                                ? b.getAcademicSession().getProgram().getName() : null)
                        .build())
                .collect(Collectors.toList());

        List<StudentFilterOption> sectionOptions;
        if (batchId != null) {
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new AuthException("Batch not found with id: " + batchId, 404));
            sectionOptions = sectionRepository.findByBatch(batch).stream()
                    .sorted(Comparator.comparing(Section::getName,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(x -> sectionOption(x))
                    .collect(Collectors.toList());
        } else {
            List<Batch> batchesOfContext = batchRepository.findByAcademicSessionIn(consistent);
            sectionOptions = sectionRepository.findByBatchIn(batchesOfContext).stream()
                    .sorted(Comparator.comparing(Section::getName,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(x -> sectionOption(x))
                    .collect(Collectors.toList());
        }

        return StudentFilterOptionsResponse.builder()
                .academicSessions(sessionOptions)
                .programs(programOptions)
                .semesters(semesterOptions)
                .batches(batchOptions)
                .sections(sectionOptions)
                .build();
    }

    /**
     * Equality-only student filter (never any cascade logic): the query applies
     * exactly the explicitly selected ids plus the optional free-text search,
     * status and loginStatus. See {@link #getFilterOptions} for the (separate)
     * option-cascade.
     */
    private Specification<Student> buildFilterSpec(String search,
                                                   Long programId,
                                                   Long academicSessionId,
                                                   Long semesterId,
                                                   Long batchId,
                                                   Long sectionId,
                                                   String status,
                                                   String loginStatus) {
        List<Specification<Student>> parts = new ArrayList<>();

        if (programId != null) {
            parts.add((root, query, cb) ->
                    cb.equal(root.get("program").get("id"), programId));
        }
        if (academicSessionId != null) {
            parts.add((root, query, cb) ->
                    cb.equal(root.get("academicSession").get("id"), academicSessionId));
        }
        if (semesterId != null) {
            parts.add((root, query, cb) ->
                    cb.equal(root.get("semester").get("id"), semesterId));
        }
        if (batchId != null) {
            parts.add((root, query, cb) ->
                    cb.equal(root.get("batch").get("id"), batchId));
        }
        if (sectionId != null) {
            parts.add((root, query, cb) ->
                    cb.equal(root.get("section").get("id"), sectionId));
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (STATUS_ACTIVE.equals(normalized) || STATUS_INACTIVE.equals(normalized)) {
                parts.add((root, query, cb) -> cb.equal(root.get("status"), normalized));
            }
        }
        if (LOGIN_STATUS_ACTIVE.equalsIgnoreCase(loginStatus == null ? "" : loginStatus.trim())) {
            parts.add((root, query, cb) -> {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<User> userRoot = sub.from(User.class);
                sub.select(userRoot.get("id"));
                sub.where(cb.equal(userRoot.get("email"), root.get("email")),
                        cb.equal(userRoot.get("status"), STATUS_ACTIVE));
                return cb.exists(sub);
            });
        } else if (LOGIN_STATUS_NONE.equalsIgnoreCase(loginStatus == null ? "" : loginStatus.trim())) {
            parts.add((root, query, cb) -> {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<User> userRoot = sub.from(User.class);
                sub.select(userRoot.get("id"));
                sub.where(cb.equal(userRoot.get("email"), root.get("email")));
                return cb.not(cb.exists(sub));
            });
        }
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            parts.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), term),
                    cb.like(cb.lower(root.get("rollNumber")), term),
                    cb.like(cb.lower(root.get("enrollmentNumber")), term)));
        }

        if (parts.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        Specification<Student> combined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            combined = combined.and(parts.get(i));
        }
        return combined;
    }

    /** Batch-loads the User for every page student (single query, no N+1). */
    private Map<String, User> loadUsersByEmail(List<Student> students) {
        List<String> emails = students.stream()
                .map(Student::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());
        Map<String, User> result = new LinkedHashMap<>();
        if (emails.isEmpty()) {
            return result;
        }
        for (User user : userRepository.findByEmailIn(emails)) {
            if (user.getEmail() != null) {
                result.put(user.getEmail(), user);
            }
        }
        return result;
    }

    private boolean isLoginLinked(Student student, Map<String, User> usersByEmail) {
        String email = student.getEmail();
        return email != null && !email.isBlank() && usersByEmail.containsKey(email);
    }

    private Set<AcademicSession> intersectBySession(List<Set<AcademicSession>> impliedSets,
                                                    Set<AcademicSession> seed) {
        Set<AcademicSession> result = new LinkedHashSet<>(seed);
        for (Set<AcademicSession> set : impliedSets) {
            result.retainAll(set);
        }
        return result;
    }

    private StudentFilterOption sectionOption(Section section) {
        Batch batch = section.getBatch();
        return StudentFilterOption.builder()
                .id(section.getId())
                .name(section.getName())
                .batchId(batch != null ? batch.getId() : null)
                .batchName(batch != null ? batch.getName() : null)
                .build();
    }

    private Semester resolveSemester(StudentManagementRequestDTO request, AcademicSession academicSession) {
        if (request.getSemesterId() == null) {
            return null;
        }
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(() -> new AuthException(
                        "Semester not found with id: " + request.getSemesterId(), 404));
        if (!semester.getAcademicSession().getId().equals(academicSession.getId())) {
            throw new AuthException("Semester does not belong to the specified academic session", 400);
        }
        return semester;
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

        Semester semester = resolveSemester(request, academicSession);

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
        existing.setSemester(semester);
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
        return convertToDTO(student, loginLinked, null);
    }

    private StudentManagementDTO convertToDTO(Student student,
                                              boolean loginLinked,
                                              Map<String, User> usersByEmail) {
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
                .semesterId(student.getSemester() != null ? student.getSemester().getId() : null)
                .semesterName(student.getSemester() != null ? student.getSemester().getName() : null)
                .academicSessionId(student.getAcademicSession() != null ? student.getAcademicSession().getId() : null)
                .academicSessionName(student.getAcademicSession() != null ? student.getAcademicSession().getName() : null)
                .loginLinked(loginLinked)
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();

        if (loginLinked && student.getEmail() != null) {
            User user = null;
            if (usersByEmail != null) {
                user = usersByEmail.get(student.getEmail());
            } else {
                user = userRepository.findByEmail(student.getEmail()).orElse(null);
            }
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
