package com.dagacs.controller;

import com.dagacs.dto.SemesterDTO;
import com.dagacs.service.SemesterService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SemesterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SemesterService semesterService;

    private String validJson() {
        return "{\"name\":\"Semester 1\",\"code\":\"SEM1\",\"year\":1,\"academicSessionId\":1}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSemester_valid_returns201() throws Exception {
        SemesterDTO dto = new SemesterDTO();
        dto.setName("Semester 1");
        when(semesterService.saveSemester(any(SemesterDTO.class))).thenReturn(dto);

        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllSemesters_returns200() throws Exception {
        SemesterDTO dto = new SemesterDTO();
        dto.setName("Semester 1");
        when(semesterService.getAllSemesters()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/semesters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSemesterById_returns200() throws Exception {
        SemesterDTO dto = new SemesterDTO();
        dto.setName("Semester 1");
        when(semesterService.getSemesterById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/semesters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Semester 1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSemester_returns200() throws Exception {
        SemesterDTO dto = new SemesterDTO();
        dto.setName("Semester 1");
        when(semesterService.updateSemester(eq(1L), any(SemesterDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/semesters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSemester_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/semesters/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSemestersBySession_returns200() throws Exception {
        SemesterDTO dto = new SemesterDTO();
        dto.setName("Semester 1");
        when(semesterService.getSemestersBySession(1L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/academic-sessions/1/semesters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSemester_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"SEM1\",\"year\":1,\"academicSessionId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSemester_missingAcademicSession_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Semester 1\",\"code\":\"SEM1\",\"year\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSemester_invalidAcademicSession_returns404() throws Exception {
        when(semesterService.saveSemester(any(SemesterDTO.class)))
                .thenThrow(new com.dagacs.exception.AuthException("Academic session not found with ID: 99", 404));

        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Semester 1\",\"code\":\"SEM1\",\"year\":1,\"academicSessionId\":99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSemester_duplicate_returns409() throws Exception {
        when(semesterService.saveSemester(any(SemesterDTO.class)))
                .thenThrow(new com.dagacs.exception.AuthException(
                        "Semester already exists for this academic session: Semester 1", 409));

        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void createSemester_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createSemester_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}
