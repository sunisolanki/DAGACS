package com.dagacs.controller;

import com.dagacs.dto.BatchDTO;
import com.dagacs.dto.SectionDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.BatchService;
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
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BatchService batchService;

    @MockBean
    private SectionService sectionService;

    private String validJson() {
        return "{\"batchCode\":\"B2026\",\"name\":\"2026 Batch\",\"year\":2026," +
                "\"academicSessionId\":1,\"maxCapacity\":60}";
    }

    private BatchDTO batchDto() {
        BatchDTO dto = new BatchDTO();
        dto.setId(1L);
        dto.setBatchCode("B2026");
        dto.setName("2026 Batch");
        dto.setYear(2026);
        dto.setAcademicSessionId(1L);
        dto.setMaxCapacity(60);
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBatch_valid_returns201() throws Exception {
        when(batchService.saveBatch(any(BatchDTO.class))).thenReturn(batchDto());

        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("2026 Batch"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllBatches_returns200() throws Exception {
        when(batchService.getAllBatches()).thenReturn(List.of(batchDto()));

        mockMvc.perform(get("/api/admin/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBatchById_returns200() throws Exception {
        when(batchService.getBatchById(1L)).thenReturn(batchDto());

        mockMvc.perform(get("/api/admin/batches/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026 Batch"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateBatch_returns200() throws Exception {
        when(batchService.updateBatch(eq(1L), any(BatchDTO.class))).thenReturn(batchDto());

        mockMvc.perform(put("/api/admin/batches/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteBatch_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/batches/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSectionsByBatch_returns200() throws Exception {
        SectionDTO dto = new SectionDTO();
        dto.setName("A");
        when(sectionService.getSectionsByBatch(1L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/batches/1/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBatch_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"B2026\",\"name\":\"\",\"year\":2026," +
                                "\"academicSessionId\":1,\"maxCapacity\":60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBatch_missingSession_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"B2026\",\"name\":\"2026 Batch\",\"year\":2026," +
                                "\"maxCapacity\":60}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBatch_invalidSession_returns404() throws Exception {
        when(batchService.saveBatch(any(BatchDTO.class)))
                .thenThrow(new AuthException("Academic session not found with ID: 99", 404));

        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"B2026\",\"name\":\"2026 Batch\",\"year\":2026," +
                                "\"academicSessionId\":99,\"maxCapacity\":60}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBatch_duplicateBatchCode_returns409() throws Exception {
        when(batchService.saveBatch(any(BatchDTO.class)))
                .thenThrow(new AuthException("Batch code already exists: B2026", 409));

        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteBatch_withSections_returns409() throws Exception {
        doThrow(new AuthException("Cannot delete batch. Section(s) exist: A", 409))
                .when(batchService).deleteBatch(1L);

        mockMvc.perform(delete("/api/admin/batches/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void createBatch_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createBatch_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}
