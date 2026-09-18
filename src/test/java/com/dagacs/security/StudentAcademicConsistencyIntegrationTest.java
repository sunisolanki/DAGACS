package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * True end-to-end M9.9 academic-consistency enforcement: real ADMIN JWT login
 * through {@code /api/auth/login}, batch/section reassignment guards verified.
 * <p>
 * Proves that a batch cannot be moved to a different program while students are
 * assigned (409, state unchanged), a section cannot be moved to another batch
 * while students are assigned (409, state unchanged), empty containers can still
 * be moved, and a same-program session move with students remains allowed.
 * </p>
 * <p>
 * Everything (master-data references + students) is created inside the test
 * transaction and rolled back, so no production/demo data is written.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentAcademicConsistencyIntegrationTest {

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
                + "\"enrollmentNumber\":\"ENR-" + System.nanoTime() + "\","
                + "\"age\":20,"
                + "\"admissionDate\":\"2026-01-01\","
                + "\"status\":\"" + status + "\","
                + "\"academicSessionId\":" + section.getBatch().getAcademicSession().getId() + ","
                + "\"programId\":" + section.getBatch().getAcademicSession().getProgram().getId() + ","
                + "\"batchId\":" + section.getBatch().getId() + ","
                + "\"sectionId\":" + section.getId()
                + "}";
    }

    private long createStudent(String token, Section section, String rollNumber, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, rollNumber, email, "ACTIVE")))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String batchBody(Long academicSessionId) {
        return "{"
                + "\"batchCode\":\"BC-" + System.nanoTime() + "\","
                + "\"name\":\"Batch " + System.nanoTime() + "\","
                + "\"year\":2026,"
                + "\"academicSessionId\":" + academicSessionId + ","
                + "\"maxCapacity\":60"
                + "}";
    }

    private String sectionBody(Long batchId, String name) {
        return "{"
                + "\"sectionCode\":\"SC-" + System.nanoTime() + "\","
                + "\"name\":\"" + name + "\","
                + "\"maxCapacity\":30,"
                + "\"batchId\":" + batchId
                + "}";
    }

    @Test
    void batchReassignment_toDifferentProgram_withStudents_isRejected409_stateUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("Consistency");
        Program cs = createProgram(dept, "Computer Science", "CS");
        Program ee = createProgram(dept, "Electrical Engineering", "EE");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessB = createSession(ee, "SessB");
        Batch batchA = createBatch(sessA, "B1", "B1");
        Section sectionA = createSection(batchA, "A", "A");
        long studentId = createStudent(admin, sectionA,
                "STU" + System.nanoTime(), "stubatch@dagacs.local");
        long batchId = batchA.getId();

        mockMvc.perform(put("/api/admin/batches/" + batchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessB.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/batches/" + batchId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessA.getId()))
                .andExpect(jsonPath("$.program").value("Computer Science"));

        mockMvc.perform(get("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.sectionId").value(sectionA.getId()))
                .andExpect(jsonPath("$.programId").value(cs.getId()));
    }

    @Test
    void sectionReassignment_toDifferentBatch_withStudents_isRejected409_stateUnchanged() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("Consistency");
        Program cs = createProgram(dept, "Computer Science", "CS");
        AcademicSession sessA = createSession(cs, "SessA");
        Batch batchA = createBatch(sessA, "B1", "B1");
        Batch batchB = createBatch(sessA, "B2", "B2");
        Section sectionA = createSection(batchA, "A", "A");
        long studentId = createStudent(admin, sectionA,
                "STU" + System.nanoTime(), "stusection@dagacs.local");
        long sectionId = sectionA.getId();

        mockMvc.perform(put("/api/admin/sections/" + sectionId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody(batchB.getId(), "A")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/sections/" + sectionId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchA.getId()));

        mockMvc.perform(get("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchA.getId()))
                .andExpect(jsonPath("$.sectionId").value(sectionId));
    }

    @Test
    void batchReassignment_withoutStudents_allows() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("Consistency");
        Program cs = createProgram(dept, "Computer Science", "CS");
        Program ee = createProgram(dept, "Electrical Engineering", "EE");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessB = createSession(ee, "SessB");
        Batch batchX = createBatch(sessA, "BX", "BX");
        long batchId = batchX.getId();

        mockMvc.perform(put("/api/admin/batches/" + batchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessB.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/batches/" + batchId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessB.getId()))
                .andExpect(jsonPath("$.program").value("Electrical Engineering"));
    }

    @Test
    void sectionReassignment_withoutStudents_allows() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("Consistency");
        Program cs = createProgram(dept, "Computer Science", "CS");
        AcademicSession sessA = createSession(cs, "SessA");
        Batch batchA = createBatch(sessA, "B1", "B1");
        Batch batchB = createBatch(sessA, "B2", "B2");
        Section sectionY = createSection(batchA, "Y", "Y");
        long sectionId = sectionY.getId();

        mockMvc.perform(put("/api/admin/sections/" + sectionId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody(batchB.getId(), "Y")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sections/" + sectionId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchB.getId()));
    }

    @Test
    void batchReassignment_sameProgram_withStudents_allows() throws Exception {
        String admin = adminToken();
        Department dept = createDepartment("Consistency");
        Program cs = createProgram(dept, "Computer Science", "CS");
        AcademicSession sessA = createSession(cs, "SessA");
        AcademicSession sessA2 = createSession(cs, "SessA2");
        Batch batchA = createBatch(sessA, "B1", "B1");
        Section sectionA = createSection(batchA, "A", "A");
        createStudent(admin, sectionA,
                "STU" + System.nanoTime(), "stusame@dagacs.local");
        long batchId = batchA.getId();

        mockMvc.perform(put("/api/admin/batches/" + batchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(sessA2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/batches/" + batchId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessionId").value(sessA2.getId()))
                .andExpect(jsonPath("$.program").value("Computer Science"));
    }
}