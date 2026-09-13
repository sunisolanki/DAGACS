package com.dagacs.security;

import com.dagacs.entity.Student;
import com.dagacs.entity.Role;
import com.dagacs.entity.User;
import com.dagacs.repository.StudentManagementRepository;
import com.dagacs.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentManagementRepository studentManagementRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                    StudentManagementRepository studentManagementRepository) {
        this.userRepository = userRepository;
        this.studentManagementRepository = studentManagementRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = resolveUser(identifier);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + identifier);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new DisabledException("Account is disabled");
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(getAuthorities(user))
                .build();
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String trimmed = identifier.trim();
        Student student = studentManagementRepository.findByRollNumber(trimmed).orElse(null);
        if (student != null && student.getEmail() != null && !student.getEmail().isBlank()) {
            return userRepository.findByEmail(student.getEmail().toLowerCase()).orElse(null);
        }
        return userRepository.findByEmail(trimmed.toLowerCase()).orElse(null);
    }

    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
    }
}
