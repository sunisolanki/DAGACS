package com.dagacs.controller;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.service.AcademicSessionService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AcademicSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcademicSessionService academicSessionService;

    private String validJson() {
        return "{\"name\":\"2026-27\",\"code\":\"2026-27\",\"programId\":1}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_valid_returns201() throws Exception {
        AcademicSessionDTO dto = new AcademicSessionDTO();
        dto.setName("2026-27");
        when(academicSessionService.saveSession(any(AcademicSessionDTO.class))).thenReturn(dto);

        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllSessions_returns200() throws Exception {
        AcademicSessionDTO dto = new AcademicSessionDTO();
        dto.setName("2026-27");
        when(academicSessionService.getAllSessions()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/academic-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSessionById_returns200() throws Exception {
        AcademicSessionDTO dto = new AcademicSessionDTO();
        dto.setName("2026-27");
        when(academicSessionService.getSessionById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/academic-sessions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026-27"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSession_returns200() throws Exception {
        AcademicSessionDTO dto = new AcademicSessionDTO();
        dto.setName("2026-27");
        when(academicSessionService.updateSession(eq(1L), any(AcademicSessionDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/academic-sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSession_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/academic-sessions/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSessionsByProgram_returns200() throws Exception {
        AcademicSessionDTO dto = new AcademicSessionDTO();
        dto.setName("2026-27");
        when(academicSessionService.getSessionsByProgram(1L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/programs/1/academic-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"2026-27\",\"programId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026-27\",\"code\":\"\",\"programId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_missingProgram_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026-27\",\"code\":\"2026-27\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_invalidProgram_returns404() throws Exception {
        when(academicSessionService.saveSession(any(AcademicSessionDTO.class)))
                .thenThrow(new com.dagacs.exception.AuthException("Program not found with ID: 99", 404));

        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026-27\",\"code\":\"2026-27\",\"programId\":99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_duplicate_returns409() throws Exception {
        when(academicSessionService.saveSession(any(AcademicSessionDTO.class)))
                .thenThrow(new com.dagacs.exception.AuthException(
                        "Academic session already exists for this program: 2026-27", 409));

        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSession_withSemesters_returns409() throws Exception {
        doThrow(new com.dagacs.exception.AuthException(
                "Cannot delete academic session. Semester(s) exist: Semester 1", 409))
                .when(academicSessionService).deleteSession(1L);

        mockMvc.perform(delete("/api/admin/academic-sessions/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void createSession_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createSession_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/academic-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}
