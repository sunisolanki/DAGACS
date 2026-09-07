package com.dagacs.service;

import com.dagacs.dto.HodIdentityDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedHodResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only HOD identity/scope service (M6.1). The HOD and its department are
 * resolved exclusively from the authenticated JWT identity - never from
 * client-supplied parameters.
 */
@Service
public class HodIdentityService {

    private final AuthenticatedHodResolver hodResolver;

    public HodIdentityService(AuthenticatedHodResolver hodResolver) {
        this.hodResolver = hodResolver;
    }

    @Transactional(readOnly = true)
    public HodIdentityDTO getMyIdentity() {
        Teacher teacher = hodResolver.resolve();
        HodIdentityDTO.HodIdentityDTOBuilder builder = HodIdentityDTO.builder()
                .email(teacher.getEmail())
                .teacherName(teacher.getFullName())
                .designation(teacher.getDesignation())
                .hod(true);
        Department department = teacher.getDepartment();
        builder.departmentName(department.getName())
                .departmentCode(department.getCode());
        return builder.build();
    }
}