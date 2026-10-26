package com.careersphere.service;

import com.careersphere.dto.AuthDtos.*;
import com.careersphere.entity.RecruiterProfile;
import com.careersphere.entity.Role;
import com.careersphere.entity.StudentProfile;
import com.careersphere.entity.User;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.RecruiterProfileRepository;
import com.careersphere.repository.StudentProfileRepository;
import com.careersphere.repository.UserRepository;
import com.careersphere.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final RecruiterProfileRepository recruiterProfiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, StudentProfileRepository studentProfiles,
                       RecruiterProfileRepository recruiterProfiles, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.recruiterProfiles = recruiterProfiles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.forbidden("Admin accounts cannot be created through registration.");
        }
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }

        User user = users.save(new User(req.fullName().trim(), email,
                passwordEncoder.encode(req.password()), req.role()));

        // Every user gets an empty profile right away, so later saves are always UPDATEs.
        if (user.getRole() == Role.STUDENT) studentProfiles.save(new StudentProfile(user));
        if (user.getRole() == Role.RECRUITER) recruiterProfiles.save(new RecruiterProfile(user));

        return new AuthResponse(jwtService.generateToken(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return new AuthResponse(jwtService.generateToken(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId).map(UserDto::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found."));
    }
}
