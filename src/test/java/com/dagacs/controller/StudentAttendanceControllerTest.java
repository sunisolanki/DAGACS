package com.dagacs.controller;

import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.service.StudentAttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentAttendanceService studentAttendanceService;

    private AttendanceRecordDTO recordDto() {
        return AttendanceRecordDTO.builder()
                .id(1L).studentId(1L).subjectId(1L).sectionId(1L)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .sessionId(1L).markedById(1L).markedByName("T").build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyAttendance_student_returns200() throws Exception {
        when(studentAttendanceService.getMyAttendance()).thenReturn(List.of(recordDto()));

        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(1));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyAttendance_noRecords_returnsEmptyList() throws Exception {
        when(studentAttendanceService.getMyAttendance()).thenReturn(List.of());

        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0));
    }

    @Test
    @WithAnonymousUser
    void getMyAttendance_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void getMyAttendance_teacherDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyAttendance_adminDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOD")
    void getMyAttendance_hodDenied_returns403() throws Exception {
        mockMvc.perform(get("/api/student/attendance/my"))
                .andExpect(status().isForbidden());
    }
}
