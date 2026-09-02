package com.dagacs.config;

import com.dagacs.entity.Role;
import com.dagacs.entity.User;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedData(RoleRepository roleRepository,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.seed.admin-password:Admin@123}") String adminPassword,
                               @Value("${app.seed.hod-password:Hod@123}") String hodPassword,
                               @Value("${app.seed.teacher-password:Teacher@123}") String teacherPassword,
                               @Value("${app.seed.student-password:Student@123}") String studentPassword) {
        return args -> {
            seedRoles(roleRepository);
            seedUsers(userRepository, roleRepository, passwordEncoder,
                    adminPassword, hodPassword, teacherPassword, studentPassword);
        };
    }

    private void seedRoles(RoleRepository roleRepository) {
        if (roleRepository.count() == 0) {
            LocalDateTime now = LocalDateTime.now();
            roleRepository.saveAll(Map.of(
                    "ADMIN", 4,
                    "HOD", 3,
                    "TEACHER", 2,
                    "STUDENT", 1
            ).entrySet().stream().map(entry -> Role.builder()
                    .name(entry.getKey())
                    .permissionLevel(entry.getValue().toString())
                    .description(entry.getKey() + " role")
                    .createdAt(now)
                    .build()).toList());
            log.info("Seeded 4 roles: ADMIN, HOD, TEACHER, STUDENT");
        }
    }

    private void seedUsers(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           String adminPassword,
                           String hodPassword,
                           String teacherPassword,
                           String studentPassword) {
        Map<String, String[]> users = new LinkedHashMap<>();
        users.put("admin@dagacs.local", new String[]{"Admin User", "ADMIN", adminPassword});
        users.put("hod@dagacs.local", new String[]{"HOD User", "HOD", hodPassword});
        users.put("teacher@dagacs.local", new String[]{"Teacher User", "TEACHER", teacherPassword});
        users.put("student@dagacs.local", new String[]{"Student User", "STUDENT", studentPassword});

        for (Map.Entry<String, String[]> entry : users.entrySet()) {
            String email = entry.getKey();
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            String[] value = entry.getValue();
            Role role = roleRepository.findByName(value[1])
                    .orElseThrow(() -> new IllegalStateException("Role not seeded: " + value[1]));
            LocalDateTime now = LocalDateTime.now();
            userRepository.save(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(value[2]))
                    .fullName(value[0])
                    .phone("")
                    .status("ACTIVE")
                    .role(role)
                    .avatarUrl("")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            log.info("Seeded development user: {}", email);
        }
    }
}
