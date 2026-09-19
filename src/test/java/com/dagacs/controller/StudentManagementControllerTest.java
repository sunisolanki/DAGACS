package com.dagacs.controller;

import com.dagacs.dto.StudentFilterOption;
import com.dagacs.dto.StudentFilterOptionsResponse;
import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentPageResponse;
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
    void searchStudents_returnsPaged200() throws Exception {
        StudentPageResponse page = StudentPageResponse.builder()
                .content(java.util.List.of(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE")))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        when(studentManagementService.searchStudents(any(), any(), any(), any(), any(), any(),
                any(), any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.size()").value(1))
                .andExpect(jsonPath("$.content[0].programName").value("Computer Science"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchStudents_withFilters_returns200() throws Exception {
        StudentPageResponse page = StudentPageResponse.builder()
                .content(java.util.List.of(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE")))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        when(studentManagementService.searchStudents(eq("rahul"), eq(1L), eq(2L), eq(3L), eq(4L), eq(5L),
                eq("ACTIVE"), eq("ACTIVE"), eq(1), eq(50))).thenReturn(page);

        mockMvc.perform(get("/api/admin/students")
                        .param("search", "rahul")
                        .param("programId", "1")
                        .param("academicSessionId", "2")
                        .param("semesterId", "3")
                        .param("batchId", "4")
                        .param("sectionId", "5")
                        .param("status", "ACTIVE")
                        .param("loginStatus", "ACTIVE")
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Rahul Kumar"));
        verify(studentManagementService).searchStudents(eq("rahul"), eq(1L), eq(2L), eq(3L), eq(4L), eq(5L),
                eq("ACTIVE"), eq("ACTIVE"), eq(1), eq(50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getFilterOptions_returns200() throws Exception {
        StudentFilterOptionsResponse response = StudentFilterOptionsResponse.builder()
                .academicSessions(java.util.List.of(StudentFilterOption.builder().id(2L).name("2026-27").build()))
                .programs(java.util.List.of())
                .semesters(java.util.List.of())
                .batches(java.util.List.of())
                .sections(java.util.List.of())
                .build();
        when(studentManagementService.getFilterOptions(eq(2L), eq(1L), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/admin/students/filter-options")
                        .param("academicSessionId", "2")
                        .param("programId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicSessions.size()").value(1))
                .andExpect(jsonPath("$.academicSessions[0].id").value(2))
                .andExpect(jsonPath("$.programs.size()").value(0));
    }

    @Test
    @WithAnonymousUser
    void searchStudents_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getFilterOptions_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/students/filter-options"))
                .andExpect(status().isForbidden());
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
    void createStudent_missingSectionId_reachesService() throws Exception {
        // Phase 2: sectionId is no longer mandatory at the bean-validation layer;
        // the service enforces section-required-iff-batch-has-sections.
        mockMvc.perform(post("/api/admin/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rollNumber\":\"2201CE001\",\"name\":\"Rahul\","
                                + "\"programId\":1,\"batchId\":1}"))
                .andExpect(status().isCreated());
        verify(studentManagementService).createStudent(any());
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void provisionLogin_valid_returns201WithTemporaryPassword() throws Exception {
        StudentManagementDTO linked = StudentManagementDTO.builder()
                .id(1L)
                .rollNumber("2201CE001")
                .email("student1@dagacs.local")
                .name("Rahul Kumar")
                .status("ACTIVE")
                .loginLinked(true)
                .loginStatus("ACTIVE")
                .mustChangePassword(true)
                .temporaryPassword("TempPass#2026")
                .build();
        when(studentManagementService.provisionLogin(1L)).thenReturn(linked);

        mockMvc.perform(post("/api/admin/students/1/login"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginLinked").value(true))
                .andExpect(jsonPath("$.loginStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").value("TempPass#2026"));
        verify(studentManagementService).provisionLogin(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void provisionLogin_studentWithoutEmail_returns400() throws Exception {
        when(studentManagementService.provisionLogin(1L))
                .thenThrow(new AuthException("The student has no email to link a login to", 400));

        mockMvc.perform(post("/api/admin/students/1/login"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The student has no email to link a login to"));
        verify(studentManagementService).provisionLogin(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setLoginStatus_returns200() throws Exception {
        when(studentManagementService.setLoginStatus(eq(1L), eq("INACTIVE")))
                .thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE"));

        mockMvc.perform(patch("/api/admin/students/1/login/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value("2201CE001"));
        verify(studentManagementService).setLoginStatus(1L, "INACTIVE");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setLoginStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/students/1/login/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).setLoginStatus(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setLoginPassword_returns200() throws Exception {
        when(studentManagementService.setLoginPassword(eq(1L), eq("NewPass#9")))
                .thenReturn(dto(1L, "2201CE001", "Rahul Kumar", "ACTIVE"));

        mockMvc.perform(put("/api/admin/students/1/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass#9\"}"))
                .andExpect(status().isOk());
        verify(studentManagementService).setLoginPassword(1L, "NewPass#9");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setLoginPassword_shortPassword_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/students/1/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verify(studentManagementService, never()).setLoginPassword(any(), any());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void provisionLogin_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/students/1/login"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void setLoginPassword_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/admin/students/1/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass#9\"}"))
                .andExpect(status().isUnauthorized());
    }
}