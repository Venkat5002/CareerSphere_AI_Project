package com.careersphere.service;

import com.careersphere.entity.Job;
import com.careersphere.entity.JobStatus;
import com.careersphere.entity.Skill;
import com.careersphere.entity.SkillLevel;
import com.careersphere.entity.StudentProfile;
import com.careersphere.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Personal Skill Roadmap: Opportunity Math for more than one step.
 *
 * Opportunity Math looks at one skill at a time. But skills pay off together: a student with only Git
 * gains nothing from Java alone or Spring Boot alone, yet both together bring a Java + Spring Boot + Git
 * job within reach. So for each student we search every bundle of 1, 2 and 3 actions exactly
 * (an action is "learn skill X" or "raise X to level L") and keep, for each bundle size, the one that
 * brings the most open jobs within reach, using the least effort.
 *
 *   effort of an action = levels gained (learning a skill to Intermediate = 2, Beginner to Advanced = 2)
 *
 * It also finds, for each nearby job, the cheapest set of actions that brings that one job within reach
 * ("path to a job"), and says when a CGPA cutoff, not skills, is what stands in the way.
 */
@Service
public class RoadmapService {

    public static final int MAX_STEPS = 3;

    public record Action(Long skillId, String skill, int from, int to) {
        public int effort() { return to - from; }
        public String label() {
            return from == 0 ? (to == 1 ? "Learn " + skill : "Learn " + skill + " to " + SkillLevel.label(to))
                    : "Level up " + skill + " to " + SkillLevel.label(to);
        }
    }

    public record StepDto(String action, String skill, int fromLevel, int toLevel, int jobsWithinReachAfter) {}

    /** The best bundle of a given size. synergy = the bundle unlocks more than its steps would one at a time. */
    public record BundleDto(int size, List<StepDto> steps, int jobsWithinReach, int newJobs, int effort,
                            boolean synergy, List<String> unlockedJobs) {}

    public record PathDto(Long jobId, String job, String company, int scoreNow, List<String> actions, int effort,
                          String blocker) {}

    public record RoadmapDto(int openJobs, int jobsWithinReachNow, int bestSingleSkillGain, List<BundleDto> bundles,
                             List<PathDto> closestJobs, long bundlesChecked, String howItWorks) {}

    static final String HOW = "Every bundle of 1, 2 and 3 learning steps is checked exactly, using the same match rule as the "
            + "Career Map. Effort counts the levels gained. A bundle marked 'works together' unlocks jobs that none of its steps "
            + "would unlock on its own.";

    private final StudentProfileService profiles;
    private final JobRepository jobs;
    private final MatchService match;

