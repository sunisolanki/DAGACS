package com.dagacs.service;

import com.dagacs.dto.HodDesignationRequestDTO;
import com.dagacs.dto.HodIdentityDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.DepartmentRepository;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.TeacherRepository;
import com.dagacs.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HodDesignationServiceTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private HodDesignationService service;

    private Department deptX;
    private Department deptY;
    private Teacher teacherA;
    private Teacher teacherB;

    @BeforeEach
    void setUp() {
        deptX = Department.builder().id(1L).name("Computer Science").code("CS").build();
        deptY = Department.builder().id(2L).name("Electronics").code("EC").build();

        teacherA = Teacher.builder()
                .id(1L).email("hod@dagacs.local").fullName("HOD User")
                .designation("Professor").department(deptX).isHod(false)
                .phone("").status("ACTIVE").avatarUrl("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        teacherB = Teacher.builder()
                .id(2L).email("hod2@dagacs.local").fullName("HOD Two")
                .designation("Professor").department(deptY).isHod(false)
                .phone("").status("ACTIVE").avatarUrl("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private void stubDeptLock(Department dept) {
        when(entityManager.find(Department.class, dept.getId(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(dept);
    }

    @Test
    void designate_success_setsHodTrueAndDepartment() {
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));
        stubDeptLock(deptX);
        when(teacherRepository.findHodLocked(1L)).thenReturn(Optional.empty());
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        HodIdentityDTO result = service.setHod(1L, HodDesignationRequestDTO.builder()
                .designated(true).departmentId(1L).build());

        assertTrue(result.getHod());
        assertEquals("Computer Science", result.getDepartmentName());
        assertEquals("hod@dagacs.local", result.getEmail());
        assertTrue(teacherA.getIsHod());
        assertEquals(deptX, teacherA.getDepartment());
        verify(teacherRepository).save(teacherA);
    }

    @Test
    void designate_withoutDepartment_returns400() {
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));

        AuthException ex = assertThrows(AuthException.class, () -> service.setHod(1L,
                HodDesignationRequestDTO.builder().designated(true).build()));

        assertEquals(400, ex.getStatus());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void designate_duplicateHodSameDepartment_returns409() {
        // teacherA is already the HOD of dept X; teacherB tries to take over.
        teacherA.setIsHod(true);
        when(teacherRepository.findByIdLocked(2L)).thenReturn(Optional.of(teacherB));
        stubDeptLock(deptX);
        when(teacherRepository.findHodLocked(1L)).thenReturn(Optional.of(teacherA));

        AuthException ex = assertThrows(AuthException.class, () -> service.setHod(2L,
                HodDesignationRequestDTO.builder().designated(true).departmentId(1L).build()));

        assertEquals(409, ex.getStatus());
        verify(teacherRepository, never()).save(any());
        // Original HOD remains intact.
        assertTrue(teacherA.getIsHod());
        assertEquals(deptX, teacherA.getDepartment());
    }

    @Test
    void designate_sameTeacherReDesignate_isIdempotent() {
        teacherA.setIsHod(true);
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));
        stubDeptLock(deptX);
        when(teacherRepository.findHodLocked(1L)).thenReturn(Optional.of(teacherA));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        HodIdentityDTO result = service.setHod(1L, HodDesignationRequestDTO.builder()
                .designated(true).departmentId(1L).build());

        assertTrue(result.getHod());
        assertEquals(1L, teacherA.getDepartment().getId());
    }

    @Test
    void designate_missingDepartment_returns404() {
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));
        when(entityManager.find(Department.class, 99L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(null);

        AuthException ex = assertThrows(AuthException.class, () -> service.setHod(1L,
                HodDesignationRequestDTO.builder().designated(true).departmentId(99L).build()));

        assertEquals(404, ex.getStatus());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void designate_missingTeacher_returns404() {
        when(teacherRepository.findByIdLocked(99L)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.setHod(99L,
                HodDesignationRequestDTO.builder().designated(true).departmentId(1L).build()));

        assertEquals(404, ex.getStatus());
    }

    @Test
    void designate_nullDesignated_returns400() {
        AuthException ex = assertThrows(AuthException.class, () -> service.setHod(1L,
                HodDesignationRequestDTO.builder().departmentId(1L).build()));

        assertEquals(400, ex.getStatus());
    }

    @Test
    void clearHod_success_clearsDesignationKeepsDepartment() {
        teacherA.setIsHod(true);
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        HodIdentityDTO result = service.setHod(1L, HodDesignationRequestDTO.builder()
                .designated(false).build());

        assertFalse(result.getHod());
        assertFalse(teacherA.getIsHod());
        // Teacher keeps its department association (no invented dept-clearing).
        assertEquals(deptX, teacherA.getDepartment());
        assertEquals("Computer Science", result.getDepartmentName());
    }

    @Test
    void clearHod_alreadyNotHod_isIdempotentNoSave() {
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));

        HodIdentityDTO result = service.setHod(1L, HodDesignationRequestDTO.builder()
                .designated(false).build());

        assertFalse(result.getHod());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void moveHod_targetFree_succeeds_singleDepartment() {
        // teacherA is HOD of dept X, moving to free dept Y.
        teacherA.setIsHod(true);
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));
        stubDeptLock(deptY);
        when(teacherRepository.findHodLocked(2L)).thenReturn(Optional.empty());
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        HodIdentityDTO result = service.setHod(1L, HodDesignationRequestDTO.builder()
                .designated(true).departmentId(2L).build());

        assertEquals("Electronics", result.getDepartmentName());
        assertEquals(deptY, teacherA.getDepartment());
        assertTrue(teacherA.getIsHod());
    }

    @Test
    void moveHod_targetOccupied_returns409_noChange() {
        // teacherA is HOD of dept X, but dept Y already has teacherB as HOD.
        teacherA.setIsHod(true);
        teacherB.setIsHod(true);
        when(teacherRepository.findByIdLocked(1L)).thenReturn(Optional.of(teacherA));
        stubDeptLock(deptY);
        when(teacherRepository.findHodLocked(2L)).thenReturn(Optional.of(teacherB));

        AuthException ex = assertThrows(AuthException.class, () -> service.setHod(1L,
                HodDesignationRequestDTO.builder().designated(true).departmentId(2L).build()));

        assertEquals(409, ex.getStatus());
        verify(teacherRepository, never()).save(any());
        // Original dept X HOD state remains unchanged.
        assertTrue(teacherA.getIsHod());
        assertEquals(deptX, teacherA.getDepartment());
    }

    @Test
    void existingNonHodTeacher_remainsValid() {
        when(teacherRepository.findByIdLocked(2L)).thenReturn(Optional.of(teacherB));

        HodIdentityDTO result = service.setHod(2L, HodDesignationRequestDTO.builder()
                .designated(false).build());

        assertFalse(result.getHod());
        assertFalse(teacherB.getIsHod());
        assertEquals(deptY, teacherB.getDepartment());
    }
}