package com.dagacs.security;

import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.TeacherRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT, real-MySQL acceptance test for the ADMIN HOD designation mechanism
 * {@code PATCH /api/admin/teachers/{teacherId}/hod}.
 * <p>
 * Proves the one-HOD-per-department invariant and the exact role matrix against
 * the real database and security stack. All master data and teacher rows are
 * created inside the test transaction and rolled back, so no permanent test
 * data remains.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HodDesignationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Department createDepartment() {
        return departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Teacher createTeacher(String email) {
        return teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("Prof " + email)
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private String adminToken() throws Exception {
        return loginToken("admin@dagacs.local", "Admin@123");
    }

    @Test
    void admin_designate_persistsAndReadsBack() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("t1-" + System.nanoTime() + "@dagacs.local");
        String token = adminToken();

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hod").value(true))
                .andExpect(jsonPath("$.departmentName").value(dept.getName()));

        entityManager.flush();
        entityManager.clear();
        Teacher persisted = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertTrue(persisted.getIsHod());
        assertEquals(dept.getId(), persisted.getDepartment().getId());
    }

    @Test
    void admin_duplicateHod_sameDepartment_returns409() throws Exception {
        Department dept = createDepartment();
        Teacher teacherA = createTeacher("ta-" + System.nanoTime() + "@dagacs.local");
        Teacher teacherB = createTeacher("tb-" + System.nanoTime() + "@dagacs.local");
        String token = adminToken();

        // A becomes HOD of dept.
        mockMvc.perform(patch("/api/admin/teachers/" + teacherA.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isOk());

        // B cannot become HOD of the same department -> 409, no silent replacement.
        mockMvc.perform(patch("/api/admin/teachers/" + teacherB.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isConflict());

        entityManager.flush();
        entityManager.clear();
        assertTrue(teacherRepository.findById(teacherA.getId()).orElseThrow().getIsHod());
        assertFalse(teacherRepository.findById(teacherB.getId()).orElseThrow().getIsHod());
    }

    @Test
    void admin_designateWithoutDepartment_returns400() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("t-" + System.nanoTime() + "@dagacs.local");
        String token = adminToken();

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_designateUnknownTeacher_returns404() throws Exception {
        Department dept = createDepartment();
        String token = adminToken();

        mockMvc.perform(patch("/api/admin/teachers/999999/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_designateUnknownDepartment_returns404() throws Exception {
        String token = adminToken();
        mockMvc.perform(patch("/api/admin/teachers/999999/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": 999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_clearHod_succeeds() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("tc-" + System.nanoTime() + "@dagacs.local");
        teacher.setIsHod(true);
        teacher.setDepartment(dept);
        teacherRepository.save(teacher);
        String token = adminToken();

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hod").value(false));

        entityManager.flush();
        entityManager.clear();
        Teacher persisted = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertFalse(persisted.getIsHod());
        // Teacher keeps its department association.
        assertEquals(dept.getId(), persisted.getDepartment().getId());
    }

    @Test
    void admin_moveHod_targetFree_succeeds() throws Exception {
        Department deptX = createDepartment();
        Department deptY = createDepartment();
        Teacher teacher = createTeacher("tm-" + System.nanoTime() + "@dagacs.local");
        teacher.setIsHod(true);
        teacher.setDepartment(deptX);
        teacherRepository.save(teacher);
        String token = adminToken();

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + deptY.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value(deptY.getName()));

        entityManager.flush();
        entityManager.clear();
        Teacher persisted = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertTrue(persisted.getIsHod());
        // Exactly one department (deptY); deptX no longer holds an HOD.
        assertEquals(deptY.getId(), persisted.getDepartment().getId());
    }

    @Test
    void admin_moveHod_targetOccupied_returns409_noChange() throws Exception {
        Department deptX = createDepartment();
        Department deptY = createDepartment();
        Teacher teacherA = createTeacher("ma-" + System.nanoTime() + "@dagacs.local");
        Teacher teacherB = createTeacher("mb-" + System.nanoTime() + "@dagacs.local");
        teacherA.setIsHod(true);
        teacherA.setDepartment(deptX);
        teacherB.setIsHod(true);
        teacherB.setDepartment(deptY);
        teacherRepository.save(teacherA);
        teacherRepository.save(teacherB);
        String token = adminToken();

        // A tries to move to dept Y, which already has B as HOD -> 409, no change.
        mockMvc.perform(patch("/api/admin/teachers/" + teacherA.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + deptY.getId() + "}"))
                .andExpect(status().isConflict());

        entityManager.flush();
        entityManager.clear();
        Teacher pa = teacherRepository.findById(teacherA.getId()).orElseThrow();
        Teacher pb = teacherRepository.findById(teacherB.getId()).orElseThrow();
        assertTrue(pa.getIsHod());
        assertEquals(deptX.getId(), pa.getDepartment().getId());
        assertTrue(pb.getIsHod());
        assertEquals(deptY.getId(), pb.getDepartment().getId());
    }

    @Test
    void designate_hodToken_returns403() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("th-" + System.nanoTime() + "@dagacs.local");
        String token = loginToken("teacher@dagacs.local", "Teacher@123");

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void designate_studentToken_returns403() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("ts-" + System.nanoTime() + "@dagacs.local");
        String token = loginToken("student@dagacs.local", "Student@123");

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void designate_hodRoleUser_returns403() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("tgu-" + System.nanoTime() + "@dagacs.local");
        String token = loginToken("hod@dagacs.local", "Hod@123");

        mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void designate_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/teachers/1/hod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void designate_responseDoesNotExposeInternalIds() throws Exception {
        Department dept = createDepartment();
        Teacher teacher = createTeacher("tid-" + System.nanoTime() + "@dagacs.local");
        String token = adminToken();

        MvcResult result = mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(null, node.get("teacherId"));
        assertEquals(null, node.get("departmentId"));
    }
}