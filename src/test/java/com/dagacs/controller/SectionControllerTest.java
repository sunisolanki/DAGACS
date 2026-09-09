package com.dagacs.controller;

import com.dagacs.dto.SectionDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.SectionService;
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
class SectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SectionService sectionService;

    private String validJson() {
        return "{\"sectionCode\":\"SEC-A\",\"name\":\"A\",\"maxCapacity\":30,\"batchId\":1}";
    }

    private SectionDTO sectionDto() {
        SectionDTO dto = new SectionDTO();
        dto.setId(1L);
        dto.setSectionCode("SEC-A");
        dto.setName("A");
        dto.setMaxCapacity(30);
        dto.setBatchId(1L);
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSection_valid_returns201() throws Exception {
        when(sectionService.saveSection(any(SectionDTO.class))).thenReturn(sectionDto());

        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("A"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllSections_returns200() throws Exception {
        when(sectionService.getAllSections()).thenReturn(List.of(sectionDto()));

        mockMvc.perform(get("/api/admin/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSectionById_returns200() throws Exception {
        when(sectionService.getSectionById(1L)).thenReturn(sectionDto());

        mockMvc.perform(get("/api/admin/sections/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSection_returns200() throws Exception {
        when(sectionService.updateSection(eq(1L), any(SectionDTO.class))).thenReturn(sectionDto());

        mockMvc.perform(put("/api/admin/sections/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSection_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/sections/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSection_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionCode\":\"SEC-A\",\"name\":\"\",\"maxCapacity\":30,\"batchId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSection_missingBatch_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionCode\":\"SEC-A\",\"name\":\"A\",\"maxCapacity\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSection_invalidBatch_returns404() throws Exception {
        when(sectionService.saveSection(any(SectionDTO.class)))
                .thenThrow(new AuthException("Batch not found with ID: 99", 404));

        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionCode\":\"SEC-A\",\"name\":\"A\",\"maxCapacity\":30,\"batchId\":99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSection_duplicateSectionCode_returns409() throws Exception {
        when(sectionService.saveSection(any(SectionDTO.class)))
                .thenThrow(new AuthException("Section code already exists: SEC-A", 409));

        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSection_missing_returns404() throws Exception {
        doThrow(new AuthException("Section not found with ID: 99", 404))
                .when(sectionService).deleteSection(99L);

        mockMvc.perform(delete("/api/admin/sections/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSection_referenced_returns409() throws Exception {
        doThrow(new AuthException("Cannot delete this section because it is referenced by attendance records.", 409))
                .when(sectionService).deleteSection(1L);

        mockMvc.perform(delete("/api/admin/sections/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void createSection_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createSection_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}
