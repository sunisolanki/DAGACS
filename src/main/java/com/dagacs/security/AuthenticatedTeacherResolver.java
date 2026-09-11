package com.dagacs.security;

import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthErrorCode;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.TeacherRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated {@link Teacher} from the JWT security context.
 * <p>
 * The authenticated principal is a {@link String} (the user's email). A {@link Teacher}
 * is located by matching that email against {@code teachers.email}. Teacher identity
 * always comes from the security context — never from a client-supplied field.
 * </p>
 */
@Component
public class AuthenticatedTeacherResolver {

    private final TeacherRepository teacherRepository;

    public AuthenticatedTeacherResolver(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    public Teacher resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.getPrincipal() instanceof String principal) {
            email = principal;
        }
        if (email == null || email.isBlank()) {
            throw new AuthException("Unable to resolve the authenticated teacher", 401,
                    AuthErrorCode.UNABLE_TO_RESOLVE_IDENTITY);
        }
        Teacher teacher = teacherRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(
                        "No teacher account is linked to the authenticated user", 401,
                        AuthErrorCode.TEACHER_PROFILE_NOT_LINKED));
        if (!"ACTIVE".equals(teacher.getStatus())) {
            throw new AuthException("The teacher account is inactive", 401,
                    AuthErrorCode.TEACHER_PROFILE_INACTIVE);
        }
        return teacher;
    }
}
