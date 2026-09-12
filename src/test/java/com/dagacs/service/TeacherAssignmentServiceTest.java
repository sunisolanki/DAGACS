package com.dagacs.service;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.dto.TeacherAssignmentRequestDTO;
import com.dagacs.dto.TeacherDTO;
import com.dagacs.entity.*;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.AttendanceRecordRepository;
import com.dagacs.repository.AttendanceSessionRepository;
import com.dagacs.repository.SectionRepository;
import com.dagacs.repository.SubjectOfferingRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.TeacherSubjectSectionAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherAssignmentServiceTest {

    @Mock
    private TeacherSubjectSectionAssignmentRepository assignmentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private SubjectOfferingRepository subjectOfferingRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks
    private TeacherAssignmentService teacherAssignmentService;

    private Teacher teacher1;
    private Teacher teacher2;
    private Subject subject;
    private AcademicSession sessionA;
    private AcademicSession sessionB;
    private Semester semester;
    private SubjectOffering offering;
    private Batch batchA;
    private Batch batchB;
    private Section sectionA;
    private TeacherSubjectSectionAssignment assignment;

    @BeforeEach
    void setUp() {
        teacher1 = Teacher.builder().id(1L).email("teacher1@dagacs.local").fullName("Teacher One")
                .designation("Professor").status("ACTIVE").build();
        teacher2 = Teacher.builder().id(2L).email("teacher2@dagacs.local").fullName("Teacher Two")
                .designation("Associate Professor").status("ACTIVE").build();

        Department dept = Department.builder().id(10L).name("CSE").code("CSE").description("D").build();
        Program program = Program.builder().id(20L).name("B.Tech CSE").code("BT").department(dept).build();
        subject = Subject.builder().id(30L).code("CS302").name("DBMS").build();
        sessionA = AcademicSession.builder().id(40L).name("2026-27").code("S1").program(program).build();
        sessionB = AcademicSession.builder().id(41L).name("2025-26").code("S0").program(program).build();
        semester = Semester.builder().id(50L).name("Semester 5").code("SEM5")
                .academicSession(sessionA).build();
        offering = SubjectOffering.builder().id(60L).subject(subject).semester(semester).build();
        batchA = Batch.builder().id(70L).batchCode("B26").academicSession(sessionA).build();
        batchB = Batch.builder().id(71L).batchCode("B25").academicSession(sessionB).build();
        sectionA = Section.builder().id(80L).sectionCode("CSE-A").name("A").batch(batchA).build();
        Section sectionB = Section.builder().id(81L).sectionCode("CSE-B").name("B").batch(batchA).build();
        assignment = TeacherSubjectSectionAssignment.builder().id(90L)
                .teacher(teacher1).subjectOffering(offering).section(sectionA)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private TeacherAssignmentRequestDTO request(Long teacherId, Long offeringId, Long sectionId) {
        return TeacherAssignmentRequestDTO.builder()
                .teacherId(teacherId).subjectOfferingId(offeringId).sectionId(sectionId).build();
    }

    @Test
    void createAssignment_valid_persistsAndDerivesContext() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndSectionId(1L, 60L, 80L)).thenReturn(false);
        when(assignmentRepository.save(any(TeacherSubjectSectionAssignment.class))).thenAnswer(inv -> {
            TeacherSubjectSectionAssignment saved = inv.getArgument(0);
            saved.setId(90L);
            return saved;
        });

        TeacherAssignmentDTO result = teacherAssignmentService.createAssignment(request(1L, 60L, 80L));

        assertEquals(90L, result.getId());
        assertEquals(1L, result.getTeacherId());
        assertEquals("Teacher One", result.getTeacherName());
        assertEquals(60L, result.getSubjectOfferingId());
        assertEquals(30L, result.getSubjectId());
        assertEquals("CS302", result.getSubjectCode());
        assertEquals(80L, result.getSectionId());
        assertEquals("CSE-A", result.getSectionCode());
        assertEquals("2026-27", result.getSessionName());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void createAssignment_missingTeacher_returns404() {
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(99L, 60L, 80L)));
        assertEquals(404, ex.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void createAssignment_missingOffering_returns404() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(1L, 99L, 80L)));
        assertEquals(404, ex.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void createAssignment_missingSection_returns404() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(1L, 60L, 99L)));
        assertEquals(404, ex.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void createAssignment_nullIds_returns400() {
        AuthException missingTeacher = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(null, 60L, 80L)));
        assertEquals(400, missingTeacher.getStatus());

        AuthException missingOffering = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(1L, null, 80L)));
        assertEquals(400, missingOffering.getStatus());

        AuthException missingSection = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(1L, 60L, null)));
        assertEquals(400, missingSection.getStatus());
    }

    @Test
    void createAssignment_duplicate_returns409() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.existsByTeacherIdAndSubjectOfferingIdAndSectionId(1L, 60L, 80L)).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(1L, 60L, 80L)));
        assertEquals(409, ex.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void createAssignment_incompatibleSessions_returns400() {
        Section foreignSection = Section.builder().id(81L).sectionCode("CSE-O").name("O").batch(batchB).build();
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(81L)).thenReturn(Optional.of(foreignSection));

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(1L, 60L, 81L)));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Academic session mismatch"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void updateAssignment_valid_movesAssignment() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(2L, 60L, 80L))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(TeacherSubjectSectionAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherAssignmentDTO result = teacherAssignmentService.updateAssignment(90L, request(2L, 60L, 80L));
        assertEquals("Teacher Two", result.getTeacherName());
        assertEquals(teacher2, assignment.getTeacher());
    }

    @Test
    void updateAssignment_missing_returns404() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.updateAssignment(99L, request(1L, 60L, 80L)));
        assertEquals(404, ex.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void updateAssignment_duplicateElsewhere_returns409() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        TeacherSubjectSectionAssignment other = TeacherSubjectSectionAssignment.builder().id(91L)
                .teacher(teacher2).subjectOffering(offering).section(sectionA).build();
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(2L, 60L, 80L))
                .thenReturn(Optional.of(other));

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.updateAssignment(90L, request(2L, 60L, 80L)));
        assertEquals(409, ex.getStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void updateAssignment_keepingSelf_notDuplicate() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(1L, 60L, 80L))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(TeacherSubjectSectionAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherAssignmentDTO result = teacherAssignmentService.updateAssignment(90L, request(1L, 60L, 80L));
        assertEquals(1L, result.getTeacherId());
        verify(assignmentRepository).save(any(TeacherSubjectSectionAssignment.class));
    }

    @Test
    void getAllAssignments_returnsDerivedList() {
        when(assignmentRepository.findAllByOrderByIdAsc()).thenReturn(List.of(assignment));

        List<TeacherAssignmentDTO> result = teacherAssignmentService.getAllAssignments();
        assertEquals(1, result.size());
        assertEquals("DBMS", result.get(0).getSubjectName());
        assertEquals("CSE", result.get(0).getDepartmentName());
        assertEquals("B.Tech CSE", result.get(0).getProgramName());
        assertEquals("B26", result.get(0).getBatchCode());
    }

    @Test
    void getAssignmentsForTeacher_returnsOwnedOnly() {
        when(assignmentRepository.findByTeacherIdOrderByIdAsc(1L)).thenReturn(List.of(assignment));

        List<TeacherAssignmentDTO> result = teacherAssignmentService.getAssignmentsForTeacher(1L);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getTeacherId());
    }

    @Test
    void deleteAssignment_missing_returns404() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.deleteAssignment(99L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void deleteAssignment_valid_deletesHard() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));

        teacherAssignmentService.deleteAssignment(90L);
        verify(assignmentRepository).delete(assignment);
    }

    @Test
    void listTeachers_returnsAllSortedByFullName() {
        when(teacherRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(teacher2, teacher1));

        List<TeacherDTO> result = teacherAssignmentService.listTeachers();
        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals("Teacher Two", result.get(0).getFullName());
        assertNull(result.get(0).getDepartmentName());
    }

    // ---------- M9.15 Assignment Lifecycle Integrity Hardening ----------

    @Test
    void createAssignment_inactiveTeacher_returns409() {
        Teacher inactive = Teacher.builder().id(3L).email("inactive@dagacs.local")
                .fullName("Inactive Teacher").status("INACTIVE").build();
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(inactive));

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.createAssignment(request(3L, 60L, 80L)));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot assign an inactive teacher.", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void updateAssignment_keepSameInactiveTeacher_noInactiveGuard() {
        Teacher inactive = Teacher.builder().id(3L).email("inactive@dagacs.local")
                .fullName("Inactive Teacher").status("INACTIVE").build();
        assignment.setTeacher(inactive);
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(inactive));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(3L, 60L, 80L))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(TeacherSubjectSectionAssignment.class))).thenAnswer(inv -> {
            TeacherSubjectSectionAssignment saved = inv.getArgument(0);
            saved.setId(90L);
            return saved;
        });

        TeacherAssignmentDTO result = teacherAssignmentService.updateAssignment(90L, request(3L, 60L, 80L));

        assertEquals(90L, result.getId());
        assertEquals(3L, result.getTeacherId());
        verify(assignmentRepository).save(any(TeacherSubjectSectionAssignment.class));
    }

    @Test
    void updateAssignment_changeToInactiveTeacher_returns409() {
        Teacher inactive = Teacher.builder().id(3L).email("inactive@dagacs.local")
                .fullName("Inactive Teacher").status("INACTIVE").build();
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(inactive));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.updateAssignment(90L, request(3L, 60L, 80L)));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot assign an inactive teacher.", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
        assertEquals(1L, assignment.getTeacher().getId(), "Assignment must stay untouched after rejection");
    }

    @Test
    void updateAssignment_teacherChangeWithSessions_returns409() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(subjectOfferingRepository.findById(60L)).thenReturn(Optional.of(offering));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(2L, 60L, 80L))
                .thenReturn(Optional.empty());
        when(attendanceSessionRepository.existsByTeacherEntityIdAndSubjectEntityIdAndSectionEntityId(1L, 30L, 80L))
                .thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.updateAssignment(90L, request(2L, 60L, 80L)));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot change assignment teacher while attendance history exists.", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
        assertEquals(1L, assignment.getTeacher().getId(), "Assignment teacher must stay untouched");
    }

    @Test
    void updateAssignment_contextChangeWithRecords_returns409() {
        SubjectOffering offering2 = SubjectOffering.builder().id(61L).subject(subject).semester(semester).build();
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(61L)).thenReturn(Optional.of(offering2));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(1L, 61L, 80L))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.existsByMarkedByIdAndSubjectIdAndSectionId(1L, 30L, 80L))
                .thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.updateAssignment(90L, request(1L, 61L, 80L)));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot change assignment context while attendance history exists.", ex.getMessage());
        verify(assignmentRepository, never()).save(any());
        assertEquals(60L, assignment.getSubjectOffering().getId(), "Assignment context must stay untouched");
    }

    @Test
    void updateAssignment_contextChange_noHistory_allowed() {
        SubjectOffering offering2 = SubjectOffering.builder().id(61L).subject(subject).semester(semester).build();
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(subjectOfferingRepository.findById(61L)).thenReturn(Optional.of(offering2));
        when(sectionRepository.findById(80L)).thenReturn(Optional.of(sectionA));
        when(assignmentRepository.findByTeacherIdAndSubjectOfferingIdAndSectionId(1L, 61L, 80L))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(TeacherSubjectSectionAssignment.class))).thenAnswer(inv -> {
            TeacherSubjectSectionAssignment saved = inv.getArgument(0);
            saved.setId(90L);
            return saved;
        });

        TeacherAssignmentDTO result = teacherAssignmentService.updateAssignment(90L, request(1L, 61L, 80L));

        assertEquals(90L, result.getId());
        assertEquals(61L, result.getSubjectOfferingId());
        verify(assignmentRepository).save(any(TeacherSubjectSectionAssignment.class));
    }

    @Test
    void deleteAssignment_withSessions_returns409() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(attendanceSessionRepository.existsByTeacherEntityIdAndSubjectEntityIdAndSectionEntityId(1L, 30L, 80L))
                .thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.deleteAssignment(90L));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot delete assignment while attendance history exists.", ex.getMessage());
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    void deleteAssignment_withRecords_returns409() {
        when(assignmentRepository.findById(90L)).thenReturn(Optional.of(assignment));
        when(attendanceRecordRepository.existsByMarkedByIdAndSubjectIdAndSectionId(1L, 30L, 80L))
                .thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> teacherAssignmentService.deleteAssignment(90L));
        assertEquals(409, ex.getStatus());
        assertEquals("Cannot delete assignment while attendance history exists.", ex.getMessage());
        verify(assignmentRepository, never()).delete(any());
    }
}