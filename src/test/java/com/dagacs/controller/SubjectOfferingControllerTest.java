package com.dagacs.controller;

import com.dagacs.dto.SubjectOfferingDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.SubjectOfferingService;
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
class SubjectOfferingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubjectOfferingService subjectOfferingService;

    private String validJson() {
        return "{\"subjectId\":1,\"semesterId\":5}";
    }

    private SubjectOfferingDTO offeringDTO() {
        return SubjectOfferingDTO.builder()
                .id(1L)
                .subjectId(1L)
                .semesterId(5L)
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubjectOffering_valid_returns201() throws Exception {
        when(subjectOfferingService.saveSubjectOffering(any()))
                .thenReturn(offeringDTO());

        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(1))
                .andExpect(jsonPath("$.semesterId").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubjectOffering_ignoresRedundantContext_derivesFromSemester() throws Exception {
        when(subjectOfferingService.saveSubjectOffering(any()))
                .thenReturn(offeringDTO());

        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":1,\"semesterId\":5,"
                                + "\"programId\":999,\"academicSessionId\":999,"
                                + "\"departmentId\":999,\"teacherId\":999,"
                                + "\"sectionId\":999,\"batchId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(1))
                .andExpect(jsonPath("$.semesterId").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllSubjectOfferings_returns200() throws Exception {
        when(subjectOfferingService.getAllSubjectOfferings())
                .thenReturn(List.of(offeringDTO()));

        mockMvc.perform(get("/api/admin/subject-offerings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSubjectOfferingById_returns200() throws Exception {
        when(subjectOfferingService.getSubjectOfferingById(1L)).thenReturn(offeringDTO());

        mockMvc.perform(get("/api/admin/subject-offerings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSubjectOfferingById_missing_returns404() throws Exception {
        when(subjectOfferingService.getSubjectOfferingById(99L))
                .thenThrow(new AuthException("Subject offering not found with ID: 99", 404));

        mockMvc.perform(get("/api/admin/subject-offerings/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSubjectOffering_returns200() throws Exception {
        when(subjectOfferingService.updateSubjectOffering(eq(1L), any()))
                .thenReturn(offeringDTO());

        mockMvc.perform(put("/api/admin/subject-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSubjectOffering_duplicate_returns409() throws Exception {
        when(subjectOfferingService.updateSubjectOffering(eq(1L), any()))
                .thenThrow(new AuthException("Subject is already mapped to this semester", 409));

        mockMvc.perform(put("/api/admin/subject-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSubjectOffering_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/subject-offerings/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubjectOffering_missingSubject_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"semesterId\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubjectOffering_missingSemester_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubjectOffering_invalidSubject_returns404() throws Exception {
        when(subjectOfferingService.saveSubjectOffering(any()))
                .thenThrow(new AuthException("Subject not found with ID: 99", 404));

        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":99,\"semesterId\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubjectOffering_duplicate_returns409() throws Exception {
        when(subjectOfferingService.saveSubjectOffering(any()))
                .thenThrow(new AuthException("Subject is already mapped to this semester", 409));

        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void createSubjectOffering_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createSubjectOffering_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/subject-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}