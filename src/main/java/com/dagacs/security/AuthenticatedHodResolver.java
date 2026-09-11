package com.dagacs.security;

import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthErrorCode;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.TeacherRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated, properly-configured HOD from the JWT security
 * context (M6.1).
 * <p>
 * Identity always comes from the security context (the JWT subject is the user's
 * email). A {@link Teacher} is located by matching that email, mirroring
 * {@link AuthenticatedTeacherResolver}. A user is treated as a resolvable HOD
 * only when the linked teacher exists, is marked HOD, and belongs to a
 * department. No client-supplied identifier (teacherId, departmentId,
 * studentId, sectionId) is ever accepted.
 * </p>
 * <p>
 * Per the project's established identity-resolution error model (see
 * {@code AuthenticatedStudentResolver} and {@code AuthenticatedTeacherResolver}),
 * every resolution failure returns 401 with a distinct message. 403 is produced
 * exclusively by the role matcher / {@code @PreAuthorize} before this resolver
 * runs.
 * </p>
 */
@Component
public class AuthenticatedHodResolver {

    private final TeacherRepository teacherRepository;

    public AuthenticatedHodResolver(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    public Teacher resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.getPrincipal() instanceof String principal) {
            email = principal;
        }
        if (email == null || email.isBlank()) {
            throw new AuthException("Unable to resolve the authenticated HOD", 401,
                    AuthErrorCode.UNABLE_TO_RESOLVE_IDENTITY);
        }

        Teacher teacher = teacherRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(
                        "No teacher account is linked to the authenticated HOD user", 401,
                        AuthErrorCode.HOD_PROFILE_NOT_LINKED));

        if (!"ACTIVE".equals(teacher.getStatus())) {
            throw new AuthException("The teacher account is inactive", 401,
                    AuthErrorCode.HOD_PROFILE_INACTIVE);
        }

        if (teacher.getIsHod() == null || !teacher.getIsHod()) {
            throw new AuthException("The authenticated teacher is not configured as an HOD", 401,
                    AuthErrorCode.HOD_NOT_DESIGNATED);
        }

        if (teacher.getDepartment() == null) {
            throw new AuthException("The authenticated HOD has no department configured", 401,
                    AuthErrorCode.HOD_NO_DEPARTMENT);
        }

        return teacher;
    }
}