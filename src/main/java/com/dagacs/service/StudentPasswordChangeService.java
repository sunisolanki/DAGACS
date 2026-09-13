package com.dagacs.service;

import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPasswordChangeService {

    private final UserRepository userRepository;
    private final AccountProvisioningService accountProvisioningService;
    private final PasswordEncoder passwordEncoder;

    public StudentPasswordChangeService(UserRepository userRepository,
                                        AccountProvisioningService accountProvisioningService,
                                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountProvisioningService = accountProvisioningService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(String email, String currentPassword,
                               String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new AuthException("Current password is required", 400);
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new AuthException("New password and confirmation do not match", 400);
        }
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new AuthException("User not found", 404));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AuthException("Current password is incorrect", 400);
        }
        if (newPassword.equals(currentPassword)) {
            throw new AuthException("New password must differ from the current password", 400);
        }
        accountProvisioningService.setNewPassword(user, newPassword);
    }
}