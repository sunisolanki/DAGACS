package com.dagacs.service;

import com.dagacs.dto.DepartmentDTO;
import com.dagacs.entity.Department;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private DepartmentDTO validDepartmentDTO() {
        return DepartmentDTO.builder()
                .name("Computer Science")
                .code("CS")
                .build();
    }

    @Test
    void saveDepartment_blankDescription_defaultsToEmptyString() {
        when(departmentRepository.existsByNameIgnoreCase("Computer Science")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        departmentService.saveDepartment(validDepartmentDTO());

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        Department saved = captor.getValue();
        assertEquals("", saved.getDescription());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void saveDepartment_validDescription_isPreserved() {
        DepartmentDTO dto = validDepartmentDTO();
        dto.setDescription("Engineering department");
        when(departmentRepository.existsByNameIgnoreCase("Computer Science")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDTO result = departmentService.saveDepartment(dto);

        assertEquals("Engineering department", result.getDescription());
    }

    @Test
    void saveDepartment_blankName_returns400() {
        DepartmentDTO dto = validDepartmentDTO();
        dto.setName(" ");

        AuthException ex = assertThrows(AuthException.class,
                () -> departmentService.saveDepartment(dto));
        assertEquals(400, ex.getStatus());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void saveDepartment_duplicateName_returns409_notSaved() {
        when(departmentRepository.existsByNameIgnoreCase("Computer Science")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> departmentService.saveDepartment(validDepartmentDTO()));
        assertEquals(409, ex.getStatus());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartment_blankDescription_defaultsToEmptyString() {
        Department existing = Department.builder()
                .id(1L)
                .name("Computer Science")
                .code("CS")
                .description("old")
                .createdBy("SYSTEM")
                .build();
        when(departmentRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDTO dto = validDepartmentDTO();
        departmentService.updateDepartment(1L, dto);

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertEquals("", captor.getValue().getDescription());
    }

    @Test
    void updateDepartment_sameNameAndBlankDescription_noDuplicateCheckConflict() {
        Department existing = Department.builder()
                .id(1L)
                .name("Computer Science")
                .code("CS")
                .description("old")
                .createdBy("SYSTEM")
                .build();
        when(departmentRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDTO dto = validDepartmentDTO();
        dto.setName("Computer Science");

        DepartmentDTO result = departmentService.updateDepartment(1L, dto);

        assertEquals("Computer Science", result.getName());
        assertEquals("", result.getDescription());
    }

    @Test
    void updateDepartment_missing_returns404() {
        when(departmentRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> departmentService.updateDepartment(99L, validDepartmentDTO()));
        assertEquals(404, ex.getStatus());
    }
}