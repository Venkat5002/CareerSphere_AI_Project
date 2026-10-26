package com.careersphere.service;

import com.careersphere.dto.JobDtos.RequirementDto;
import com.careersphere.dto.JobDtos.*;
import com.careersphere.entity.*;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.ApplicationRepository;
import com.careersphere.repository.JobRepository;
import com.careersphere.repository.RecruiterProfileRepository;
import com.careersphere.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final RecruiterProfileRepository recruiterProfiles;
    private final SkillService skillService;
    private final StudentProfileService studentProfileService;
    private final MatchService matchService;

    public JobService(JobRepository jobs, ApplicationRepository applications, UserRepository users,
                      RecruiterProfileRepository recruiterProfiles, SkillService skillService,
                      StudentProfileService studentProfileService, MatchService matchService) {
        this.jobs = jobs;
        this.applications = applications;
        this.users = users;
        this.recruiterProfiles = recruiterProfiles;
        this.skillService = skillService;
        this.studentProfileService = studentProfileService;
        this.matchService = matchService;
    }

    // ------------------------------------------------------------------ student side

    /** Open jobs with an explainable match for the logged-in student. sort = "match" or "newest". */
    @Transactional
    public List<JobDto> openJobsForStudent(Long studentId, String query, String sort) {
        StudentProfile profile = studentProfileService.profileOf(studentId);
        Map<Long, ApplicationStatus> myApps = applications.findByStudentIdOrderByAppliedAtDesc(studentId).stream()
                .collect(Collectors.toMap(a -> a.getJob().getId(), Application::getStatus));

        String q = query == null ? "" : query.trim().toLowerCase();
        List<JobDto> result = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN).stream()
                .filter(j -> q.isEmpty() || matchesQuery(j, q))
                .map(j -> toDto(j, null, matchService.match(profile, j),
                        myApps.get(j.getId())))
                .collect(Collectors.toCollection(ArrayList::new));

        if (!"newest".equalsIgnoreCase(sort)) {
            result.sort(Comparator.comparingInt((JobDto d) -> d.match().score()).reversed());
        }
        return result;
    }

    @Transactional
    public JobDto jobForStudent(Long studentId, Long jobId) {
        Job job = jobs.findById(jobId)
                .filter(j -> j.getStatus() == JobStatus.OPEN || applications.existsByJobIdAndStudentId(j.getId(), studentId))
                .orElseThrow(() -> ApiException.notFound("Job not found."));
        StudentProfile profile = studentProfileService.profileOf(studentId);
        ApplicationStatus mine = applications.findByStudentIdOrderByAppliedAtDesc(studentId).stream()
                .filter(a -> a.getJob().getId().equals(jobId)).map(Application::getStatus).findFirst().orElse(null);
        return toDto(job, null, matchService.match(profile, job), mine);
    }

    // ------------------------------------------------------------------ recruiter side

    @Transactional(readOnly = true)
    public List<JobDto> recruiterJobs(Long recruiterId) {
        Map<Long, Long> counts = applications.findByJobRecruiterId(recruiterId).stream()
                .collect(Collectors.groupingBy(a -> a.getJob().getId(), Collectors.counting()));
        return jobs.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).stream()
                .map(j -> toDto(j, counts.getOrDefault(j.getId(), 0L), null, null))
                .toList();
    }

    @Transactional
    public JobDto create(Long recruiterId, JobRequest req) {
        User recruiter = users.findById(recruiterId).orElseThrow(() -> ApiException.notFound("User not found."));
        Job job = new Job(recruiter);
        apply(job, req, recruiterId);
        return toDto(jobs.save(job), 0L, null, null);
    }

    @Transactional
    public JobDto update(Long recruiterId, Long jobId, JobRequest req) {
        Job job = ownedJob(recruiterId, jobId);
        apply(job, req, recruiterId);
        return toDto(job, null, null, null);
    }

    @Transactional
    public JobDto setStatus(Long recruiterId, Long jobId, JobStatus status) {
        Job job = ownedJob(recruiterId, jobId);
        job.setStatus(status);
        return toDto(job, null, null, null);
    }

    @Transactional
    public void delete(Long recruiterId, Long jobId) {
        Job job = ownedJob(recruiterId, jobId);
        if (applications.existsByJobId(jobId)) {
            throw ApiException.conflict("Students have already applied to this job. Close it instead of deleting it.");
        }
        jobs.delete(job);
    }

    /** Loads a job and makes sure the logged-in recruiter is the one who posted it. */
    @Transactional(readOnly = true)
    public Job ownedJob(Long recruiterId, Long jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (!job.getRecruiter().getId().equals(recruiterId)) {
            throw ApiException.forbidden("You can only manage jobs you posted.");
        }
        return job;
    }

    // ------------------------------------------------------------------ helpers

    private void apply(Job job, JobRequest req, Long recruiterId) {
        String company = req.companyName();
        if (company == null || company.isBlank()) {
            company = recruiterProfiles.findByUserId(recruiterId).map(RecruiterProfile::getCompanyName).orElse(null);
        }
        if (company == null || company.isBlank()) {
            throw ApiException.badRequest("Enter a company name, or add one to your company profile.");
        }
        job.setTitle(req.title().trim());
        job.setCompanyName(company.trim());
        job.setLocation(req.location());
        job.setSalary(req.salary());
        job.setJobType(req.jobType());
        job.setExperience(req.experience());
        job.setDescription(req.description());
        job.setMinCgpa(req.minCgpa());
        job.setDeadline(req.deadline());
        job.setOpenings(req.openings());
        job.setSkills(skillService.resolve(req.skills()));
        // Levels and must-have flags arrive by name; store them by skill id.
        java.util.Map<Long, Integer> levels = new java.util.HashMap<>();
        java.util.Set<Long> nice = new java.util.HashSet<>();
        for (com.careersphere.entity.Skill s : job.getSkills()) {
            if (req.skillLevels() != null) {
                req.skillLevels().forEach((name, lvl) -> {
                    if (name != null && name.trim().equalsIgnoreCase(s.getName())) levels.put(s.getId(), lvl);
                });
            }
            if (req.niceToHave() != null && req.niceToHave().stream()
                    .anyMatch(n -> n != null && n.trim().equalsIgnoreCase(s.getName()))) nice.add(s.getId());
        }
        if (!job.getSkills().isEmpty() && nice.size() == job.getSkills().size()) {
            throw ApiException.badRequest("At least one skill must be a must-have.");
        }
        job.setRequirements(levels, nice);
    }

    /** Each required skill with its minimum level and must-have flag, must-haves first. */
    public static List<RequirementDto> requirements(Job j) {
        return j.getSkills().stream()
                .sorted(java.util.Comparator.comparing((com.careersphere.entity.Skill s) -> j.isNiceToHave(s))
                        .thenComparing(com.careersphere.entity.Skill::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new RequirementDto(s.getName(), j.minLevelOf(s),
                        com.careersphere.entity.SkillLevel.label(j.minLevelOf(s)), !j.isNiceToHave(s)))
                .toList();
    }

    private boolean matchesQuery(Job j, String q) {
        return Optional.ofNullable(j.getTitle()).orElse("").toLowerCase().contains(q)
                || Optional.ofNullable(j.getCompanyName()).orElse("").toLowerCase().contains(q)
                || Optional.ofNullable(j.getLocation()).orElse("").toLowerCase().contains(q)
                || j.getSkills().stream().anyMatch(s -> s.getName().toLowerCase().contains(q));
    }

    public JobDto toDto(Job j, Long applicantCount, MatchDto match, ApplicationStatus mine) {
        return new JobDto(j.getId(), j.getTitle(), j.getCompanyName(), j.getLocation(), j.getSalary(),
                j.getJobType(), j.getExperience(), j.getDescription(), j.getMinCgpa(), j.getDeadline(),
                j.getOpenings(), j.getStatus(), j.getCreatedAt(), SkillService.names(j.getSkills()),
                requirements(j), applicantCount, match, mine);
    }
}
