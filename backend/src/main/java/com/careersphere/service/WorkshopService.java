package com.careersphere.service;

import com.careersphere.entity.Job;
import com.careersphere.entity.JobStatus;
import com.careersphere.entity.Skill;
import com.careersphere.entity.StudentProfile;
import com.careersphere.repository.JobRepository;
import com.careersphere.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Workshop Planner: Opportunity Math for the whole batch.
 *
 * Question: if the placement cell can run k skill workshops, which k skills, and for whom,
 * give the most students a job within reach (or the most new student-job matches)?
 *
 * Skills work in combination: a student with only Git gets nothing from a Java workshop alone or a
 * Spring Boot workshop alone, but both together bring a Java + Spring Boot + Git job within reach.
 * Picking one workshop at a time (greedy) cannot see that, so the planner checks every combination
 * of k skills and keeps the best (exact search). Each student is invited only to the workshops they
 * need, and only if the plan helps them. When there are too many combinations to check quickly, it
 * falls back to the one-at-a-time greedy plan and says so.
 *
 * Every score uses the same rule as the student's Career Map (MatchService): weighted share of
 * required skills (must-have 2, nice to have 1, half credit below the level asked), rounded,
 * at least 70%, plus the CGPA cutoff. A workshop brings attendees up to Intermediate, so a
 * student who already has a skill at Beginner can still gain from it.
 */
@Service
public class WorkshopService {

    /** A workshop brings every attendee up to this level (Intermediate). */
    public static final int WORKSHOP_LEVEL = 2;

    /** Most workshops one plan can contain. */
    public static final int MAX_WORKSHOPS = 6;

    /** Exact search runs while combinations x students x jobs stays under this (well under a second). */
    static final long EXACT_SEARCH_BUDGET = 300_000_000L;

    public record StudentGain(Long studentId, String name, String email, String branch, int before, int after) {}

    public record SkillImpact(String skill, String category, int openJobsAsking, int studentsLacking,
                              int studentsHelped, int firstReach, int newMatches, List<StudentGain> invite) {}

    public record PlanStep(int order, String skill, int invited, int firstReach, int newMatches,
                           int studentsWithReachAfter, List<StudentGain> invite) {}

    /** Outcome of another way of choosing the workshops, shown for comparison. */
    public record Alternative(String method, List<String> skills, int studentsWithReach, int matches) {}

    /** What the placement cell is optimising for. */
    public enum Goal {
        /** Give as many students as possible a job within reach (then most matches). */
        REACH,
        /** Create the most (student, job) matches overall (then most students reached). */
        MATCHES
    }

    /** EXACT: every combination was checked. GREEDY: too many combinations, chose one workshop at a time. */
    public enum Method { EXACT, GREEDY }

    public record WorkshopPlan(String goal, int totalStudents, int openJobs, int threshold,
                               int studentsWithReachNow, int matchesNow,
                               int studentsWithReachAfterPlan, int matchesAfterPlan,
                               String method, long combinationsChecked,
                               List<PlanStep> plan, Alternative oneAtATime, List<SkillImpact> ranking) {}

    private final JobRepository jobs;
    private final StudentProfileRepository profiles;
    private final MatchService match;

