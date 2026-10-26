package com.careersphere.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Shapes used by the student profile and resume screens. */
public final class StudentDtos {

    private StudentDtos() {}

    public record ProfileDto(
            String fullName, String email, String phone, String college, String branch,
            Integer graduationYear, Double cgpa, Double tenthPercentage, Double twelfthPercentage,
            String about, String linkedinUrl, String githubUrl, List<String> skills,
            Map<String, Integer> skillLevels,
            int completion, List<String> missingItems,
            String headline, boolean placementConsent, Map<String, Integer> verifiedLevels, boolean hasVideo) {}

    public record ProfileUpdateRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String fullName,
            @Pattern(regexp = "^$|^[0-9+\\- ]{7,20}$", message = "Enter a valid phone number") String phone,
            @Size(max = 150) String college,
            @Size(max = 100) String branch,
            @Min(value = 2000, message = "Enter a valid year") @Max(value = 2100, message = "Enter a valid year") Integer graduationYear,
            @DecimalMin(value = "0.0", message = "CGPA must be between 0 and 10") @DecimalMax(value = "10.0", message = "CGPA must be between 0 and 10") Double cgpa,
            @DecimalMin(value = "0.0", message = "Must be between 0 and 100") @DecimalMax(value = "100.0", message = "Must be between 0 and 100") Double tenthPercentage,
            @DecimalMin(value = "0.0", message = "Must be between 0 and 100") @DecimalMax(value = "100.0", message = "Must be between 0 and 100") Double twelfthPercentage,
            @Size(max = 2000) String about,
            @Size(max = 255) String linkedinUrl,
            @Size(max = 255) String githubUrl,
            @Size(max = 40, message = "At most 40 skills") List<@NotBlank @Size(max = 60) String> skills,
            /** Optional. Skill name to level (1 Beginner, 2 Intermediate, 3 Advanced). Left out: levels you already set are kept. */
            Map<String, @Min(1) @Max(3) Integer> skillLevels,
            /** Optional. The role the student is aiming for, shown under their name. */
            @Size(max = 80) String headline) {}

    public record ResumeDto(String originalFileName, String contentType, long fileSize, LocalDateTime uploadedAt) {}

    /**
     * A skill found in the resume. evidence is a phrase copied from the resume that shows the skill;
     * suggestions whose evidence is not really in the resume are removed by the server.
     */
    public record SkillSuggestion(String skill, String category, String evidence, boolean alreadyInProfile) {}

    /** mode: "gemini" or "offline". */
    public record ResumeSkillsResponse(String mode, List<SkillSuggestion> suggestions, int charactersRead, String notice) {}

    public record AddSkillsRequest(@NotEmpty(message = "Choose at least one skill")
                                   @Size(max = 30) List<@NotBlank @Size(max = 60) String> skills) {}
}
