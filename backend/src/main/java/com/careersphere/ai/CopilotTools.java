package com.careersphere.ai;

import com.careersphere.dto.CopilotDtos.CopilotAction;
import com.careersphere.dto.DashboardDtos.OpportunityDto;
import com.careersphere.dto.JobDtos.ApplicationDto;
import com.careersphere.dto.JobDtos.JobDto;
import com.careersphere.dto.StudentDtos.ProfileDto;
import com.careersphere.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * The tools the Copilot may use. Every answer about the student comes from these
 * functions, which read the same database and run the same matching rules as the rest of the app.
 * Tools can only READ data or PROPOSE an action; they never change anything.
 */
@Component
public class CopilotTools {

    private final StudentProfileService profiles;
    private final JobService jobs;
    private final ApplicationService applications;
    private final DashboardService dashboard;
    private final ResumeService resumes;
    private final SkillService skills;
    private final ObjectMapper mapper;

    public CopilotTools(StudentProfileService profiles, JobService jobs, ApplicationService applications,
                        DashboardService dashboard, ResumeService resumes, SkillService skills, ObjectMapper mapper) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.applications = applications;
        this.dashboard = dashboard;
        this.resumes = resumes;
        this.skills = skills;
        this.mapper = mapper;
    }

    /** Per-request state: who is asking, and what the tools proposed or read. */
    public static class Session {
        final Long studentId;
        final List<CopilotAction> actions = new ArrayList<>();
        final LinkedHashSet<String> dataUsed = new LinkedHashSet<>();
        public Session(Long studentId) { this.studentId = studentId; }
        public List<CopilotAction> actions() { return actions; }
        public List<String> dataUsed() { return new ArrayList<>(dataUsed); }
    }

    // ------------------------------------------------------------------ declarations for Gemini

    public ArrayNode declarations() {
        ArrayNode tools = mapper.createArrayNode();
        ArrayNode fns = tools.addObject().putArray("functionDeclarations");
        declare(fns, "get_my_snapshot", "The student's profile: name, branch, CGPA, skills, profile completion, missing items, whether a resume is uploaded.");
        ObjectNode list = declare(fns, "list_job_matches", "Open jobs with the student's match score (% of required skills they have), matched and missing skills, CGPA eligibility and application status. Sorted best match first.");
        prop(list, "only_within_reach", "boolean", "If true, only jobs at or above the 70% match threshold that the student meets the CGPA cutoff for.");
        prop(list, "limit", "integer", "Maximum number of jobs to return (default 6).");
        declare(fns, "get_skill_opportunities", "Opportunity Math: for each skill the student lacks or has below the level jobs ask for, how many more open jobs would cross the 70% threshold if they learned it or raised it to targetLevel. Ranked by jobs unlocked.");
        ObjectNode sim = declare(fns, "simulate_learning", "What-if: recompute every open job's match as if the student had learned the given skills. A name like \"Java:2\" means reaching that level (1 Beginner, 2 Intermediate, 3 Advanced). Returns jobs within reach before and after.");
        arrayProp(sim, "skills", "Skill names, e.g. [\"Spring Boot\", \"Docker\"].", true);
        ObjectNode explain = declare(fns, "explain_job", "Full match explanation for one job: required skills, which the student has, which are missing, CGPA rule, description.");
        prop(explain, "job", "string", "The job title (or part of it) or the job id.");
        explain.withObject("parameters").putArray("required").add("job");
        declare(fns, "get_my_applications", "The student's applications with status and any recruiter note.");
        ObjectNode apply = declare(fns, "propose_application", "Show the student an Apply button for a job. Does NOT apply; the student must click to confirm. Use only when the student asks to apply or clearly wants to.");
        prop(apply, "job_id", "integer", "The job id from list_job_matches.");
        apply.withObject("parameters").putArray("required").add("job_id");
        ObjectNode map = declare(fns, "show_on_map", "Show the student a button that animates their Career Map as if they had learned these skills.");
        arrayProp(map, "skills", "Skill names to simulate on the map.", true);
        return tools;
    }

    private ObjectNode declare(ArrayNode fns, String name, String description) {
        ObjectNode fn = fns.addObject();
        fn.put("name", name).put("description", description);
        fn.putObject("parameters").put("type", "object").putObject("properties");
        return fn;
    }

    private void prop(ObjectNode fn, String name, String type, String description) {
        fn.withObject("parameters").withObject("properties").putObject(name).put("type", type).put("description", description);
    }

    private void arrayProp(ObjectNode fn, String name, String description, boolean required) {
        ObjectNode p = fn.withObject("parameters").withObject("properties").putObject(name);
        p.put("type", "array").put("description", description).putObject("items").put("type", "string");
        if (required) fn.withObject("parameters").putArray("required").add(name);
    }

    // ------------------------------------------------------------------ execution

    /** Runs a tool by name. Unknown tools and bad arguments return an "error" field instead of throwing. */
    public Map<String, Object> run(Session s, String name, JsonNode args) {
        try {
            return switch (name) {
                case "get_my_snapshot" -> snapshot(s);
                case "list_job_matches" -> jobMatches(s, args.path("only_within_reach").asBoolean(false), args.path("limit").asInt(6));
                case "get_skill_opportunities" -> opportunities(s);
                case "simulate_learning" -> simulate(s, stringList(args.path("skills")));
                case "explain_job" -> explainJob(s, args.path("job").asText(""));
                case "get_my_applications" -> myApplications(s);
                case "propose_application" -> proposeApplication(s, args.path("job_id").asLong(-1));
                case "show_on_map" -> showOnMap(s, stringList(args.path("skills")));
                default -> Map.of("error", "Unknown tool " + name);
            };
        } catch (Exception e) {
            return Map.of("error", e.getMessage() == null ? "Tool failed" : e.getMessage());
        }
    }

    public Map<String, Object> snapshot(Session s) {
        s.dataUsed.add("your profile");
        ProfileDto p = profiles.get(s.studentId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.fullName());
        m.put("branch", p.branch());
        m.put("graduationYear", p.graduationYear());
        m.put("cgpa", p.cgpa());
        m.put("skills", p.skills());
        m.put("skillLevels", p.skillLevels());   // 1 Beginner, 2 Intermediate, 3 Advanced
        m.put("verifiedSkillLevels", p.verifiedLevels());
        m.put("placementConsent", p.placementConsent());
        m.put("hasIntroductionVideo", p.hasVideo());
        m.put("profileCompletionPercent", p.completion());
        m.put("missingProfileItems", p.missingItems());
        m.put("hasResume", resumes.get(s.studentId) != null);
        m.put("hasGithub", p.githubUrl() != null);
        return m;
    }

    public Map<String, Object> jobMatches(Session s, boolean onlyWithinReach, int limit) {
        s.dataUsed.add("open jobs and your match scores");
        List<Map<String, Object>> list = new ArrayList<>();
        for (JobDto j : jobs.openJobsForStudent(s.studentId, null, "match")) {
            boolean reach = j.match().score() >= MatchService.MATCH_THRESHOLD && j.match().cgpaEligible();
            if (onlyWithinReach && !reach) continue;
            list.add(jobSummary(j, reach));
            if (list.size() >= Math.max(1, Math.min(limit, 20))) break;
        }
        return Map.of("threshold", MatchService.MATCH_THRESHOLD, "jobs", list);
    }

    public Map<String, Object> opportunities(Session s) {
        s.dataUsed.add("Opportunity Math over all open jobs");
        List<OpportunityDto> list = dashboard.opportunities(s.studentId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (OpportunityDto o : list.stream().limit(8).toList()) {
            out.add(Map.of("skill", o.skill(), "jobsUnlocked", o.jobsUnlocked(),
                    "openJobsRequiringIt", o.jobsRequiring(), "exampleJobs", o.exampleJobs(),
                    "currentLevel", com.careersphere.entity.SkillLevel.label(o.currentLevel()),
                    "targetLevel", com.careersphere.entity.SkillLevel.label(o.targetLevel()),
                    "simulate", o.targetLevel() > 1 ? o.skill() + ":" + o.targetLevel() : o.skill()));
        }
        return Map.of("threshold", MatchService.MATCH_THRESHOLD, "ranking", out);
    }

    public Map<String, Object> simulate(Session s, List<String> names) {
        s.dataUsed.add("what-if simulation");
        Map<String, Object> r = new LinkedHashMap<>(dashboard.whatIf(s.studentId, names));
        r.remove("jobs");   // per-job details are for the map; the summary is enough here
        List<String> known = SkillService.names(skills.all().stream()
                .filter(k -> names.stream().map(DashboardService.SimulatedSkill::parse).filter(java.util.Objects::nonNull)
                        .anyMatch(n -> n.name().equalsIgnoreCase(k.getName()))).toList());
        List<String> unknown = names.stream().filter(n -> known.stream().noneMatch(k -> k.equalsIgnoreCase(n.trim()))).toList();
        if (!unknown.isEmpty()) r.put("unknownSkills", unknown);
        return r;
    }

    public Map<String, Object> explainJob(Session s, String query) {
        s.dataUsed.add("job requirements");
        Optional<JobDto> job = findJob(s, query);
        if (job.isEmpty()) return Map.of("error", "No open job matches '" + query + "'.");
        JobDto j = jobs.jobForStudent(s.studentId, job.get().id());
        Map<String, Object> m = jobSummary(j, j.match().score() >= MatchService.MATCH_THRESHOLD && j.match().cgpaEligible());
        m.put("explanation", j.match().explanation());
        m.put("requirements", j.requirements());
        m.put("levelGaps", j.match().levelGaps());
        m.put("minCgpa", j.minCgpa());
        m.put("salary", j.salary());
        m.put("location", j.location());
        m.put("description", j.description());
        s.actions.add(CopilotAction.openJob(j.id(), j.title()));
        return m;
    }

    public Map<String, Object> myApplications(Session s) {
        s.dataUsed.add("your applications");
        List<Map<String, Object>> list = new ArrayList<>();
        for (ApplicationDto a : applications.myApplications(s.studentId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("job", a.jobTitle());
            m.put("company", a.companyName());
            m.put("status", a.status().name());
            m.put("recruiterNote", a.recruiterNote());
            list.add(m);
        }
        return Map.of("applications", list);
    }

    public Map<String, Object> proposeApplication(Session s, long jobId) {
        JobDto j = jobs.jobForStudent(s.studentId, jobId);
        if (j.myApplicationStatus() != null) return Map.of("error", "Already applied; status is " + j.myApplicationStatus());
        if (resumes.get(s.studentId) == null) {
            s.actions.add(CopilotAction.openPage("/student/profile", "Upload resume"));
            return Map.of("error", "The student has no resume yet, so they cannot apply. An Upload resume button was shown.");
        }
        if (!profiles.get(s.studentId).placementConsent()) {
            s.actions.add(CopilotAction.openPage("/student/placement", "Give placement consent"));
            return Map.of("error", "The student hasn't given placement consent yet, so they cannot apply. A button to the placement profile was shown.");
        }
        s.actions.add(CopilotAction.apply(j.id(), j.title() + " at " + j.companyName()));
        return Map.of("shown", "An Apply button for " + j.title() + " was shown. The student must click it to apply.");
    }

    public Map<String, Object> showOnMap(Session s, List<String> names) {
        if (names.isEmpty()) return Map.of("error", "No skills given.");
        s.actions.add(CopilotAction.simulate(names));
        return Map.of("shown", "A 'Show on my map' button was shown for " + String.join(", ", names) + ".");
    }

    // ------------------------------------------------------------------ helpers

    Optional<JobDto> findJob(Session s, String query) {
        String q = query.trim().toLowerCase();
        List<JobDto> open = jobs.openJobsForStudent(s.studentId, null, "match");
        if (q.matches("\\d+")) {
            long id = Long.parseLong(q);
            return open.stream().filter(j -> j.id() == id).findFirst();
        }
        return open.stream()
                .filter(j -> j.title().toLowerCase().contains(q) || q.contains(j.title().toLowerCase()))
                .findFirst()
                .or(() -> open.stream()   // fall back to word overlap: "java backend" -> "Java Backend Developer"
                        .max(Comparator.comparingLong(j -> Arrays.stream(q.split("\\W+"))
                                .filter(w -> w.length() > 2 && j.title().toLowerCase().contains(w)).count()))
                        .filter(j -> Arrays.stream(q.split("\\W+")).anyMatch(w -> w.length() > 2 && j.title().toLowerCase().contains(w))));
    }

    private Map<String, Object> jobSummary(JobDto j, boolean withinReach) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id());
        m.put("title", j.title());
        m.put("company", j.companyName());
        m.put("matchScore", j.match().score());
        m.put("matchedSkills", j.match().matchedSkills());
        m.put("missingSkills", j.match().missingSkills());
        if (!j.match().levelGaps().isEmpty()) m.put("belowLevel", j.match().levelGaps());
        m.put("meetsCgpaCutoff", j.match().cgpaEligible());
        m.put("withinReach", withinReach);
        m.put("applicationStatus", j.myApplicationStatus() == null ? "NOT_APPLIED" : j.myApplicationStatus().name());
        return m;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(n -> { if (!n.asText().isBlank()) out.add(n.asText().trim()); });
        return out;
    }
}
