package com.dagacs.controller;

import com.dagacs.dto.DepartmentDTO;
import com.dagacs.service.DepartmentService;
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
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DepartmentService departmentService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDepartment_valid_returns201() throws Exception {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Computer Science");
        dto.setCode("CS");
        when(departmentService.saveDepartment(any(DepartmentDTO.class))).thenReturn(dto);

        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Computer Science\",\"code\":\"CS\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllDepartments_returns200() throws Exception {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Computer Science");
        dto.setCode("CS");
        when(departmentService.getAllDepartments()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithAnonymousUser
    void createDepartment_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Computer Science\",\"code\":\"CS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createDepartment_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Computer Science\",\"code\":\"CS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDepartmentById_returns200() throws Exception {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Computer Science");
        dto.setCode("CS");
        when(departmentService.getDepartmentById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Computer Science"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDepartment_returns200() throws Exception {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Computer Science");
        dto.setCode("CS");
        when(departmentService.updateDepartment(eq(1L), any(DepartmentDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/departments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Computer Science\",\"code\":\"CS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDepartment_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/departments/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDepartment_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"CS\"}"))
                .andExpect(status().isBadRequest());
    }
}