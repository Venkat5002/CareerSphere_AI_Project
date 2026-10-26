package com.careersphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Dashboards for all three roles, plus recruiter profile. */
public final class DashboardDtos {

    private DashboardDtos() {}

    /**
     * Opportunity Math: if the student learned this one skill,
     * how many more open jobs would they reach the match threshold for?
     */
    public record OpportunityDto(String skill, String category, int jobsUnlocked, int jobsRequiring,
                                 List<String> exampleJobs, int currentLevel, int targetLevel) {}

    public record NextActionDto(String type, String title, String reason, String link) {}

    public record StudentDashboardDto(
            String fullName, int profileCompletion, List<String> missingItems, boolean hasResume,
            int skillCount, int openJobs, int jobsAboveThreshold, int matchThreshold,
            long totalApplications, Map<String, Long> applicationsByStatus,
            List<JobDtos.JobDto> topMatches, List<OpportunityDto> opportunities,
            NextActionDto nextBestAction) {}

    public record RecruiterDashboardDto(String companyName, long activeJobs, long totalJobs,
                                        long totalApplicants, Map<String, Long> applicantsByStatus) {}

    public record RecruiterProfileDto(String fullName, String email, String companyName, String designation,
                                      String website, String location, String description) {}

    public record RecruiterProfileUpdateRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String fullName,
            @Size(max = 150) String companyName, @Size(max = 100) String designation,
            @Size(max = 255) String website, @Size(max = 100) String location,
            @Size(max = 2000) String description) {}

    /** Curriculum vs market: what % of open jobs need a skill vs what % of students have it. */
    public record SkillGapDto(String skill, int jobDemandPercent, int studentSupplyPercent, int gap) {}

    public record AdminStatsDto(long students, long recruiters, long openJobs, long totalJobs,
                                long applications, long selected, int placementRatePercent,
                                Map<String, Long> applicationsByStatus, List<SkillGapDto> skillGaps) {}

    public record AdminUserDto(Long id, String fullName, String email, String role, String createdAt) {}
}
