package com.dagacs.exception;

import com.dagacs.dto.DepartmentDTO;
import com.dagacs.service.DepartmentService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DepartmentService departmentService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedJsonBody_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pathTypeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/departments/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dataIntegrityViolation_returns409() throws Exception {
        when(departmentService.saveDepartment(any(DepartmentDTO.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicate\",\"code\":\"DUP\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void constraintViolation_returns400() throws Exception {
        when(departmentService.saveDepartment(any(DepartmentDTO.class)))
                .thenThrow(new ConstraintViolationException("invalid", null));

        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"code\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }
}
