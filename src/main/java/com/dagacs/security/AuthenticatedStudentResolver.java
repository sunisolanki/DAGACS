package com.dagacs.security;

import com.dagacs.entity.Student;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated {@link Student} from the JWT security context.
 * <p>
 * The authenticated principal is a {@link String} (the user's email). A
 * {@link Student} is located by matching that email against {@code students.email}
 * (mirroring how {@link AuthenticatedTeacherResolver} resolves teachers against
 * {@code teachers.email}). Student identity always comes from the security context —
 * never from a client-supplied field.
 * </p>
 */
@Component
public class AuthenticatedStudentResolver {

    private final StudentRepository studentRepository;

    public AuthenticatedStudentResolver(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.getPrincipal() instanceof String principal) {
            email = principal;
        }
        if (email == null || email.isBlank()) {
            throw new AuthException("Unable to resolve the authenticated student", 401);
        }
        return studentRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(
                        "No student account is linked to the authenticated user", 401));
    }
}
