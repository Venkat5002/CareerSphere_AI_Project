package com.careersphere.service;

import com.careersphere.dto.DashboardDtos.*;
import com.careersphere.dto.JobDtos.JobDto;
import com.careersphere.dto.JobDtos.MatchDto;
import com.careersphere.entity.*;
import com.careersphere.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Builds the dashboards for students, recruiters and admins from real database data. */
@Service
public class DashboardService {

    private final StudentProfileService profileService;
    private final ResumeRepository resumes;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final RecruiterProfileRepository recruiterProfiles;
    private final MatchService matchService;
    private final JobService jobService;
    private final SkillService skillService;

    public DashboardService(StudentProfileService profileService, ResumeRepository resumes, JobRepository jobs,
                            ApplicationRepository applications, UserRepository users,
                            StudentProfileRepository studentProfiles, RecruiterProfileRepository recruiterProfiles,
                            MatchService matchService, JobService jobService, SkillService skillService) {
        this.profileService = profileService;
        this.resumes = resumes;
        this.jobs = jobs;
        this.applications = applications;
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.recruiterProfiles = recruiterProfiles;
        this.matchService = matchService;
        this.jobService = jobService;
        this.skillService = skillService;
    }

    // ------------------------------------------------------------------ student

    @Transactional
    public StudentDashboardDto student(Long studentId) {
        StudentProfile p = profileService.profileOf(studentId);
        List<String> missing = profileService.missingItems(p);
        int completion = profileService.completion(missing);
        boolean hasResume = resumes.existsByStudentId(studentId);

        List<Application> myApps = applications.findByStudentIdOrderByAppliedAtDesc(studentId);
        Set<Long> appliedJobIds = myApps.stream().map(a -> a.getJob().getId()).collect(Collectors.toSet());
        Map<String, Long> byStatus = countByStatus(myApps);

        List<Job> open = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        int reachable = (int) open.stream().filter(j -> matchService.reachable(p, j)).count();

        // Best jobs the student has not applied to yet
        List<JobDto> top = open.stream()
                .filter(j -> !appliedJobIds.contains(j.getId()))
                .map(j -> jobService.toDto(j, null, matchService.match(p, j), null))
                .sorted(Comparator.comparingInt((JobDto d) -> d.match().score()).reversed())
                .limit(3)
                .toList();

        List<OpportunityDto> opportunities = matchService.opportunities(p, open, 5);
        NextActionDto next = nextBestAction(completion, missing, hasResume, p, top, opportunities);

        return new StudentDashboardDto(p.getUser().getFullName(), completion, missing, hasResume,
                p.getSkills().size(), open.size(), reachable, MatchService.MATCH_THRESHOLD,
                myApps.size(), byStatus, top, opportunities, next);
    }

