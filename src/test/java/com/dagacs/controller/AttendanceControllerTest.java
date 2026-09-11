package com.dagacs.controller;

import com.dagacs.dto.*;
import com.dagacs.exception.AuthException;
import com.dagacs.service.AttendanceService;
import com.dagacs.service.AttendanceSessionService;
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
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttendanceSessionService sessionService;

    @MockBean
    private AttendanceService attendanceService;

    private AttendanceSessionDTO sessionDto() {
        return AttendanceSessionDTO.builder()
                .id(1L).subjectId(1L).subjectName("DBMS").sectionId(1L).sectionName("CSE-A")
                .teacherId(1L).lecturePeriod("1st").date("2026-09-04").status("SCHEDULED").build();
    }

    private AttendanceRecordDTO recordDto() {
        return AttendanceRecordDTO.builder()
                .id(1L).studentId(1L).subjectId(1L).sectionId(1L)
                .status("PRESENT").lecturePeriod("1st").date("2026-09-04").isPresent(true)
                .sessionId(1L).markedById(1L).markedByName("T").build();
    }

    private String createSessionJson() {
        return "{\"subjectId\":1,\"sectionId\":1,\"lecturePeriod\":\"1st\",\"date\":\"2026-09-04\"}";
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createSession_returns201() throws Exception {
        when(sessionService.createSession(any(AttendanceSessionCreateRequestDTO.class))).thenReturn(sessionDto());

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createSession_blankDate_returns400() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":1,\"sectionId\":1,\"lecturePeriod\":\"1st\",\"date\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createSession_duplicate_returns409() throws Exception {
        when(sessionService.createSession(any(AttendanceSessionCreateRequestDTO.class)))
                .thenThrow(new AuthException("An attendance session already exists for this subject, section, date, and period", 409));

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createSession_notAssigned_returns403() throws Exception {
        when(sessionService.createSession(any(AttendanceSessionCreateRequestDTO.class)))
                .thenThrow(new AuthException("Teacher is not assigned to this subject and section", 403));

        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void listSessions_returns200() throws Exception {
        when(sessionService.listTeacherSessions()).thenReturn(List.of(sessionDto()));

        mockMvc.perform(get("/api/teacher/attendance/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void getStudentsForSession_returns200() throws Exception {
        when(sessionService.getStudentsForSession(1L))
                .thenReturn(List.of(StudentDTO.builder().id(1L).rollNumber("R1").name("S1").build()));

        mockMvc.perform(get("/api/teacher/attendance/sessions/1/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void markAttendance_returns201() throws Exception {
        when(attendanceService.markAttendance(any(AttendanceMarkRequestDTO.class)))
                .thenReturn(List.of(recordDto()));

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"items\":[{\"studentId\":1,\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void markAttendance_duplicate_returns409() throws Exception {
        when(attendanceService.markAttendance(any(AttendanceMarkRequestDTO.class)))
                .thenThrow(new AuthException("Attendance already recorded for student 1 in this session", 409));

        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"items\":[{\"studentId\":1,\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void updateAttendance_returns200() throws Exception {
        when(attendanceService.updateAttendance(eq(1L), any(AttendanceUpdateRequestDTO.class)))
                .thenReturn(recordDto());

        mockMvc.perform(put("/api/teacher/attendance/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PRESENT\",\"reason\":\"doc\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void updateAttendance_unchanged_returns400() throws Exception {
        when(attendanceService.updateAttendance(eq(1L), any(AttendanceUpdateRequestDTO.class)))
                .thenThrow(new AuthException("New status is the same as the current status", 400));

        mockMvc.perform(put("/api/teacher/attendance/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PRESENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void getAttendanceBySession_returns200() throws Exception {
        when(attendanceService.getAttendanceBySession(1L)).thenReturn(List.of(recordDto()));

        mockMvc.perform(get("/api/teacher/attendance/sessions/1/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @WithAnonymousUser
    void createSession_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void markAttendance_studentDenied_returns403() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"items\":[{\"studentId\":1,\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOD")
    void createSession_hodDenied_returns403() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSession_forgedToken_returns401() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZWFjaGVyQGRhZ2Fjcy5sb2NhbCIsInJvbGVzIjoiVEVBQ0hFUiJ9.forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAttendance_forgedToken_returns401() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/mark")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZWFjaGVyQGRhZ2Fjcy5sb2NhbCIsInJvbGVzIjoiVEVBQ0hFUiJ9.forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":1,\"items\":[{\"studentId\":1,\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSession_adminDenied_returns403() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSessionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createSession_invalidDate_returns400() throws Exception {
        mockMvc.perform(post("/api/teacher/attendance/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":1,\"sectionId\":1,\"lecturePeriod\":\"1st\",\"date\":\"2026-13-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void updateSession_invalidDate_returns400() throws Exception {
        mockMvc.perform(put("/api/teacher/attendance/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturePeriod\":\"1st\",\"date\":\"2026-13-01\",\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isBadRequest());
    }
}
