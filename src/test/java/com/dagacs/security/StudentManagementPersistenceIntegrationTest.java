package com.dagacs.security;

import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Department;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.entity.Student;
import com.dagacs.repository.AcademicSessionRepository;
import com.dagacs.repository.BatchRepository;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.ProgramRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.StudentManagementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-persistence acceptance test for the M5.2 minimal payload.
 * <p>
 * Unlike the mocked service unit tests, this test drives the ACTUAL controller,
 * service, and Spring Data JPA repository against the real database. It proves
 * that a request containing ONLY the five approved mandatory fields persists
 * successfully: the frozen {@code students} table columns that are NOT NULL are
 * satisfied by the service's existing neutral defaults (blank string / age 0 /
 * status ACTIVE) without any entity or schema change.
 * </p>
 * <p>
 * All master-data references and the created student live inside the test
 * transaction and are rolled back, so no production/demo data is written.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StudentManagementPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

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
    private StudentManagementRepository studentManagementRepository;

    private Section createTestSection() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        Program program = programRepository.save(Program.builder()
                .name("Computer Science").code("CS").duration("4yr")
                .description("Test").department(dept).build());
        AcademicSession session = academicSessionRepository.save(AcademicSession.builder()
                .name("Sess-" + System.nanoTime()).code("S")
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

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void minimalPayload_persistsThroughRealRepository() throws Exception {
        Section section = createTestSection();
        String token = adminToken();
        String rollNumber = "STU" + System.nanoTime() + "-MIN";

        String minimalBody = "{"
                + "\"rollNumber\":\"" + rollNumber + "\","
                + "\"name\":\"Rahul Kumar\","
                + "\"programId\":" + section.getBatch().getAcademicSession().getProgram().getId() + ","
                + "\"batchId\":" + section.getBatch().getId() + ","
                + "\"sectionId\":" + section.getId()
                + "}";

        MvcResult createdResult = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollNumber").value(rollNumber))
                .andExpect(jsonPath("$.name").value("Rahul Kumar"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.programName").value("Computer Science"))
                .andExpect(jsonPath("$.batchName").value("B1"))
                .andExpect(jsonPath("$.sectionName").value("A"))
                .andReturn();
        long id = objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asLong();

        // Read path: the created student is visible through the real GET API.
        mockMvc.perform(get("/api/admin/students/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.rollNumber").value(rollNumber));

        // Force a re-query from the real database and verify persisted state.
        entityManager.flush();
        entityManager.clear();
        Student persisted = studentManagementRepository.findById(id).orElseThrow();
        assertEquals(rollNumber, persisted.getRollNumber());
        assertEquals("Rahul Kumar", persisted.getName());
        assertEquals("ACTIVE", persisted.getStatus());
        assertNotNull(persisted.getProgram());
        assertNotNull(persisted.getBatch());
        assertNotNull(persisted.getSection());
        assertEquals(section.getBatch().getAcademicSession().getProgram().getId(),
                persisted.getProgram().getId());
        assertEquals(section.getBatch().getId(), persisted.getBatch().getId());
        assertEquals(section.getId(), persisted.getSection().getId());

        // Optional fields collapsed to neutral values - no NOT NULL violation.
        assertEquals("", persisted.getGender());
        assertEquals("", persisted.getFatherName());
        assertEquals("", persisted.getMotherName());
        assertEquals("", persisted.getPhotoUrl());
        assertEquals("", persisted.getEnrollmentNumber());
        assertEquals("", persisted.getAdmissionDate());
        assertEquals(0, persisted.getAge());
        assertNull(persisted.getEmail());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getUpdatedAt());
    }
}