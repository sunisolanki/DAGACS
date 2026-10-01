package com.dagacs.security;

import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthErrorCode;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.TeacherRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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

    /**
     * Request-scoped memo key for the resolved HOD.
     *
     * <p>See {@link #resolve()} for why the result is memoised.</p>
     */
    private static final String RESOLVED_CACHE_ATTRIBUTE =
            AuthenticatedHodResolver.class.getName() + ".resolved";

    private final TeacherRepository teacherRepository;

    public AuthenticatedHodResolver(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    /**
     * The authenticated HOD, resolved at most once per request.
     *
     * <p><b>Why the result is memoised.</b> A single request legitimately needs
     * the same identity more than once: the canonical report service resolves it
     * to derive the department that authorizes the data, and the Phase 4A export
     * header resolves it again to print the department name. Without a memo those
     * are two {@code findByEmail} statements - {@code findByEmail} is a query, so
     * Hibernate issues the SQL again even when the entity is already loaded in the
     * session - which made every export cost exactly one statement more than the
     * on-screen report it was generated from.</p>
     *
     * <p><b>This cannot weaken authorization.</b> The identity behind a request is
     * fixed by the JWT the filter already validated before any of this ran, so
     * there is nothing to re-read. Every one of the four checks below is still
     * applied on the <i>first</i> resolution of a request, and a memoised hit is
     * only ever served for the same principal it was resolved for. Failures are
     * deliberately never memoised, so a rejected identity is re-evaluated and
     * cannot be cached into a later, unrelated resolution.</p>
     *
     * <p>Outside a request (unit tests, scheduled work) there is no scope to memo
     * into, so the identity is resolved exactly as before.</p>
     */
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

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object memo = attributes.getAttribute(RESOLVED_CACHE_ATTRIBUTE,
                    RequestAttributes.SCOPE_REQUEST);
            if (memo instanceof ResolvedHod cached && cached.email().equals(email)) {
                return cached.hod();
            }
        }

        Teacher teacher = resolveAndValidate(email);

        if (attributes != null) {
            attributes.setAttribute(RESOLVED_CACHE_ATTRIBUTE,
                    new ResolvedHod(email, teacher),
                    RequestAttributes.SCOPE_REQUEST);
        }
        return teacher;
    }

    /** The database lookup and the four checks that make a resolvable HOD. */
    private Teacher resolveAndValidate(String email) {
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

    /** The request-scoped memo: which principal it was resolved for, and the entity. */
    private record ResolvedHod(String email, Teacher hod) {
    }
}