    public WorkshopService(JobRepository jobs, StudentProfileRepository profiles, MatchService match) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.match = match;
    }

    @Transactional(readOnly = true)
    public WorkshopPlan plan(int workshops, Goal goal) {
        List<Job> open = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        Batch b = new Batch(profiles.findAll(), open, match);
        int k = Math.max(1, Math.min(workshops, MAX_WORKSHOPS));

        long[] none = b.empty();
        int reachNow = 0, matchesNow = 0;
        for (int s = 0; s < b.students(); s++) {
            int r = b.reach(s, none);
            if (r > 0) reachNow++;
            matchesNow += r;
        }

        List<SkillImpact> ranking = rankSingleSkills(b, goal);
        Greedy greedy = greedy(b, k, goal);

        int size = Math.min(k, b.skills());
        long combinations = binomial(b.skills(), size);
        boolean exact = size > 0 && combinations <= EXACT_SEARCH_BUDGET
                && combinations * Math.max(1, b.students()) * Math.max(1, open.size()) * b.words <= EXACT_SEARCH_BUDGET;

        if (!exact) {
            return new WorkshopPlan(goal.name(), b.students(), open.size(), MatchService.MATCH_THRESHOLD,
                    reachNow, matchesNow, greedy.reach, greedy.matches, Method.GREEDY.name(), 0,
                    greedy.steps, null, ranking);
        }

        int[] best = exactSearch(b, size, goal);
        Plan p = buildPlan(b, best, goal);
        Alternative alt = new Alternative("ONE_AT_A_TIME",
                greedy.steps.stream().map(PlanStep::skill).toList(), greedy.reach, greedy.matches);
        return new WorkshopPlan(goal.name(), b.students(), open.size(), MatchService.MATCH_THRESHOLD,
                reachNow, matchesNow, p.reach, p.matches, Method.EXACT.name(), combinations,
                p.steps, alt, ranking);
    }

    // ------------------------------------------------------------------ exact search

    /** Tries every combination of `size` candidate skills and returns the best one (skill indexes). */
    private int[] exactSearch(Batch b, int size, Goal goal) {
        int[] current = new int[size];
        int[] best = new int[size];
        long[][] masks = new long[size + 1][b.words];
        int[] bestScore = {-1, -1};
        search(b, goal, 0, 0, size, current, masks, best, bestScore);
        return best;
    }

    private void search(Batch b, Goal goal, int depth, int from, int size, int[] current, long[][] masks,
                        int[] best, int[] bestScore) {
        if (depth == size) {
            int reach = 0, matches = 0;
            for (int s = 0; s < b.students(); s++) {
                int r = b.reach(s, masks[depth]);
                if (r > 0) reach++;
                matches += r;
            }
            int primary = goal == Goal.REACH ? reach : matches;
            int secondary = goal == Goal.REACH ? matches : reach;
            if (primary > bestScore[0] || (primary == bestScore[0] && secondary > bestScore[1])) {
                bestScore[0] = primary;
                bestScore[1] = secondary;
                System.arraycopy(current, 0, best, 0, size);
            }
            return;
        }
        for (int i = from; i <= b.skills() - (size - depth); i++) {
            current[depth] = i;
            System.arraycopy(masks[depth], 0, masks[depth + 1], 0, b.words);
            masks[depth + 1][i >>> 6] |= 1L << (i & 63);
            search(b, goal, depth + 1, i + 1, size, current, masks, best, bestScore);
        }
    }

    private record Plan(List<PlanStep> steps, int reach, int matches) {}

    /**
     * Turns the chosen skills into an ordered plan: each student is invited to the smallest set of
     * these workshops that gives them everything the plan can give them; workshops nobody needs are
     * dropped; the order puts the workshop with the biggest immediate effect first.
     */
    private Plan buildPlan(Batch b, int[] chosen, Goal goal) {
        int n = b.students();
        int[] before = new int[n], after = new int[n];
        List<Set<Integer>> needs = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            before[s] = b.reach(s, b.empty());
            after[s] = b.reach(s, b.maskOf(chosen));
            needs.add(after[s] > before[s] ? smallestSubset(b, s, chosen, after[s]) : Set.of());
        }

        List<Integer> remaining = new ArrayList<>();
        for (int skill : chosen) {
            boolean anyone = needs.stream().anyMatch(x -> x.contains(skill));
            if (anyone) remaining.add(skill);
        }

        long[][] state = new long[n][b.words];
        List<PlanStep> steps = new ArrayList<>();
        int order = 0;
        while (!remaining.isEmpty()) {
            int pick = -1, pickFirst = -1, pickNew = -1, pickInvited = 0;
            for (int skill : remaining) {
                int first = 0, added = 0, invited = 0;
                for (int s = 0; s < n; s++) {
                    if (!needs.get(s).contains(skill)) continue;
                    invited++;
                    int was = b.reach(s, state[s]);
                    long[] next = state[s].clone();
                    next[skill >>> 6] |= 1L << (skill & 63);
                    int now = b.reach(s, next);
                    added += now - was;
                    if (was == 0 && now > 0) first++;
                }
                if (pick < 0 || better(goal, first, added, pickFirst, pickNew)
                        || (first == pickFirst && added == pickNew && b.name(skill).compareTo(b.name(pick)) < 0)) {
                    pick = skill; pickFirst = first; pickNew = added; pickInvited = invited;
                }
            }
            List<StudentGain> invite = new ArrayList<>();
            for (int s = 0; s < n; s++) {
                if (!needs.get(s).contains(pick)) continue;
                state[s][pick >>> 6] |= 1L << (pick & 63);
                invite.add(b.gain(s, before[s], after[s]));
            }
            sortInvite(invite);
            int reachAfter = 0;
            for (int s = 0; s < n; s++) if (b.reach(s, state[s]) > 0) reachAfter++;
            steps.add(new PlanStep(++order, b.name(pick), pickInvited, pickFirst, pickNew, reachAfter, invite));
            remaining.remove(Integer.valueOf(pick));
        }

        int reach = 0, matches = 0;
        for (int s = 0; s < n; s++) {
            int r = b.reach(s, state[s]);
            if (r > 0) reach++;
            matches += r;
        }
        return new Plan(steps, reach, matches);
    }

    /** Smallest subset of the chosen skills (the ones the student lacks) that reaches `target` jobs. */
    private Set<Integer> smallestSubset(Batch b, int s, int[] chosen, int target) {
        List<Integer> lacking = new ArrayList<>();
        for (int skill : chosen) if (!b.has(s, skill)) lacking.add(skill);
        int m = lacking.size();
        Set<Integer> bestSet = new HashSet<>(lacking);
        int bestSize = m;
        for (int bits = 1; bits < (1 << m); bits++) {
            int count = Integer.bitCount(bits);
            if (count >= bestSize) continue;
            long[] mask = b.empty();
            for (int i = 0; i < m; i++) if ((bits & (1 << i)) != 0) {
                int skill = lacking.get(i);
                mask[skill >>> 6] |= 1L << (skill & 63);
            }
            if (b.reach(s, mask) == target) {
                bestSize = count;
                bestSet = new HashSet<>();
                for (int i = 0; i < m; i++) if ((bits & (1 << i)) != 0) bestSet.add(lacking.get(i));
            }
        }
        return bestSet;
    }

    // ------------------------------------------------------------------ greedy (one workshop at a time)

    private record Greedy(List<PlanStep> steps, int reach, int matches) {}

    /** Picks the best single workshop, assumes its invitees learned it, repeats. Used for comparison and as a fallback. */
    private Greedy greedy(Batch b, int k, Goal goal) {
        int n = b.students();
        long[][] state = new long[n][b.words];
        Set<Integer> used = new HashSet<>();
        List<PlanStep> steps = new ArrayList<>();
        for (int round = 1; round <= k; round++) {
            List<SkillImpact> scores = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            for (int skill = 0; skill < b.skills(); skill++) {
                if (used.contains(skill)) continue;
                scores.add(impact(b, skill, state));
                ids.add(skill);
            }
            if (scores.isEmpty()) break;
            int bestIdx = 0;
            for (int i = 1; i < scores.size(); i++) if (ranksAbove(goal, scores.get(i), scores.get(bestIdx))) bestIdx = i;
            SkillImpact best = scores.get(bestIdx);
            if (best.newMatches() == 0) break;
            int skill = ids.get(bestIdx);
            used.add(skill);
            Set<Long> invited = new HashSet<>();
            for (StudentGain g : best.invite()) invited.add(g.studentId());
            for (int s = 0; s < n; s++) {
                if (invited.contains(b.studentId(s))) state[s][skill >>> 6] |= 1L << (skill & 63);
            }
            int reachAfter = 0;
            for (int s = 0; s < n; s++) if (b.reach(s, state[s]) > 0) reachAfter++;
            steps.add(new PlanStep(round, best.skill(), best.studentsHelped(), best.firstReach(), best.newMatches(),
                    reachAfter, best.invite()));
        }
        int reach = 0, matches = 0;
        for (int s = 0; s < n; s++) {
            int r = b.reach(s, state[s]);
            if (r > 0) reach++;
            matches += r;
        }
        return new Greedy(steps, reach, matches);
    }

    /** Effect of teaching one skill to every student who lacks it, given each student's current skills. */
    private SkillImpact impact(Batch b, int skill, long[][] state) {
        int lacking = 0, helped = 0, first = 0, added = 0;
        List<StudentGain> invite = new ArrayList<>();
        for (int s = 0; s < b.students(); s++) {
            if (b.has(s, skill) || (state[s][skill >>> 6] & (1L << (skill & 63))) != 0) continue;
            lacking++;
            int was = b.reach(s, state[s]);
            long[] next = state[s].clone();
            next[skill >>> 6] |= 1L << (skill & 63);
            int now = b.reach(s, next);
            if (now > was) {
                helped++;
                added += now - was;
                if (was == 0) first++;
                invite.add(b.gain(s, was, now));
            }
        }
        sortInvite(invite);
        return new SkillImpact(b.name(skill), b.category(skill), b.asking(skill), lacking, helped, first, added, invite);
    }

    /** Every candidate skill measured on today's batch, one at a time, best first. */
    private List<SkillImpact> rankSingleSkills(Batch b, Goal goal) {
        long[][] today = new long[b.students()][b.words];
        List<SkillImpact> out = new ArrayList<>();
        for (int skill = 0; skill < b.skills(); skill++) out.add(impact(b, skill, today));
        out.sort((x, y) -> ranksAbove(goal, x, y) ? -1 : ranksAbove(goal, y, x) ? 1 : 0);
        return out.stream().limit(12).toList();
    }

    private static boolean ranksAbove(Goal goal, SkillImpact x, SkillImpact y) {
        int a1 = goal == Goal.REACH ? x.firstReach() : x.newMatches();
        int b1 = goal == Goal.REACH ? y.firstReach() : y.newMatches();
        if (a1 != b1) return a1 > b1;
        int a2 = goal == Goal.REACH ? x.newMatches() : x.firstReach();
        int b2 = goal == Goal.REACH ? y.newMatches() : y.firstReach();
        if (a2 != b2) return a2 > b2;
        if (x.studentsHelped() != y.studentsHelped()) return x.studentsHelped() > y.studentsHelped();
        return x.skill().compareTo(y.skill()) < 0;
    }

    private static boolean better(Goal goal, int first, int added, int bestFirst, int bestAdded) {
        int a1 = goal == Goal.REACH ? first : added, b1 = goal == Goal.REACH ? bestFirst : bestAdded;
        if (a1 != b1) return a1 > b1;
        int a2 = goal == Goal.REACH ? added : first, b2 = goal == Goal.REACH ? bestAdded : bestFirst;
        return a2 > b2;
    }

    private static void sortInvite(List<StudentGain> invite) {
        invite.sort(Comparator.comparingInt((StudentGain g) -> g.before() == 0 ? 0 : 1)
                .thenComparing(Comparator.comparingInt((StudentGain g) -> g.after() - g.before()).reversed())
                .thenComparing(StudentGain::name));
    }

    /** n choose k, capped so it never overflows. */
    static long binomial(int n, int k) {
        if (k < 0 || k > n) return 0;
        long r = 1;
        for (int i = 1; i <= k; i++) {
            r = r * (n - k + i) / i;
            if (r > Long.MAX_VALUE / 64) return Long.MAX_VALUE / 64;
        }
        return r;
    }

    // ------------------------------------------------------------------ the batch as bit masks

    /**
     * Students and open jobs with skills as bit masks over the candidate skills (every skill an open
     * job asks for), so one match check is a few AND + bit-count operations.
     */
    private static final class Batch {
        final int words;
        private final List<Skill> skills = new ArrayList<>();
        private final List<StudentProfile> students;
        /** Per student: skills known at level 1+ (any), 2+ and 3+. */
        private final long[][] any, atLeast2, atLeast3;
        /** Per job: skills by [minimum level - 1][weight - 1]. */
        private final long[][][][] jobMask;
        /** Per job: total weight in half units (2 x sum of weights). */
        private final long[] jobTotal;
        private final boolean[][] eligible;
        private final int[] asking;

        Batch(List<StudentProfile> students, List<Job> open, MatchService match) {
            this.students = students;
            Map<Long, Integer> index = new LinkedHashMap<>();
            List<Integer> askingList = new ArrayList<>();
            List<Skill> found = new ArrayList<>();
            for (Job j : open) for (Skill s : j.getSkills()) {
                if (!index.containsKey(s.getId())) { index.put(s.getId(), found.size()); found.add(s); askingList.add(0); }
                int i = index.get(s.getId());
                askingList.set(i, askingList.get(i) + 1);
            }
            // Stable, readable order: most-asked skills first, then by name.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < found.size(); i++) order.add(i);
            order.sort(Comparator.comparingInt((Integer i) -> -askingList.get(i)).thenComparing(i -> found.get(i).getName()));
            Map<Long, Integer> pos = new HashMap<>();
            asking = new int[found.size()];
            for (int i = 0; i < order.size(); i++) {
                Skill s = found.get(order.get(i));
                pos.put(s.getId(), i);
                skills.add(s);
                asking[i] = askingList.get(order.get(i));
            }
            words = Math.max(1, (skills.size() + 63) / 64);

            jobMask = new long[open.size()][3][2][words];
            jobTotal = new long[open.size()];
            for (int j = 0; j < open.size(); j++) {
                Job job = open.get(j);
                for (Skill s : job.getSkills()) {
                    int w = MatchService.weight(job, s);
                    jobTotal[j] += 2L * w;
                    set(jobMask[j][job.minLevelOf(s) - 1][w - 1], pos.get(s.getId()));
                }
            }
            any = new long[students.size()][words];
            atLeast2 = new long[students.size()][words];
            atLeast3 = new long[students.size()][words];
            eligible = new boolean[students.size()][open.size()];
            for (int s = 0; s < students.size(); s++) {
                StudentProfile p = students.get(s);
                for (Skill sk : p.getSkills()) {
                    Integer i = pos.get(sk.getId());
                    if (i == null) continue;
                    int level = p.levelOf(sk);
                    if (level >= 1) set(any[s], i);
                    if (level >= 2) set(atLeast2[s], i);
                    if (level >= 3) set(atLeast3[s], i);
                }
                for (int j = 0; j < open.size(); j++) eligible[s][j] = match.cgpaEligible(p.getCgpa(), open.get(j));
            }
        }

        private static void set(long[] mask, int i) { mask[i >>> 6] |= 1L << (i & 63); }

        long[] empty() { return new long[words]; }

        long[] maskOf(int[] skillIndexes) {
            long[] m = empty();
            for (int i : skillIndexes) set(m, i);
            return m;
        }

        int skills() { return skills.size(); }
        int students() { return students.size(); }
        String name(int skill) { return skills.get(skill).getName(); }
        String category(int skill) { return skills.get(skill).getCategory(); }
        int asking(int skill) { return asking[skill]; }
        Long studentId(int s) { return students.get(s).getUser().getId(); }
        /** True when student s is already at the workshop level (or above), so the workshop adds nothing. */
        boolean has(int s, int skill) { return (atLeast2[s][skill >>> 6] & (1L << (skill & 63))) != 0; }

        /**
         * Jobs within reach for student s if they also attended the `extra` workshops, which bring a skill
         * up to WORKSHOP_LEVEL (Intermediate). Same rule as MatchService: each skill earns its weight once
         * for being known at all and once more for meeting the level, all in half units.
         */
        int reach(int s, long[] extra) {
            int n = 0;
            for (int j = 0; j < jobMask.length; j++) {
                if (!eligible[s][j]) continue;
                long total = jobTotal[j];
                if (total == 0) { n++; continue; }
                long half = 0;
                for (int w = 0; w < words; w++) {
                    long a = any[s][w] | extra[w];
                    long l2 = atLeast2[s][w] | extra[w];
                    long[] meets = { a, l2, atLeast3[s][w] };
                    for (int need = 0; need < 3; need++) {
                        for (int wt = 0; wt < 2; wt++) {
                            long m = jobMask[j][need][wt][w];
                            if (m == 0) continue;
                            half += (wt + 1L) * (Long.bitCount(a & m) + Long.bitCount(meets[need] & m));
                        }
                    }
                }
                if (MatchService.percent(half, total) >= MatchService.MATCH_THRESHOLD) n++;
            }
            return n;
        }

        StudentGain gain(int s, int before, int after) {
            StudentProfile p = students.get(s);
            return new StudentGain(p.getUser().getId(), p.getUser().getFullName(), p.getUser().getEmail(),
                    p.getBranch(), before, after);
        }
    }
}
