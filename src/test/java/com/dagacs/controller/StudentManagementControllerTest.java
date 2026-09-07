package com.dagacs.controller;

import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.StudentManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentManagementControllerTest {

    private static final String VALID_BODY = "{"
            + "\"rollNumber\":\"2201CE001\","
            + "\"email\":\"student1@dagacs.local\","
            + "\"name\":\"Rahul Kumar\","
            + "\"gender\":\"M\","
            + "\"fatherName\":\"Father\","
            + "\"motherName\":\"Mother\","
            + "\"photoUrl\":\"\","
            + "\"enrollmentNumber\":\"ENR-001\","
            + "\"age\":20,"
            + "\"admissionDate\":\"2026-01-01\","
            + "\"programId\":1,"
            + "\"batchId\":1,"
            + "\"sectionId\":1"
            + "}";

    private static final String MINIMAL_BODY = "{"
            + "\"rollNumber\":\"2201CE001\","
            + "\"name\":\"Rahul Kumar\","
            + "\"programId\":1,"
            + "\"batchId\":1,"
            + "\"sectionId\":1"
            + "}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentManagementService studentManagementService;

    private StudentManagementDTO dto(long id, String rollNumber, String name, String status) {
        return StudentManagementDTO.builder()
                .id(id)
                .rollNumber(rollNumber)
                .email("student1@dagacs.local")
                .name(name)
                .gender("M")
                .fatherName("Father")
                .motherName("Mother")
                .photoUrl("")
                .enrollmentNumber("ENR-001")
                .age(20)
                .admissionDate("2026-01-01")
                .status(status)
                .programId(1L)
                .programName("Computer Science")
                .batchId(1L)
                .batchName("B1")
                .sectionId(1L)
                .sectionName("A")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_valid_returns201() throws Exception {
        when(studentManagementService.createStudent(any())).thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE"));

        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollNumber").value("2201CE001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllStudents_returns200() throws Exception {
        when(studentManagementService.getAllStudents())
                .thenReturn(List.of(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE")));

        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].programName").value("Computer Science"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStudentById_returns200() throws Exception {
        when(studentManagementService.getStudentById(1L)).thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE"));

        mockMvc.perform(get("/api/admin/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahul Kumar"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStudentById_unknown_returns404() throws Exception {
        when(studentManagementService.getStudentById(99L)).thenThrow(new AuthException("Student not found", 404));

        mockMvc.perform(get("/api/admin/students/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStudent_returns200() throws Exception {
        when(studentManagementService.updateStudent(eq(1L), any())).thenReturn(dto(1L, "2201CE001", "Rahul Updated", "ACTIVE"));

        mockMvc.perform(put("/api/admin/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahul Updated"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStudentStatus_returns200() throws Exception {
        when(studentManagementService.setStudentStatus(eq(1L), eq("INACTIVE")))
                .thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "INACTIVE"));

        mockMvc.perform(patch("/api/admin/students/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        verify(studentManagementService).setStudentStatus(1L, "INACTIVE");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_minimalMandatoryOnly_passesValidationAndReachesService() throws Exception {
        when(studentManagementService.createStudent(any()))
                .thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE"));

        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollNumber").value("2201CE001"));
        verify(studentManagementService).createStudent(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_blankRollNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"\",\"name\":\"Rahul\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStudentStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/students/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"2201CE001\",\"programId\":1,"
                                + "\"batchId\":1,\"sectionId\":1}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_missingProgramId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"2201CE001\",\"name\":\"Rahul\","
                                + "\"batchId\":1,\"sectionId\":1}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_missingBatchId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"2201CE001\",\"name\":\"Rahul\","
                                + "\"programId\":1,\"sectionId\":1}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_missingSectionId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"2201CE001\",\"name\":\"Rahul\","
                                + "\"programId\":1,\"batchId\":1}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"2201CE001\",\"name\":\"Rahul\","
                                + "\"email\":\"not-an-email\",\"programId\":1,"
                                + "\"batchId\":1,\"sectionId\":1}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStudent_minimalMandatoryOnly_passesValidationAndReachesService() throws Exception {
        when(studentManagementService.updateStudent(eq(1L), any()))
                .thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE"));

        mockMvc.perform(put("/api/admin/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_BODY))
                .andExpect(status().isOk());
        verify(studentManagementService).updateStudent(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStudent_duplicateConflict_returns409() throws Exception {
        when(studentManagementService.createStudent(any()))
                .thenThrow(new AuthException("Roll number already exists", 409));

        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createStudent_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void updateStudentStatus_teacher_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/students/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void createStudent_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }
}