package com.careersphere.controller;

import com.careersphere.dto.AuthDtos.*;
import com.careersphere.entity.Skill;
import com.careersphere.security.AuthUser;
import com.careersphere.service.AuthService;
import com.careersphere.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final SkillService skillService;

    public AuthController(AuthService authService, SkillService skillService) {
        this.authService = authService;
        this.skillService = skillService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** Who am I? Used by the frontend to restore the session after a page reload. */
    @GetMapping("/auth/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return authService.me(me.id());
    }

    /** Skill catalog for autocomplete (any logged-in user). */
    @GetMapping("/skills")
    public List<Map<String, String>> skills() {
        return skillService.all().stream()
                .map((Skill s) -> Map.of("name", s.getName(), "category", s.getCategory()))
                .toList();
    }
}
