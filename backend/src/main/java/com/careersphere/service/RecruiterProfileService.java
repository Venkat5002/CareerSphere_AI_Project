package com.careersphere.service;

import com.careersphere.dto.DashboardDtos.RecruiterProfileDto;
import com.careersphere.dto.DashboardDtos.RecruiterProfileUpdateRequest;
import com.careersphere.entity.RecruiterProfile;
import com.careersphere.entity.User;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.RecruiterProfileRepository;
import com.careersphere.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterProfileService {

    private final RecruiterProfileRepository profiles;
    private final UserRepository users;

    public RecruiterProfileService(RecruiterProfileRepository profiles, UserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    @Transactional
    public RecruiterProfileDto get(Long userId) {
        return toDto(profileOf(userId));
    }

    @Transactional
    public RecruiterProfileDto update(Long userId, RecruiterProfileUpdateRequest req) {
        RecruiterProfile p = profileOf(userId);
        p.getUser().setFullName(req.fullName().trim());
        p.setCompanyName(trim(req.companyName()));
        p.setDesignation(trim(req.designation()));
        p.setWebsite(trim(req.website()));
        p.setLocation(trim(req.location()));
        p.setDescription(trim(req.description()));
        return toDto(p);
    }

    private RecruiterProfile profileOf(Long userId) {
        return profiles.findByUserId(userId).orElseGet(() -> {
            User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
            return profiles.save(new RecruiterProfile(user));
        });
    }

    private RecruiterProfileDto toDto(RecruiterProfile p) {
        return new RecruiterProfileDto(p.getUser().getFullName(), p.getUser().getEmail(), p.getCompanyName(),
                p.getDesignation(), p.getWebsite(), p.getLocation(), p.getDescription());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
