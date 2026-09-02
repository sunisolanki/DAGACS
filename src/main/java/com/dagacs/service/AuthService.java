package com.dagacs.service;

import com.dagacs.dto.AuthRequest;
import com.dagacs.dto.AuthResponse;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.UserRepository;
import com.dagacs.security.JwtTokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Service
public class AuthService {

    private final UserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserDetailsService userDetailsService,
                       JwtTokenProvider jwtTokenProvider,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(AuthRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String rawPassword = userDetails.getPassword();

        if (!passwordEncoder.matches(request.getPassword(), rawPassword)) {
            throw new AuthException("Invalid credentials", 401);
        }

        String token = jwtTokenProvider.generateToken(
                userDetails.getUsername(),
                userDetails.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .toList());

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().getName())
                .build();
    }
}