    /** Full Opportunity Math ranking for the student (used by the Opportunities page). */
    @Transactional
    public List<OpportunityDto> opportunities(Long studentId) {
        StudentProfile p = profileService.profileOf(studentId);
        return matchService.opportunities(p, jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN), 15);
    }

    /**
     * "What if I learned these skills?" - recomputes matches against all open jobs
     * as if the student already had the extra skills.
     */
    @Transactional
    public Map<String, Object> whatIf(Long studentId, List<String> extraSkillNames) {
        StudentProfile p = profileService.profileOf(studentId);
        Map<Long, Integer> current = p.levels();
        Map<Long, Integer> simulated = new HashMap<>(current);
        List<String> changed = new ArrayList<>();
        // Each entry is "Skill" (learn it, Beginner) or "Skill:2" (reach that level).
        // Only existing catalog skills are looked up - a simulation must not create new skills.
        for (String raw : extraSkillNames) {
            SimulatedSkill want = SimulatedSkill.parse(raw);
            if (want == null) continue;
            for (Skill s : skillService.all()) {
                if (!want.name().equalsIgnoreCase(s.getName())) continue;
                int have = current.getOrDefault(s.getId(), 0);
                if (want.level() > have) {
                    simulated.merge(s.getId(), want.level(), Math::max);
                    changed.add(want.level() > 1 || have > 0
                            ? s.getName() + " (" + SkillLevel.label(want.level()) + ")" : s.getName());
                }
            }
        }

        List<Job> open = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        List<Map<String, Object>> newlyReachable = new ArrayList<>();
        List<Map<String, Object>> perJob = new ArrayList<>();
        int before = 0, after = 0;
        for (Job j : open) {
            boolean was = matchService.reachable(current, p.getCgpa(), j);
            boolean now = matchService.reachable(simulated, p.getCgpa(), j);
            int oldScore = matchService.match(current, p.getCgpa(), j).score();
            int newScore = matchService.match(simulated, p.getCgpa(), j).score();
            if (was) before++;
            if (now) after++;
            if (!was && now) {
                newlyReachable.add(Map.of("jobId", j.getId(), "title", j.getTitle(),
                        "companyName", j.getCompanyName(), "newScore", newScore, "oldScore", oldScore));
            }
            perJob.add(Map.of("jobId", j.getId(), "oldScore", oldScore, "newScore", newScore,
                    "reachableBefore", was, "reachableAfter", now));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        changed.sort(String.CASE_INSENSITIVE_ORDER);
        result.put("addedSkills", changed);
        result.put("openJobs", open.size());
        result.put("reachableBefore", before);
        result.put("reachableAfter", after);
        result.put("threshold", MatchService.MATCH_THRESHOLD);
        result.put("newlyReachable", newlyReachable);
        result.put("jobs", perJob);
        return result;
    }

    /** Picks the single most useful next step, in priority order. Every rule is based on real data. */
    private NextActionDto nextBestAction(int completion, List<String> missing, boolean hasResume, StudentProfile p,
                                         List<JobDto> top, List<OpportunityDto> opportunities) {
        if (completion < 60) {
            return new NextActionDto("COMPLETE_PROFILE", "Complete your profile",
                    "Your profile is " + completion + "% complete. Still missing: " + String.join(", ", missing) + ".",
                    "/student/profile");
        }
        if (!hasResume) {
            return new NextActionDto("UPLOAD_RESUME", "Upload your resume",
                    "You need a resume to apply, and recruiters open it before shortlisting.", "/student/profile");
        }
        if (!p.isPlacementConsent()) {
            return new NextActionDto("GIVE_CONSENT", "Give placement consent",
                    "Recruiters can only see your profile, and you can only apply, after you agree to share it.",
                    "/student/placement");
        }
        if (p.getSkills().size() < 3) {
            return new NextActionDto("ADD_SKILLS", "Add your skills",
                    "Matching works on skills. Add at least 3 so CareerSphere can find jobs for you.", "/student/profile");
        }
        Optional<JobDto> applyNow = top.stream()
                .filter(j -> j.match().score() >= MatchService.MATCH_THRESHOLD && j.match().cgpaEligible())
                .findFirst();
        if (applyNow.isPresent()) {
            JobDto j = applyNow.get();
            return new NextActionDto("APPLY", "Apply to " + j.title() + " at " + j.companyName(),
                    "You have " + j.match().matchedSkills().size() + " of " + j.skills().size()
                            + " required skills (" + j.match().score() + "% match) and haven't applied yet.",
                    "/student/jobs/" + j.id());
        }
        if (!opportunities.isEmpty() && opportunities.get(0).jobsUnlocked() > 0) {
            OpportunityDto o = opportunities.get(0);
            String title = o.currentLevel() > 0
                    ? "Level up " + o.skill() + " to " + SkillLevel.label(o.targetLevel())
                    : "Learn " + o.skill() + (o.targetLevel() > 1 ? " to " + SkillLevel.label(o.targetLevel()) : "");
            return new NextActionDto("LEARN_SKILL", title,
                    "It would bring " + o.jobsUnlocked() + " more open job" + (o.jobsUnlocked() == 1 ? "" : "s")
                            + " above the " + MatchService.MATCH_THRESHOLD + "% match threshold - more than any other skill.",
                    "/student/opportunities");
        }
        return new NextActionDto("EXPLORE", "Explore open jobs",
                "Browse jobs to see which skills recruiters are asking for.", "/student/jobs");
    }

    // ------------------------------------------------------------------ recruiter

    @Transactional(readOnly = true)
    public RecruiterDashboardDto recruiter(Long recruiterId) {
        List<Application> apps = applications.findByJobRecruiterId(recruiterId);
        String company = recruiterProfiles.findByUserId(recruiterId).map(RecruiterProfile::getCompanyName).orElse(null);
        return new RecruiterDashboardDto(company,
                jobs.countByRecruiterIdAndStatus(recruiterId, JobStatus.OPEN),
                jobs.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).size(),
                apps.size(), countByStatus(apps));
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public AdminStatsDto admin() {
        long students = users.countByRole(Role.STUDENT);
        long selected = applications.countByStatus(ApplicationStatus.SELECTED);
        List<Application> all = applications.findAll();
        long placedStudents = all.stream().filter(a -> a.getStatus() == ApplicationStatus.SELECTED)
                .map(a -> a.getStudent().getId()).distinct().count();
        int placementRate = students == 0 ? 0 : (int) Math.round(100.0 * placedStudents / students);

        return new AdminStatsDto(students, users.countByRole(Role.RECRUITER), jobs.countByStatus(JobStatus.OPEN),
                jobs.count(), all.size(), selected, placementRate, countByStatus(all), skillGaps());
    }

    /** Curriculum vs market: demand (share of open jobs needing a skill) minus supply (share of students having it). */
    private List<SkillGapDto> skillGaps() {
        List<Job> open = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        List<StudentProfile> profiles = studentProfiles.findAll();
        if (open.isEmpty()) return List.of();

        Map<String, Long> demand = open.stream().flatMap(j -> j.getSkills().stream())
                .collect(Collectors.groupingBy(Skill::getName, Collectors.counting()));
        Map<String, Long> supply = profiles.stream().flatMap(p -> p.getSkills().stream())
                .collect(Collectors.groupingBy(Skill::getName, Collectors.counting()));

        return demand.entrySet().stream()
                .map(e -> {
                    int d = (int) Math.round(100.0 * e.getValue() / open.size());
                    int s = profiles.isEmpty() ? 0 : (int) Math.round(100.0 * supply.getOrDefault(e.getKey(), 0L) / profiles.size());
                    return new SkillGapDto(e.getKey(), d, s, d - s);
                })
                .sorted(Comparator.comparingInt(SkillGapDto::gap).reversed().thenComparing(SkillGapDto::skill))
                .limit(10)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> adminUsers() {
        return users.findAllByOrderByCreatedAtDesc().stream()
                .map(u -> new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                        u.getCreatedAt().toString()))
                .toList();
    }

    private static Map<String, Long> countByStatus(List<Application> apps) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) map.put(s.name(), 0L);
        apps.forEach(a -> map.merge(a.getStatus().name(), 1L, Long::sum));
        return map;
    }

    /** One what-if entry: "Docker" means learn it (Beginner); "Java:2" means reach Intermediate in Java. */
    public record SimulatedSkill(String name, int level) {
        public static SimulatedSkill parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String t = raw.trim();
            int level = SkillLevel.BEGINNER.value();
            int colon = t.lastIndexOf(':');
            if (colon > 0 && t.substring(colon + 1).trim().matches("[1-3]")) {
                level = Integer.parseInt(t.substring(colon + 1).trim());
                t = t.substring(0, colon).trim();
            }
            return t.isEmpty() ? null : new SimulatedSkill(t, level);
        }
    }
}
