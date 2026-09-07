package com.dagacs.controller;

import com.dagacs.dto.AttendancePercentageDTO;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.service.AttendanceCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttendanceCalculationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttendanceCalculationService calculationService;

    private AttendancePercentageDTO percentageDTO(long present, long total, Double percentage) {
        return AttendancePercentageDTO.builder()
                .presentCount(present)
                .totalRecordedCount(total)
                .percentage(percentage)
                .build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void subjectCalculation_returns200() throws Exception {
        when(calculationService.getSubjectCalculation(5L, null, null))
                .thenReturn(percentageDTO(7, 10, 70.0));

        mockMvc.perform(get("/api/student/attendance/calculation/subject/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(7))
                .andExpect(jsonPath("$.totalRecordedCount").value(10))
                .andExpect(jsonPath("$.percentage").value(70.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void overallCalculation_returns200() throws Exception {
        when(calculationService.getOverallCalculation(null, null))
                .thenReturn(percentageDTO(11, 20, 55.0));

        mockMvc.perform(get("/api/student/attendance/calculation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(11))
                .andExpect(jsonPath("$.totalRecordedCount").value(20))
                .andExpect(jsonPath("$.percentage").value(55.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void subjectCalculation_zeroRecords_returnsNullPercentage() throws Exception {
        when(calculationService.getSubjectCalculation(5L, null, null))
                .thenReturn(percentageDTO(0, 0, null));

        mockMvc.perform(get("/api/student/attendance/calculation/subject/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(0))
                .andExpect(jsonPath("$.totalRecordedCount").value(0));
    }

    @Test
    @WithAnonymousUser
    void subjectCalculation_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation/subject/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void subjectCalculation_teacherDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation/subject/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subjectCalculation_adminDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation/subject/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOD")
    void subjectCalculation_hodDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation/subject/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void overallCalculation_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void overallCalculation_teacherDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overallCalculation_adminDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOD")
    void overallCalculation_hodDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void overallCalculation_withDateRange_returns200() throws Exception {
        when(calculationService.getOverallCalculation(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(percentageDTO(8, 10, 80.0));

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(8))
                .andExpect(jsonPath("$.totalRecordedCount").value(10))
                .andExpect(jsonPath("$.percentage").value(80.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void overallCalculation_withStartDateOnly_returns200() throws Exception {
        when(calculationService.getOverallCalculation(LocalDate.of(2026, 1, 1), null))
                .thenReturn(percentageDTO(5, 8, 62.5));

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(5))
                .andExpect(jsonPath("$.totalRecordedCount").value(8))
                .andExpect(jsonPath("$.percentage").value(62.5));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void overallCalculation_withEndDateOnly_returns200() throws Exception {
        when(calculationService.getOverallCalculation(null, LocalDate.of(2026, 1, 31)))
                .thenReturn(percentageDTO(3, 4, 75.0));

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(3))
                .andExpect(jsonPath("$.totalRecordedCount").value(4))
                .andExpect(jsonPath("$.percentage").value(75.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void subjectCalculation_withDateRange_returns200() throws Exception {
        when(calculationService.getSubjectCalculation(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(percentageDTO(7, 10, 70.0));

        mockMvc.perform(get("/api/student/attendance/calculation/subject/5")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(7))
                .andExpect(jsonPath("$.totalRecordedCount").value(10))
                .andExpect(jsonPath("$.percentage").value(70.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void overallCalculation_invertedDateRange_returns400() throws Exception {
        when(calculationService.getOverallCalculation(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .thenThrow(new InvalidDateRangeException("startDate must not be after endDate"));

        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void overallCalculation_malformedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation")
                        .param("startDate", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void subjectCalculation_malformedEndDate_returns400() throws Exception {
        mockMvc.perform(get("/api/student/attendance/calculation/subject/5")
                        .param("endDate", "31-01-2026"))
                .andExpect(status().isBadRequest());
    }
}
