package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.AttendanceSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.entity.Teacher;
import com.dagacs.entity.TeacherSubjectSectionAssignment;
import com.dagacs.entity.User;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.SubjectRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import com.dagacs.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.12 academic-spine reassignment consistency: true end-to-end tests with a
 * real ADMIN JWT login through {@code /api/auth/login}.
 * <p>
 * Proves that the three sibling reassignment edges are dependency-aware and
 * do not mutate state on rejection (HTTP 409 + POST-state GET confirms the
 * original foreign key is unchanged):
 * <ul>
 *   <li>Section -&gt; different Batch is blocked when students or teacher
 *       assignments exist, allowed when empty;</li>
 *   <li>Batch -&gt; different AcademicSession is blocked when teacher
 *       assignments or attendance sessions exist, allowed when empty;</li>
 *   <li>SubjectOffering -&gt; different Semester is blocked when teacher
 *       assignments exist, allowed otherwise;</li>
 *   <li>each protected reassignment endpoint rejects a non-ADMIN caller with
 *       403.</li>
 * </ul>
 * <p>
 * Everything (master data + descendants) is created inside the test
 * transaction and rolled back, so no production/demo data is written.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AssignmentConsistencyReassignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private String login(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String adminToken() throws Exception {
        return login("admin@dagacs.local", "Admin@123");
    }

    private String studentToken() throws Exception {
        return login("student@dagacs.local", "Student@123");
    }

    private Department createDepartment() {
        String stamp = String.valueOf(System.nanoTime());
        return departmentRepository.save(Department.builder()
                .name("Dept-" + stamp).code("D" + stamp.substring(stamp.length() - 4))
                .description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Program createProgram(Department dept, String name) {
        return programRepository.save(Program.builder()
                .name(name + "-" + System.nanoTime()).code("P" + System.nanoTime()).duration("4yr")
                .description("Test").department(dept).build());
    }

    private AcademicSession createSession(Program program) {
        return academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("SC" + System.nanoTime())
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Batch createBatch(AcademicSession session) {
        return batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B" + System.nanoTime()).year(2026)
                .program(session.getProgram().getName()).maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createSection(Batch batch) {
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A" + System.nanoTime()).maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Semester createSemester(AcademicSession session) {
        return semesterRepository.save(Semester.builder()
                .name("Sem-" + System.nanoTime()).code("SEM" + System.nanoTime()).year(1)
                .academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private SubjectOffering createOffering(Semester semester) {
        Subject subject = subjectRepository.save(Subject.builder()
                .code("SUB-" + System.nanoTime()).name("Subject " + System.nanoTime()).description("Test")
                .creditHours("3").status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Subject createSubject() {
        return subjectRepository.save(Subject.builder()
                .code("SUB-" + System.nanoTime()).name("Subject " + System.nanoTime()).description("Test")
                .creditHours("3").status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Teacher createTeacherAccount(String email, Department dept) {
        roleRepository.findByName("TEACHER").orElseThrow();
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("Pass@123")).fullName("Teacher " + email)
                .phone("").status("ACTIVE").role(roleRepository.findByName("TEACHER").orElseThrow())
                .avatarUrl("").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("Teacher " + email)
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false).department(dept)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private void assign(Teacher teacher, SubjectOffering offering, Section section) {
        assignmentRepository.save(TeacherSubjectSectionAssignment.builder()
                .teacher(teacher).subjectOffering(offering).section(section)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private void createAttendanceSession(Subject subject, Section section, Teacher teacher) {
        attendanceSessionRepository.save(AttendanceSession.builder()
                .subjectEntity(subject).sectionEntity(section).teacherEntity(teacher)
                .subject(subject.getName()).section(section.getName()).teacher(teacher.getFullName())
                .lecturePeriod("P1").date("2026-09-01").status("CONDUCTED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String sectionBody(Long batchId, String sectionCode) {
        return "{"
                + "\"sectionCode\":\"" + sectionCode + "\","
                + "\"name\":\"" + String.valueOf(System.nanoTime()) + "\","
                + "\"maxCapacity\":30,"
                + "\"batchId\":" + batchId
                + "}";
    }

    private String batchBody(Long academicSessionId, String batchCode) {
        return "{"
                + "\"batchCode\":\"" + batchCode + "\","
                + "\"name\":\"" + String.valueOf(System.nanoTime()) + "\","
                + "\"year\":2026,"
                + "\"academicSessionId\":" + academicSessionId + ","
                + "\"maxCapacity\":60"
                + "}";
    }

    private String offeringBody(Long subjectId, Long semesterId) {
        return "{"
                + "\"subjectId\":" + subjectId + ","
                + "\"semesterId\":" + semesterId
                + "}";
    }

    private String studentBody(Section section) {
        return "{"
                + "\"rollNumber\":\"STU" + System.nanoTime() + "\","
                + "\"email\":\"stu" + System.nanoTime() + "@dagacs.local\","
                + "\"name\":\"Rahul Kumar\","
                + "\"gender\":\"M\","
                + "\"fatherName\":\"Father\","
                + "\"motherName\":\"Mother\","
                + "\"photoUrl\":\"\","
                + "\"enrollmentNumber\":\"ENR-" + System.nanoTime() + "\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"status\":\"ACTIVE\","
                + "\"programId\":" + section.getBatch().getAcademicSession().getProgram().getId() + ","
                + "\"batchId\":" + section.getBatch().getId() + ","
                + "\"sectionId\":" + section.getId()
                + "}";
    }

    // ============================ Section -> Batch ============================

    @Test
    void sectionMove_withStudent_isRejected409_batchUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession session = createSession(program);
        Batch batchA = createBatch(session);
        Batch batchB = createBatch(session);
        Section section = createSection(batchA);

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody(batchB.getId(), section.getSectionCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move a section to a different batch while students or teacher assignments exist"));

        mockMvc.perform(get("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchA.getId()));
    }

    @Test
    void sectionMove_withTeacherAssignment_isRejected409_batchUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession session = createSession(program);
        Batch batchA = createBatch(session);
        Batch batchB = createBatch(session);
        Section section = createSection(batchA);
        Semester semester = createSemester(session);
        SubjectOffering offering = createOffering(semester);
        Teacher teacher = createTeacherAccount("m912sec" + System.nanoTime() + "@dagacs.local", dept);
        assign(teacher, offering, section);

        mockMvc.perform(put("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody(batchB.getId(), section.getSectionCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move a section to a different batch while students or teacher assignments exist"));

        mockMvc.perform(get("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchA.getId()));
    }

    @Test
    void sectionMove_emptySection_isAllowed200_batchUpdated() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession session = createSession(program);
        Batch batchA = createBatch(session);
        Batch batchB = createBatch(session);
        Section section = createSection(batchA);

        mockMvc.perform(put("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody(batchB.getId(), section.getSectionCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchB.getId()));

        mockMvc.perform(get("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchB.getId()));
    }

    // ============================ Batch -> AcademicSession ============================

    @Test
    void batchMove_withTeacherAssignment_isRejected409_sessionUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession sessionA = createSession(program);
        AcademicSession sessionB = createSession(program);
        Batch batch = createBatch(sessionA);
        Section section = createSection(batch);
        Semester semester = createSemester(sessionA);
        SubjectOffering offering = createOffering(semester);
        Teacher teacher = createTeacherAccount("m912bat" + System.nanoTime() + "@dagacs.local", dept);
        assign(teacher, offering, section);

        mockMvc.perform(put("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessionB.getId(), batch.getBatchCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move batch to a different academic session while teacher assignments or attendance sessions exist"));

        mockMvc.perform(get("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessionA.getId()));
    }

    @Test
    void batchMove_withAttendanceSession_isRejected409_sessionUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession sessionA = createSession(program);
        AcademicSession sessionB = createSession(program);
        Batch batch = createBatch(sessionA);
        Section section = createSection(batch);
        Subject subject = createSubject();
        Teacher teacher = createTeacherAccount("m912att" + System.nanoTime() + "@dagacs.local", dept);
        createAttendanceSession(subject, section, teacher);

        mockMvc.perform(put("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessionB.getId(), batch.getBatchCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move batch to a different academic session while teacher assignments or attendance sessions exist"));

        mockMvc.perform(get("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessionA.getId()));
    }

    @Test
    void batchMove_noDependencies_isAllowed200_sessionUpdated() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession sessionA = createSession(program);
        AcademicSession sessionB = createSession(program);
        Batch batch = createBatch(sessionA);

        mockMvc.perform(put("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessionB.getId(), batch.getBatchCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessionB.getId()));

        mockMvc.perform(get("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessionB.getId()));
    }

    // ============================ SubjectOffering -> Semester ============================

    @Test
    void offeringMove_withTeacherAssignment_isRejected409_semesterUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession session = createSession(program);
        Semester semesterA = createSemester(session);
        Semester semesterB = createSemester(session);
        SubjectOffering offering = createOffering(semesterA);
        Batch batch = createBatch(session);
        Section section = createSection(batch);
        Teacher teacher = createTeacherAccount("m912ofr" + System.nanoTime() + "@dagacs.local", dept);
        assign(teacher, offering, section);

        mockMvc.perform(put("/api/admin/subject-offerings/" + offering.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(offering.getSubject().getId(), semesterB.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move subject offering to a different semester while teacher assignments exist"));

        mockMvc.perform(get("/api/admin/subject-offerings/" + offering.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(semesterA.getId()));
    }

    @Test
    void offeringMove_withoutAssignment_isAllowed200_semesterUpdated() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession session = createSession(program);
        Semester semesterA = createSemester(session);
        Semester semesterB = createSemester(session);
        SubjectOffering offering = createOffering(semesterA);

        mockMvc.perform(put("/api/admin/subject-offerings/" + offering.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(offering.getSubject().getId(), semesterB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(semesterB.getId()));

        mockMvc.perform(get("/api/admin/subject-offerings/" + offering.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(semesterB.getId()));
    }

    // ============================ Authorization ============================

    @Test
    void nonAdmin_cannotReassign_returns403() throws Exception {
        String student = studentToken();
        Department dept = createDepartment();
        Program program = createProgram(dept, "CS");
        AcademicSession sessionA = createSession(program);
        AcademicSession sessionB = createSession(program);
        Batch batch = createBatch(sessionA);
        Batch batchB = createBatch(sessionA);
        Section section = createSection(batch);
        Semester semester = createSemester(sessionA);
        SubjectOffering offering = createOffering(semester);

        mockMvc.perform(put("/api/admin/sections/" + section.getId())
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody(batchB.getId(), section.getSectionCode())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/batches/" + batch.getId())
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessionB.getId(), batch.getBatchCode())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/subject-offerings/" + offering.getId())
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(offering.getSubject().getId(), semester.getId())))
                .andExpect(status().isForbidden());
    }
}