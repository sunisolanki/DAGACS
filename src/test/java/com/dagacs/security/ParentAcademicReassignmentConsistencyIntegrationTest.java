package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Semester;
import com.dagacs.entity.Subject;
import com.dagacs.entity.SubjectOffering;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SemesterRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.SubjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M9.11 parent-academic reassignment consistency enforcement: true end-to-end
 * tests with a real ADMIN JWT login through {@code /api/auth/login}.
 * <p>
 * Proves that:
 * <ul>
 *   <li>an AcademicSession cannot be moved to a different Program while a
 *       Batch exists (409, state unchanged);</li>
 *   <li>a Semester cannot be moved to a different AcademicSession while a
 *       SubjectOffering exists (409, state unchanged) and cannot be deleted
 *       in that state (409);</li>
 *   <li>a Program cannot be moved to a different Department while Academic
 *       Sessions exist (409, state unchanged);</li>
 *   <li>a Student whose selected Program shares the batch's program name but
 *       not the same Program id is rejected (the consistency check is now
 *       id-based, not name-based);</li>
 *   <li>the same reassignments succeed when no descendants exist (guards are
 *       conditional, not blanket blocks).</li>
 * </ul>
 * <p>
 * Everything (master data + descendants) is created inside the test
 * transaction and rolled back, so no production/demo data is written.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ParentAcademicReassignmentConsistencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SubjectOfferingRepository subjectOfferingRepository;

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

    private Department createDepartment(String prefix) {
        String stamp = String.valueOf(System.nanoTime());
        return departmentRepository.save(Department.builder()
                .name(prefix + "-Dept-" + stamp).code("D" + stamp.substring(stamp.length() - 4))
                .description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Program createProgram(Department dept, String name, String code) {
        return programRepository.save(Program.builder()
                .name(name).code(code).duration("4yr")
                .description("Test").department(dept).build());
    }

    private AcademicSession createSession(Program program, String prefix) {
        return academicSessionRepository.save(AcademicSession.builder()
                .name(prefix + "-" + System.nanoTime()).code(prefix)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Batch createBatch(AcademicSession session, String name, String code) {
        return batchRepository.save(Batch.builder()
                .batchCode(code + "-" + System.nanoTime()).name(name).year(2026)
                .program(session.getProgram().getName()).maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Section createSection(Batch batch, String name, String code) {
        return sectionRepository.save(Section.builder()
                .sectionCode(code + "-" + System.nanoTime()).name(name).maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Semester createSemester(AcademicSession session, String name, String code) {
        return semesterRepository.save(Semester.builder()
                .name(name + "-" + System.nanoTime()).code(code).year(1).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private SubjectOffering createOffering(Semester semester) {
        String stamp = String.valueOf(System.nanoTime());
        Subject subject = subjectRepository.save(Subject.builder()
                .code("SUB-" + stamp).name("Subject " + stamp).description("Test")
                .creditHours("3").status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return subjectOfferingRepository.save(SubjectOffering.builder()
                .subject(subject).semester(semester)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String sessionBody(Long programId, String name, String code) {
        return "{"
                + "\"name\":\"" + name + "\","
                + "\"code\":\"" + code + "\","
                + "\"description\":\"Test\","
                + "\"programId\":" + programId
                + "}";
    }

    private String semesterBody(Long academicSessionId, String name, String code) {
        return "{"
                + "\"name\":\"" + name + "\","
                + "\"code\":\"" + code + "\","
                + "\"year\":1,"
                + "\"academicSessionId\":" + academicSessionId
                + "}";
    }

    private String programBody(Long departmentId, String name, String code) {
        return "{"
                + "\"name\":\"" + name + "\","
                + "\"code\":\"" + code + "\","
                + "\"duration\":\"4yr\","
                + "\"description\":\"Test\","
                + "\"departmentId\":" + departmentId
                + "}";
    }

    private String studentBody(Section section, String rollNumber, String email, String status) {
        return "{"
                + "\"rollNumber\":\"" + rollNumber + "\","
                + "\"email\":\"" + email + "\","
                + "\"name\":\"Rahul Kumar\","
                + "\"gender\":\"M\","
                + "\"fatherName\":\"Father\","
                + "\"motherName\":\"Mother\","
                + "\"photoUrl\":\"\","
                + "\"enrollmentNumber\":\"ENR-" + System.nanoTime() + "\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"status\":\"" + status + "\","
                + "\"programId\":" + section.getBatch().getAcademicSession().getProgram().getId() + ","
                + "\"batchId\":" + section.getBatch().getId() + ","
                + "\"sectionId\":" + section.getId()
                + "}";
    }

    @Test
    void sessionReassignment_toDifferentProgram_withBatch_isRejected409_stateUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("M911");
        Program cs = createProgram(dept, "Computer Science", "CS");
        Program ee = createProgram(dept, "Electrical Engineering", "EE");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessB = createSession(ee, "SessB");
        Batch batchA = createBatch(sessA, "B1", "B1");

        mockMvc.perform(put("/api/admin/academic-sessions/" + sessA.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(ee.getId(), sessA.getName(), sessA.getCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move academic session to a different program while batches/students exist"));

        mockMvc.perform(get("/api/admin/academic-sessions/" + sessA.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program.id").value(cs.getId()));

        mockMvc.perform(get("/api/admin/batches/" + batchA.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessA.getId()))
                .andExpect(jsonPath("$.program").value("Computer Science"));
    }

    @Test
    void sessionReassignment_toDifferentProgram_withoutBatch_allows() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("M911");
        Program cs = createProgram(dept, "Computer Science", "CS");
        Program ee = createProgram(dept, "Electrical Engineering", "EE");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessB = createSession(ee, "SessB");

        mockMvc.perform(put("/api/admin/academic-sessions/" + sessA.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(ee.getId(), sessA.getName(), sessA.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(ee.getId()));
    }

    @Test
    void semesterMove_toDifferentSession_withOffering_isRejected409_stateUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("M911");
        Program cs = createProgram(dept, "Computer Science", "CS");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessB = createSession(cs, "SessB");
        Semester semA = createSemester(sessA, "Sem1", "SEM1");
        createOffering(semA);

        mockMvc.perform(put("/api/admin/semesters/" + semA.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody(sessB.getId(), semA.getName(), semA.getCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move semester to a different academic session while subject offerings exist"));

        mockMvc.perform(get("/api/admin/semesters/" + semA.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessA.getId()));
    }

    @Test
    void semesterMove_toDifferentSession_withoutOffering_allows() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("M911");
        Program cs = createProgram(dept, "Computer Science", "CS");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessB = createSession(cs, "SessB");
        Semester semA = createSemester(sessA, "Sem1", "SEM1");

        mockMvc.perform(put("/api/admin/semesters/" + semA.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody(sessB.getId(), semA.getName(), semA.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessB.getId()));
    }

    @Test
    void semesterDelete_withOffering_isRejected409_notDeleted() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("M911");
        Program cs = createProgram(dept, "Computer Science", "CS");
        AcademicSession sessA = createSession(cs, "SessA");
        Semester semA = createSemester(sessA, "Sem1", "SEM1");
        createOffering(semA);

        mockMvc.perform(delete("/api/admin/semesters/" + semA.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete semester while subject offerings exist"));

        mockMvc.perform(get("/api/admin/semesters/" + semA.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(semA.getName()));
    }

    @Test
    void programMove_toDifferentDepartment_withSessions_isRejected409_stateUnchanged() throws Exception {
        String admin = adminToken();
        Department csDept = createDepartment("M911");
        Department eeDept = createDepartment("M911");
        Program program = createProgram(csDept, "Computer Science", "CS");
        AcademicSession sessA = createSession(program, "SessA");

        mockMvc.perform(put("/api/admin/programs/" + program.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programBody(eeDept.getId(), program.getName(), program.getCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot move program to a different department while academic sessions exist"));

        mockMvc.perform(get("/api/admin/programs/" + program.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.id").value(csDept.getId()));
    }

    @Test
    void programMove_toDifferentDepartment_withoutSessions_allows() throws Exception {
        String admin = adminToken();
        Department csDept = createDepartment("M911");
        Department eeDept = createDepartment("M911");
        Program program = createProgram(csDept, "Computer Science", "CS");

        mockMvc.perform(put("/api/admin/programs/" + program.getId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programBody(eeDept.getId(), program.getName(), program.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.id").value(eeDept.getId()));
    }

    @Test
    void createStudent_sameProgramNameDifferentProgramId_isRejected400() throws Exception {
        String admin = adminToken();
        Department csDept = createDepartment("M911");
        Department eeDept = createDepartment("M911");
        Program cs = createProgram(csDept, "Computer Science", "CS");
        Program otherSameName = createProgram(eeDept, "Computer Science", "CSA");
        AcademicSession sessA = createSession(otherSameName, "SessA");
        Batch batchA = createBatch(sessA, "B1", "B1");
        Section sectionA = createSection(batchA, "A", "A");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"rollNumber\":\"STU" + System.nanoTime() + "\","
                                + "\"email\":\"stusamenamediffid@dagacs.local\","
                                + "\"name\":\"Rahul Kumar\","
                                + "\"gender\":\"M\","
                                + "\"fatherName\":\"Father\","
                                + "\"motherName\":\"Mother\","
                                + "\"photoUrl\":\"\","
                                + "\"enrollmentNumber\":\"ENR-" + System.nanoTime() + "\","
                                + "\"age\":20,"
                                + "\"admissionDate\":\"2026-01-01\","
                                + "\"status\":\"ACTIVE\","
                                + "\"academicSessionId\":" + sessA.getId() + ","
                                + "\"programId\":" + cs.getId() + ","
                                + "\"batchId\":" + batchA.getId() + ","
                                + "\"sectionId\":" + sectionA.getId()
                                + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Academic session program does not match student program"));
    }
}