    public RoadmapService(StudentProfileService profiles, JobRepository jobs, MatchService match) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.match = match;
    }

    @Transactional(readOnly = true)
    public RoadmapDto forStudent(Long studentId) {
        StudentProfile p = profiles.profileOf(studentId);
        return plan(p.levels(), p.getCgpa(), jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN));
    }

    /** Pure calculation, so it can be unit tested without a database. */
    public RoadmapDto plan(Map<Long, Integer> levels, Double cgpa, List<Job> open) {
        List<Action> actions = candidateActions(levels, open);
        boolean[] before = new boolean[open.size()];
        int now = 0;
        for (int j = 0; j < open.size(); j++) if (before[j] = match.reachable(levels, cgpa, open.get(j))) now++;

        Map<Action, Integer> single = new HashMap<>();
        for (Action a : actions) single.put(a, reach(apply(levels, List.of(a)), cgpa, open) - now);
        int bestSingle = single.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<BundleDto> bundles = new ArrayList<>();
        long checked = 0;
        int lastReach = now;
        for (int k = 1; k <= Math.min(MAX_STEPS, distinctSkills(actions)); k++) {
            List<Action> best = null;
            int bestReach = -1, bestEffort = Integer.MAX_VALUE;
            for (List<Action> combo : combinations(actions, k)) {
                checked++;
                int r = reach(apply(levels, combo), cgpa, open);
                int effort = combo.stream().mapToInt(Action::effort).sum();
                if (r > bestReach || (r == bestReach && effort < bestEffort)
                        || (r == bestReach && effort == bestEffort && best != null && key(combo).compareTo(key(best)) < 0)) {
                    best = combo; bestReach = r; bestEffort = effort;
                }
            }
            // A bundle is only shown if it unlocks more than any smaller one. A size can gain nothing while the
            // next size gains a lot (skills that only pay off together), so keep looking instead of stopping.
            if (best == null || bestReach <= lastReach) continue;
            bundles.add(describe(levels, cgpa, open, before, now, best, single));
            lastReach = bestReach;
        }
        return new RoadmapDto(open.size(), now, Math.max(0, bestSingle), bundles,
                closestJobs(levels, cgpa, open, before), checked, HOW);
    }

    // ------------------------------------------------------------------ bundles

    /** Every (skill, target level) that some open job asks for above the student's level. */
    static List<Action> candidateActions(Map<Long, Integer> levels, List<Job> open) {
        Map<String, Action> out = new TreeMap<>();
        for (Job job : open) {
            for (Skill s : job.getSkills()) {
                int have = levels.getOrDefault(s.getId(), 0);
                int need = job.minLevelOf(s);
                if (have >= need) continue;
                Action a = new Action(s.getId(), s.getName(), have, need);
                out.putIfAbsent(s.getName() + ":" + need, a);
            }
        }
        return new ArrayList<>(out.values());
    }

    private static int distinctSkills(List<Action> actions) {
        Set<Long> ids = new HashSet<>();
        for (Action a : actions) ids.add(a.skillId());
        return ids.size();
    }

    /** Combinations of k actions with at most one action per skill. */
    static List<List<Action>> combinations(List<Action> actions, int k) {
        List<List<Action>> out = new ArrayList<>();
        walk(actions, k, 0, new ArrayDeque<>(), new HashSet<>(), out);
        return out;
    }

    private static void walk(List<Action> actions, int k, int from, Deque<Action> chosen, Set<Long> skills, List<List<Action>> out) {
        if (chosen.size() == k) { out.add(List.copyOf(chosen)); return; }
        for (int i = from; i < actions.size(); i++) {
            Action a = actions.get(i);
            if (skills.contains(a.skillId())) continue;
            chosen.addLast(a); skills.add(a.skillId());
            walk(actions, k, i + 1, chosen, skills, out);
            chosen.removeLast(); skills.remove(a.skillId());
        }
    }

    private static String key(List<Action> combo) {
        StringBuilder b = new StringBuilder();
        for (Action a : combo) b.append(a.skill()).append(':').append(a.to()).append(',');
        return b.toString();
    }

    static Map<Long, Integer> apply(Map<Long, Integer> levels, List<Action> combo) {
        Map<Long, Integer> out = new HashMap<>(levels);
        for (Action a : combo) out.merge(a.skillId(), a.to(), Math::max);
        return out;
    }

    private int reach(Map<Long, Integer> levels, Double cgpa, List<Job> open) {
        int n = 0;
        for (Job j : open) if (match.reachable(levels, cgpa, j)) n++;
        return n;
    }

    /** Orders the bundle's steps by immediate effect and reports jobs within reach after each step. */
    private BundleDto describe(Map<Long, Integer> levels, Double cgpa, List<Job> open, boolean[] before, int now,
                               List<Action> bundle, Map<Action, Integer> single) {
        List<Action> left = new ArrayList<>(bundle);
        List<Action> done = new ArrayList<>();
        List<StepDto> steps = new ArrayList<>();
        while (!left.isEmpty()) {
            Action pick = null;
            int pickReach = -1;
            for (Action a : left) {
                List<Action> trial = new ArrayList<>(done);
                trial.add(a);
                int r = reach(apply(levels, trial), cgpa, open);
                if (r > pickReach || (r == pickReach && a.effort() < pick.effort())) { pick = a; pickReach = r; }
            }
            done.add(pick);
            left.remove(pick);
            steps.add(new StepDto(pick.label(), pick.skill(), pick.from(), pick.to(), pickReach));
        }
        Map<Long, Integer> after = apply(levels, bundle);
        List<String> unlocked = new ArrayList<>();
        for (int j = 0; j < open.size(); j++) {
            if (!before[j] && match.reachable(after, cgpa, open.get(j))) unlocked.add(open.get(j).getTitle() + " - " + open.get(j).getCompanyName());
        }
        int alone = bundle.stream().mapToInt(a -> Math.max(0, single.getOrDefault(a, 0))).sum();
        int total = steps.get(steps.size() - 1).jobsWithinReachAfter();
        return new BundleDto(bundle.size(), steps, total, total - now, bundle.stream().mapToInt(Action::effort).sum(),
                bundle.size() > 1 && total - now > alone, unlocked);
    }

    // ------------------------------------------------------------------ path to one job

    /** For each job not yet within reach: the cheapest set of actions that brings it within reach. */
    private List<PathDto> closestJobs(Map<Long, Integer> levels, Double cgpa, List<Job> open, boolean[] before) {
        List<PathDto> out = new ArrayList<>();
        for (int j = 0; j < open.size(); j++) {
            if (before[j]) continue;
            Job job = open.get(j);
            int scoreNow = match.score(levels, job);
            if (!match.cgpaEligible(cgpa, job)) {
                out.add(new PathDto(job.getId(), job.getTitle(), job.getCompanyName(), scoreNow, List.of(), Integer.MAX_VALUE,
                        cgpa == null ? "Add your CGPA: this job has a cutoff of " + job.getMinCgpa()
                                : "CGPA cutoff " + job.getMinCgpa() + " (yours " + cgpa + "); skills can't change this"));
                continue;
            }
            List<Action> gaps = candidateActions(levels, List.of(job));
            List<Action> best = null;
            int bestEffort = Integer.MAX_VALUE;
            for (int mask = 1; mask < (1 << gaps.size()); mask++) {
                List<Action> pick = new ArrayList<>();
                int effort = 0;
                for (int i = 0; i < gaps.size(); i++) if ((mask & (1 << i)) != 0) { pick.add(gaps.get(i)); effort += gaps.get(i).effort(); }
                if (effort > bestEffort || (effort == bestEffort && best != null && pick.size() >= best.size())) continue;
                if (match.reachable(apply(levels, pick), cgpa, job)) { best = pick; bestEffort = effort; }
            }
            if (best != null) {
                out.add(new PathDto(job.getId(), job.getTitle(), job.getCompanyName(), scoreNow,
                        best.stream().map(Action::label).toList(), bestEffort, null));
            }
        }
        out.sort(Comparator.comparingInt(PathDto::effort).thenComparing(Comparator.comparingInt(PathDto::scoreNow).reversed())
                .thenComparing(PathDto::job));
        return out.stream().limit(6).toList();
    }
}
