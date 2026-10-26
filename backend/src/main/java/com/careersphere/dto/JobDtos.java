package com.careersphere.dto;

import com.careersphere.entity.ApplicationStatus;
import com.careersphere.entity.JobStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Shapes for jobs, matching and applications. */
public final class JobDtos {

    private JobDtos() {}

    public record JobRequest(
            @NotBlank(message = "Title is required") @Size(max = 120) String title,
            @Size(max = 150) String companyName,
            @Size(max = 100) String location,
            @Size(max = 60) String salary,
            @Size(max = 30) String jobType,
            @Size(max = 60) String experience,
            @Size(max = 4000) String description,
            @DecimalMin(value = "0.0") @DecimalMax(value = "10.0", message = "CGPA must be between 0 and 10") Double minCgpa,
            LocalDate deadline,
            @Min(value = 1, message = "At least 1 opening") Integer openings,
            @NotEmpty(message = "Add at least one required skill") @Size(max = 25) List<@NotBlank @Size(max = 60) String> skills,
            /** Optional. Skill name to minimum level (1 Beginner, 2 Intermediate, 3 Advanced). Missing means Beginner. */
            Map<String, @Min(1) @Max(3) Integer> skillLevels,
            /** Optional. Names of skills that are nice to have; every other skill is a must-have. */
            List<String> niceToHave) {}

    /** One skill a job asks for: the minimum level and whether it is a must-have. */
    public record RequirementDto(String skill, int minLevel, String levelLabel, boolean mustHave) {}

    /** A skill the student has, but below the level the job asks for. */
    public record LevelGapDto(String skill, String have, String need) {}

    /**
     * Explainable match between a student and a job (rule-based, not a black box).
     * Must-have skills weigh 2, nice-to-have 1; a skill below the level asked earns half its weight.
     * With every skill must-have at Beginner, score = matched required skills / total required skills.
     */
    public record MatchDto(int score, List<String> matchedSkills, List<String> missingSkills,
                           boolean cgpaEligible, String explanation,
                           List<LevelGapDto> levelGaps, List<String> niceToHave) {}

    public record JobDto(
            Long id, String title, String companyName, String location, String salary, String jobType,
            String experience, String description, Double minCgpa, LocalDate deadline, Integer openings,
            JobStatus status, LocalDateTime createdAt, List<String> skills,
            List<RequirementDto> requirements,
            Long applicantCount,          // recruiter view only
            MatchDto match,               // student view only
            ApplicationStatus myApplicationStatus) {}   // student view only, null if not applied

    public record ApplyRequest(@NotNull(message = "jobId is required") Long jobId) {}

    public record ApplicationDto(Long id, Long jobId, String jobTitle, String companyName, String location,
                                 ApplicationStatus status, String recruiterNote,
                                 LocalDateTime appliedAt, LocalDateTime updatedAt) {}

    public record ApplicantDto(Long applicationId, Long studentId, String studentName, String email,
                               String college, String branch, Integer graduationYear, Double cgpa,
                               List<String> skills, Map<String, Integer> skillLevels,
                               Map<String, Integer> verifiedLevels, List<String> verifiedCertificates,
                               boolean hasVideo, String headline, String githubUrl, String linkedinUrl, boolean hasResume,
                               ApplicationStatus status, String recruiterNote, LocalDateTime appliedAt,
                               MatchDto match) {}

    public record StatusUpdateRequest(@NotNull(message = "Status is required") ApplicationStatus status,
                                      @Size(max = 1000) String note) {}

    public record JobStatusRequest(@NotNull(message = "Status is required") JobStatus status) {}
}
