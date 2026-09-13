package com.dagacs.security;

import com.dagacs.entity.User;
import com.dagacs.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    private static final String CHANGE_PASSWORD_PATH = "/api/student/change-password";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String requestUri = request.getRequestURI();

        if (jwtTokenProvider.validateToken(token)) {
            String email = jwtTokenProvider.getSubject(token);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null && !"ACTIVE".equals(user.getStatus())) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is disabled");
                return;
            }
            if (user != null && user.isMustChangePassword()
                    && "STUDENT".equals(user.getRole().getName())
                    && !CHANGE_PASSWORD_PATH.equals(requestUri)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "STUDENT_PASSWORD_CHANGE_REQUIRED");
                return;
            }
            if (user != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user.getEmail(),
                                null,
                                authoritiesOf(user));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> authoritiesOf(User user) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
    }
}
