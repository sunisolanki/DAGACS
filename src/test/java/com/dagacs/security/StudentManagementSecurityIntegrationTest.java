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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * True end-to-end M5.2 security verification: real JWT login for every role.
 * <p>
 * Proves the ADMIN master-management surface is system-wide and ADMIN-only:
 * ADMIN can create/list/get/update/change-status; HOD, TEACHER and STUDENT are
 * forbidden on every endpoint; anonymous gets 401.
 * </p>
 * <p>
 * Everything (master-data references + created students) is created inside the
 * test transaction and rolled back, so no production/demo data is written.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentManagementSecurityIntegrationTest {

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

    private Section createTestSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Computer Science").code("CS").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S").semester("1")
                .durationHours(100).lecturePeriods(40).credits(20)
                .description("Test").program(program)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Batch batch = batchRepository.save(Batch.builder()
                .batchCode("Bat-" + System.nanoTime()).name("B1").year(2026)
                .program("Computer Science").maxCapacity(60).academicSession(session)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return sectionRepository.save(Section.builder()
                .sectionCode("Sec-" + System.nanoTime()).name("A").maxCapacity(30)
                .batch(batch).status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String login(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
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
    void admin_canCreateListGetUpdateAndChangeStatus() throws Exception {
        Section section = createTestSection();
        String token = login("admin@dagacs.local", "Admin@123");
        String rollNumber = "STU" + System.nanoTime() + "-ADMIN";

        MvcResult createdResult = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, rollNumber, "st-admin@dagacs.local", "ACTIVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollNumber").value(rollNumber))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.programName").value("Computer Science"))
                .andExpect(jsonPath("$.batchName").value("B1"))
                .andExpect(jsonPath("$.sectionName").value("A"))
                .andReturn();
        long id = objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/students/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.rollNumber").value(rollNumber));

        mockMvc.perform(put("/api/admin/students/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, rollNumber, "st-admin@dagacs.local", "ACTIVE")
                                .replace("Rahul Kumar", "Rahul Kumar Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahul Kumar Updated"));

        mockMvc.perform(patch("/api/admin/students/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/admin/students/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void admin_duplicateRollNumber_returns409() throws Exception {
        Section section = createTestSection();
        String token = login("admin@dagacs.local", "Admin@123");
        String rollNumber = "STU" + System.nanoTime() + "-DUP";

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, rollNumber, "dup-a@dagacs.local", "ACTIVE")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, rollNumber, "dup-b@dagacs.local", "ACTIVE")))
                .andExpect(status().isConflict());
    }

    @Test
    void hod_isForbiddenOnEveryEndpoint() throws Exception {
        assertEveryEndpointForbidden("hod@dagacs.local", "Hod@123");
    }

    @Test
    void teacher_isForbiddenOnEveryEndpoint() throws Exception {
        assertEveryEndpointForbidden("teacher@dagacs.local", "Teacher@123");
    }

    @Test
    void student_isForbiddenOnEveryEndpoint() throws Exception {
        assertEveryEndpointForbidden("student@dagacs.local", "Student@123");
    }

    private void assertEveryEndpointForbidden(String email, String password) throws Exception {
        Section section = createTestSection();
        String token = login(email, password);

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, "STU" + System.nanoTime(), "who@dagacs.local", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/students/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/students/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, "STU" + System.nanoTime(), "who@dagacs.local", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/students/1/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        Section section = createTestSection();

        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentBody(section, "STU" + System.nanoTime(), "anon@dagacs.local", "ACTIVE")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isUnauthorized());
    }
}