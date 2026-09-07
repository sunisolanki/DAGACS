package com.dagacs.security;

import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.TeacherRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-JWT + real-MySQL concurrency test for the one-HOD-per-department
 * invariant (M6.1 correction).
 * <p>
 * This test is intentionally NOT {@code @Transactional}: each concurrent
 * designation HTTP request must run in its own committed transaction so the
 * pessimistic row locks genuinely serialize against the real database. After
 * both requests complete, the database invariant
 * {@code COUNT(teachers WHERE department_id = D AND is_hod = true) <= 1} is
 * asserted directly. The created department/teachers are removed in cleanup so
 * the database returns to a clean state.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class HodDesignationConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private final List<Long> createdTeacherIds = new ArrayList<>();
    private final List<Long> createdDepartmentIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdTeacherIds) {
            teacherRepository.findById(id).ifPresent(teacherRepository::delete);
        }
        for (Long id : createdDepartmentIds) {
            departmentRepository.findById(id).ifPresent(departmentRepository::delete);
        }
        createdTeacherIds.clear();
        createdDepartmentIds.clear();
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dagacs.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Department createDepartment() {
        Department dept = departmentRepository.save(Department.builder()
                .name("Dept-" + System.nanoTime()).code("D").description("Test")
                .createdBy("test").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        createdDepartmentIds.add(dept.getId());
        return dept;
    }

    private Teacher createEligibleTeacher(String email) {
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .email(email).password("ignored").fullName("Prof " + email)
                .phone("").designation("Professor").status("ACTIVE")
                .avatarUrl("").isHod(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        createdTeacherIds.add(teacher.getId());
        return teacher;
    }

    private long countHods(Long departmentId) {
        return teacherRepository.countByDepartmentIdAndIsHodTrue(departmentId);
    }

    @Test
    void twoConcurrentDesignationsToSameDepartment_neverCreateTwoHods() throws Exception {
        Department dept = createDepartment();
        Teacher teacherA = createEligibleTeacher("conc-a-" + System.nanoTime() + "@dagacs.local");
        Teacher teacherB = createEligibleTeacher("conc-b-" + System.nanoTime() + "@dagacs.local");
        String token = adminToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<MvcResult> reqA = executor.submit(() -> {
            start.await();
            return mockMvc.perform(patch("/api/admin/teachers/" + teacherA.getId() + "/hod")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                    .andReturn();
        });

        Future<MvcResult> reqB = executor.submit(() -> {
            start.await();
            return mockMvc.perform(patch("/api/admin/teachers/" + teacherB.getId() + "/hod")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"designated\": true, \"departmentId\": " + dept.getId() + "}"))
                    .andReturn();
        });

        start.countDown();
        int statusA = reqA.get().getResponse().getStatus();
        int statusB = reqB.get().getResponse().getStatus();
        executor.shutdown();

        // Exactly one succeeds; the colliding designation is rejected with 409.
        long okCount = List.of(statusA, statusB).stream().filter(s -> s == 200 || s == 201).count();
        long conflictCount = List.of(statusA, statusB).stream().filter(s -> s == 409).count();
        assertEquals(1, okCount, "exactly one designation must succeed");
        assertEquals(1, conflictCount, "the colliding designation must be rejected with 409");

        // Direct database invariant assertion.
        assertTrue(countHods(dept.getId()) <= 1,
                "invariant violated: more than one HOD for the department");
    }

    @Test
    void concurrentSameTeacherMoves_endInConsistentSingleDepartment() throws Exception {
        Department deptY = createDepartment();
        Department deptZ = createDepartment();
        Teacher teacher = createEligibleTeacher("conc-m-" + System.nanoTime() + "@dagacs.local");
        String token = adminToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<MvcResult> reqY = executor.submit(() -> {
            start.await();
            return mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"designated\": true, \"departmentId\": " + deptY.getId() + "}"))
                    .andReturn();
        });

        Future<MvcResult> reqZ = executor.submit(() -> {
            start.await();
            return mockMvc.perform(patch("/api/admin/teachers/" + teacher.getId() + "/hod")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"designated\": true, \"departmentId\": " + deptZ.getId() + "}"))
                    .andReturn();
        });

        start.countDown();
        reqY.get();
        reqZ.get();
        executor.shutdown();

        // The single teacher ends up HOD of exactly one of the two departments.
        Teacher persisted = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(persisted.getIsHod()));
        assertEquals(1, countHods(deptY.getId()) + countHods(deptZ.getId()),
                "the teacher must be HOD of exactly one department, leaving the other with no HOD");
    }
}