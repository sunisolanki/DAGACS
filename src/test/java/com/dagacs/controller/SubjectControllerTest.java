package com.dagacs.controller;

import com.dagacs.dto.SubjectDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.SubjectService;
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
class SubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubjectService subjectService;

    private String validJson() {
        return "{\"code\":\"CS101\",\"name\":\"Data Structures\"," +
                "\"description\":\"Course on data structures\",\"creditHours\":\"3\"," +
                "\"department\":\"CSE\",\"status\":\"ACTIVE\"}";
    }

    private SubjectDTO subjectDto() {
        SubjectDTO dto = new SubjectDTO();
        dto.setId(1L);
        dto.setCode("CS101");
        dto.setName("Data Structures");
        dto.setDescription("Course on data structures");
        dto.setCreditHours("3");
        dto.setDepartment("CSE");
        dto.setStatus("ACTIVE");
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubject_valid_returns201() throws Exception {
        when(subjectService.saveSubject(any(SubjectDTO.class))).thenReturn(subjectDto());

        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Data Structures"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllSubjects_returns200() throws Exception {
        when(subjectService.getAllSubjects()).thenReturn(List.of(subjectDto()));

        mockMvc.perform(get("/api/admin/subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSubjectById_returns200() throws Exception {
        when(subjectService.getSubjectById(1L)).thenReturn(subjectDto());

        mockMvc.perform(get("/api/admin/subjects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Data Structures"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSubject_returns200() throws Exception {
        when(subjectService.updateSubject(eq(1L), any(SubjectDTO.class))).thenReturn(subjectDto());

        mockMvc.perform(put("/api/admin/subjects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSubject_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/subjects/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubject_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"Data Structures\"," +
                                "\"creditHours\":\"3\",\"department\":\"CSE\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubject_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CS101\",\"name\":\"\"," +
                                "\"creditHours\":\"3\",\"department\":\"CSE\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubject_missingStatus_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CS101\",\"name\":\"Data Structures\"," +
                                "\"creditHours\":\"3\",\"department\":\"CSE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSubjectById_returns404() throws Exception {
        when(subjectService.getSubjectById(99L))
                .thenThrow(new AuthException("Subject not found with ID: 99", 404));

        mockMvc.perform(get("/api/admin/subjects/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSubject_duplicateCode_returns409() throws Exception {
        when(subjectService.saveSubject(any(SubjectDTO.class)))
                .thenThrow(new AuthException("Subject code already exists: CS101", 409));

        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSubject_referenced_returns409() throws Exception {
        doThrow(new AuthException("Cannot delete subject. Section(s) reference it: A", 409))
                .when(subjectService).deleteSubject(1L);

        mockMvc.perform(delete("/api/admin/subjects/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void createSubject_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createSubject_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }
}
