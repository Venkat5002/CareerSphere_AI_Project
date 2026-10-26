package com.careersphere.service;

import com.careersphere.dto.StudentDtos.ProfileDto;
import com.careersphere.dto.StudentDtos.ProfileUpdateRequest;
import com.careersphere.entity.StudentProfile;
import com.careersphere.entity.User;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.ResumeRepository;
import com.careersphere.repository.StudentProfileRepository;
import com.careersphere.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careersphere.entity.Skill;
import com.careersphere.entity.SkillLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentProfileService {

    private final StudentProfileRepository profiles;
    private final UserRepository users;
    private final ResumeRepository resumes;
    private final SkillService skillService;

    public StudentProfileService(StudentProfileRepository profiles, UserRepository users,
                                 ResumeRepository resumes, SkillService skillService) {
        this.profiles = profiles;
        this.users = users;
        this.resumes = resumes;
        this.skillService = skillService;
    }

    /** Loads the profile of the logged-in student, creating it if somehow missing. */
    @Transactional
    public StudentProfile profileOf(Long userId) {
        return profiles.findByUserId(userId).orElseGet(() -> {
            User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
            return profiles.save(new StudentProfile(user));
        });
    }

    @Transactional
    public ProfileDto get(Long userId) {
        return toDto(profileOf(userId));
    }

    @Transactional
    public ProfileDto update(Long userId, ProfileUpdateRequest req) {
        StudentProfile p = profileOf(userId);   // always the existing row -> UPDATE, never a duplicate INSERT
        p.getUser().setFullName(req.fullName().trim());
        p.setPhone(blankToNull(req.phone()));
        p.setCollege(blankToNull(req.college()));
        p.setBranch(blankToNull(req.branch()));
        p.setGraduationYear(req.graduationYear());
        p.setCgpa(req.cgpa());
        p.setTenthPercentage(req.tenthPercentage());
        p.setTwelfthPercentage(req.twelfthPercentage());
        p.setAbout(blankToNull(req.about()));
        p.setLinkedinUrl(blankToNull(req.linkedinUrl()));
        p.setGithubUrl(blankToNull(req.githubUrl()));
        p.setSkills(skillService.resolve(req.skills()));
        if (req.headline() != null) p.setHeadline(blankToNull(req.headline()));
        applyLevels(p, req.skillLevels());
        return toDto(p);
    }

    /** Sets levels by skill name (case-insensitive) and drops levels of removed skills. */
    static void applyLevels(StudentProfile p, Map<String, Integer> byName) {
        if (byName != null) {
            for (Skill s : p.getSkills()) {
                byName.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getKey().trim().equalsIgnoreCase(s.getName()))
                        .findFirst()
                        .ifPresent(e -> p.setLevel(s, SkillLevel.clamp(e.getValue())));
            }
        }
        p.pruneLevels();
        // A verified skill keeps the level the assessment or certificate proved.
        for (Skill s : p.getSkills()) {
            Integer v = p.getVerifiedLevels().get(s.getId());
            if (v != null) p.setLevel(s, v);
        }
    }

    /** Skill name to verified level, for skills proved by an assessment or certificate. */
    public static Map<String, Integer> verifiedByName(StudentProfile p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String name : SkillService.names(p.getSkills())) {
            for (Skill s : p.getSkills()) {
                if (s.getName().equals(name) && p.verifiedLevelOf(s) > 0) out.put(name, p.verifiedLevelOf(s));
            }
        }
        return out;
    }

    /** Skill name to level (1-3), sorted by name. */
    public static Map<String, Integer> levelsByName(StudentProfile p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String name : SkillService.names(p.getSkills())) {
            for (Skill s : p.getSkills()) if (s.getName().equals(name)) out.put(name, p.levelOf(s));
        }
        return out;
    }

    /** Adds skills (e.g. confirmed suggestions from the resume) without removing existing ones. */
    @Transactional
    public ProfileDto addSkills(Long userId, List<String> names) {
        StudentProfile p = profileOf(userId);
        java.util.Set<com.careersphere.entity.Skill> merged = new java.util.HashSet<>(p.getSkills());
        merged.addAll(skillService.resolve(names));
        if (merged.size() > 40) throw ApiException.badRequest("A profile can have at most 40 skills.");
        p.setSkills(merged);
        return toDto(p);
    }

    /** Which profile items are still missing - drives the completion % and Next Best Action. */
    public List<String> missingItems(StudentProfile p) {
        List<String> missing = new ArrayList<>();
        if (p.getPhone() == null) missing.add("Phone");
        if (p.getCollege() == null) missing.add("College");
        if (p.getBranch() == null) missing.add("Branch");
        if (p.getGraduationYear() == null) missing.add("Graduation year");
        if (p.getCgpa() == null) missing.add("CGPA");
        if (p.getTenthPercentage() == null) missing.add("10th percentage");
        if (p.getTwelfthPercentage() == null) missing.add("12th percentage");
        if (p.getAbout() == null) missing.add("About you");
        if (p.getSkills().size() < 3) missing.add("At least 3 skills");
        if (!resumes.existsByStudentId(p.getUser().getId())) missing.add("Resume");
        return missing;
    }

    /** 10 items, 10% each. */
    public int completion(List<String> missing) {
        return (10 - missing.size()) * 10;
    }

    private ProfileDto toDto(StudentProfile p) {
        List<String> missing = missingItems(p);
        User u = p.getUser();
        return new ProfileDto(u.getFullName(), u.getEmail(), p.getPhone(), p.getCollege(), p.getBranch(),
                p.getGraduationYear(), p.getCgpa(), p.getTenthPercentage(), p.getTwelfthPercentage(),
                p.getAbout(), p.getLinkedinUrl(), p.getGithubUrl(), SkillService.names(p.getSkills()),
                levelsByName(p), completion(missing), missing,
                p.getHeadline(), p.isPlacementConsent(), verifiedByName(p), p.hasVideo());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
