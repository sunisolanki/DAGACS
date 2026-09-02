package com.dagacs.controller;

import com.dagacs.dto.ProgramDTO;
import com.dagacs.service.ProgramService;
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
class ProgramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProgramService programService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProgram_valid_returns201() throws Exception {
        ProgramDTO dto = new ProgramDTO();
        dto.setName("M.Tech CSE");
        dto.setCode("MTCSE");
        dto.setDuration("2 years");
        when(programService.saveProgram(any(ProgramDTO.class))).thenReturn(dto);

        mockMvc.perform(post("/api/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M.Tech CSE\",\"code\":\"MTCSE\",\"duration\":\"2 years\",\"departmentId\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllPrograms_returns200() throws Exception {
        ProgramDTO dto = new ProgramDTO();
        dto.setName("M.Tech CSE");
        dto.setCode("MTCSE");
        when(programService.getAllPrograms()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/programs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getProgramById_returns200() throws Exception {
        ProgramDTO dto = new ProgramDTO();
        dto.setName("M.Tech CSE");
        dto.setCode("MTCSE");
        when(programService.getProgramById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/programs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("M.Tech CSE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getProgramsByDepartment_returns200() throws Exception {
        ProgramDTO dto = new ProgramDTO();
        dto.setName("M.Tech CSE");
        dto.setCode("MTCSE");
        when(programService.getProgramsByDepartment(1L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/programs/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProgram_returns200() throws Exception {
        ProgramDTO dto = new ProgramDTO();
        dto.setName("M.Tech CSE");
        dto.setCode("MTCSE");
        when(programService.updateProgram(eq(1L), any(ProgramDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/programs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M.Tech CSE\",\"code\":\"MTCSE\",\"duration\":\"2 years\",\"departmentId\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProgram_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/programs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void createProgram_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M.Tech CSE\",\"code\":\"MTCSE\",\"duration\":\"2 years\",\"departmentId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createProgram_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M.Tech CSE\",\"code\":\"MTCSE\",\"duration\":\"2 years\",\"departmentId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProgram_missingDepartment_returns400() throws Exception {
        when(programService.saveProgram(any(ProgramDTO.class)))
                .thenThrow(new com.dagacs.exception.AuthException("Department is required", 400));

        mockMvc.perform(post("/api/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M.Tech CSE\",\"code\":\"MTCSE\",\"duration\":\"2 years\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProgram_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"MTCSE\",\"duration\":\"2 years\",\"departmentId\":1}"))
                .andExpect(status().isBadRequest());
    }
}