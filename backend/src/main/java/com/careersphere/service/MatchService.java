package com.careersphere.service;

import com.careersphere.dto.DashboardDtos.OpportunityDto;
import com.careersphere.dto.JobDtos.LevelGapDto;
import com.careersphere.dto.JobDtos.MatchDto;
import com.careersphere.entity.Job;
import com.careersphere.entity.Skill;
import com.careersphere.entity.SkillLevel;
import com.careersphere.entity.StudentProfile;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Explainable, rule-based matching and "Opportunity Math", with skill levels.
 *
 * Every skill on a job has a weight: 2 if it is a must-have, 1 if it is nice to have.
 * A student earns the full weight when their level is at least the level the job asks for,
 * half the weight when they have the skill but at a lower level, and nothing when they don't have it.
 *
 *   score = 100 x (credit earned) / (total weight), rounded half up.
 *
 * With the defaults (every skill must-have, every level Beginner) this is exactly
 * matched skills / required skills x 100, the original rule.
 * CGPA is a separate eligibility check, not mixed into the score.
 *
 * All arithmetic is done in integer "half units" so this class and WorkshopService
 * always agree to the last point.
 */
@Service
public class MatchService {

    /** A job counts as "within reach" when the score is at least this and the CGPA cutoff is met. */
    public static final int MATCH_THRESHOLD = 70;

    // ---------------------------------------------------------------- scoring core

    /** Weight of a skill on a job: must-have 2, nice to have 1. */
    public static int weight(Job job, Skill skill) {
        return job.isNiceToHave(skill) ? 1 : 2;
    }

    /** Credit in half units: 2w if level >= needed, w if 0 < level < needed, 0 if missing. */
    public static int halfCredit(int level, int needed, int weight) {
        if (level <= 0) return 0;
        return level >= needed ? 2 * weight : weight;
    }

    /** round(100 x half / total), half up, in integers. total is in half units too. */
    public static int percent(long half, long total) {
        if (total <= 0) return 100;
        return (int) ((200L * half + total) / (2L * total));
    }

    /** Score of a student (skill id to level; absent means not known) for a job. */
    public int score(Map<Long, Integer> levels, Job job) {
        long half = 0, total = 0;
        for (Skill s : job.getSkills()) {
            int w = weight(job, s);
            total += 2L * w;
            half += halfCredit(levels.getOrDefault(s.getId(), 0), job.minLevelOf(s), w);
        }
        return percent(half, total);
    }

    // ---------------------------------------------------------------- explained match

    public MatchDto match(StudentProfile p, Job job) {
        return match(p.levels(), p.getCgpa(), job);
    }

    /** Every skill counts as Beginner. Kept for callers and tests that only know skill names. */
    public MatchDto match(Set<Skill> studentSkills, Double cgpa, Job job) {
        return match(beginner(studentSkills), cgpa, job);
    }

    public MatchDto match(Map<Long, Integer> levels, Double cgpa, Job job) {
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<LevelGapDto> gaps = new ArrayList<>();
        List<String> nice = new ArrayList<>();
        for (Skill s : sorted(job.getSkills())) {
            int have = levels.getOrDefault(s.getId(), 0);
            int need = job.minLevelOf(s);
            if (job.isNiceToHave(s)) nice.add(s.getName());
            if (have <= 0) missing.add(s.getName());
            else if (have >= need) matched.add(s.getName());
            else {
                matched.add(s.getName());
                gaps.add(new LevelGapDto(s.getName(), SkillLevel.label(have), SkillLevel.label(need)));
            }
        }
        int total = job.getSkills().size();
        int score = score(levels, job);
        boolean eligible = cgpaEligible(cgpa, job);

        StringBuilder why = new StringBuilder();
        if (total == 0) {
            why.append("This job does not list specific skills.");
        } else if (missing.isEmpty() && gaps.isEmpty()) {
            why.append("You have all ").append(total).append(" required skills.");
        } else if (matched.isEmpty()) {
            why.append("You have none of the ").append(total).append(" required skills yet. Missing: ")
               .append(join(missing, nice)).append('.');
        } else {
            why.append("You have ").append(matched.size()).append(" of ").append(total)
               .append(" required skills (").append(String.join(", ", matched)).append(").");
            if (!missing.isEmpty()) why.append(" Missing: ").append(join(missing, nice)).append('.');
        }
        if (!gaps.isEmpty()) {
            why.append(" Below the level asked (half credit): ");
            List<String> parts = new ArrayList<>();
            for (LevelGapDto g : gaps) parts.add(g.skill() + " (you: " + g.have() + ", needs " + g.need() + ")");
            why.append(String.join(", ", parts)).append('.');
        }
        if (job.getMinCgpa() != null && !eligible) {
            if (cgpa == null) why.append(" Add your CGPA to check eligibility (minimum ").append(job.getMinCgpa()).append(").");
            else why.append(" Your CGPA ").append(cgpa).append(" is below the minimum of ").append(job.getMinCgpa()).append('.');
        }
        return new MatchDto(score, matched, missing, eligible, why.toString(), gaps, nice);
    }

    private static String join(List<String> names, List<String> nice) {
        List<String> out = new ArrayList<>();
        for (String n : names) out.add(nice.contains(n) ? n + " (nice to have)" : n);
        return String.join(", ", out);
    }

    public boolean cgpaEligible(Double cgpa, Job job) {
        return job.getMinCgpa() == null || (cgpa != null && cgpa >= job.getMinCgpa());
    }

    // ---------------------------------------------------------------- within reach

    public boolean reachable(StudentProfile p, Job job) {
        return reachable(p.levels(), p.getCgpa(), job);
    }

    public boolean reachable(Set<Skill> studentSkills, Double cgpa, Job job) {
        return reachable(beginner(studentSkills), cgpa, job);
    }

    /** True when the student meets the threshold and the CGPA cutoff. */
    public boolean reachable(Map<Long, Integer> levels, Double cgpa, Job job) {
        return cgpaEligible(cgpa, job) && score(levels, job) >= MATCH_THRESHOLD;
    }

    // ---------------------------------------------------------------- Opportunity Math

    public List<OpportunityDto> opportunities(StudentProfile p, List<Job> openJobs, int limit) {
        return opportunities(p.levels(), p.getCgpa(), openJobs, limit);
    }

    public List<OpportunityDto> opportunities(Set<Skill> studentSkills, Double cgpa, List<Job> openJobs, int limit) {
        return opportunities(beginner(studentSkills), cgpa, openJobs, limit);
    }

    /**
     * Opportunity Math.
     * An action is "learn skill X" or "raise skill X to level L". For every action that would
     * close a gap on some open job, pretend the student took it and count how many open jobs
     * would newly come within reach. For each skill we keep its best target level (most jobs
     * unlocked, then the lowest level), then rank skills by jobs unlocked, then by how many
     * open jobs ask for more than the student has, then by name.
     */
    public List<OpportunityDto> opportunities(Map<Long, Integer> levels, Double cgpa, List<Job> openJobs, int limit) {
        Map<Long, Skill> skillById = new HashMap<>();
        Map<Long, TreeSet<Integer>> targets = new HashMap<>();
        Map<Long, Integer> requiring = new HashMap<>();
        for (Job job : openJobs) {
            for (Skill s : job.getSkills()) {
                int have = levels.getOrDefault(s.getId(), 0);
                int need = job.minLevelOf(s);
                if (have >= need) continue;
                skillById.put(s.getId(), s);
                requiring.merge(s.getId(), 1, Integer::sum);
                targets.computeIfAbsent(s.getId(), k -> new TreeSet<>()).add(need);
            }
        }

        boolean[] before = new boolean[openJobs.size()];
        for (int j = 0; j < openJobs.size(); j++) before[j] = reachable(levels, cgpa, openJobs.get(j));

        List<OpportunityDto> out = new ArrayList<>();
        for (Map.Entry<Long, TreeSet<Integer>> e : targets.entrySet()) {
            Long id = e.getKey();
            Skill skill = skillById.get(id);
            int current = levels.getOrDefault(id, 0);
            int bestTarget = e.getValue().first();
            List<String> bestExamples = List.of();
            int bestGain = -1;
            for (int target : e.getValue()) {               // ascending, so ties keep the lower level
                Map<Long, Integer> after = new HashMap<>(levels);
                after.put(id, target);
                int gain = 0;
                List<String> examples = new ArrayList<>();
                for (int j = 0; j < openJobs.size(); j++) {
                    Job job = openJobs.get(j);
                    if (before[j] || job.getSkills().stream().noneMatch(x -> x.getId().equals(id))) continue;
                    if (reachable(after, cgpa, job)) {
                        gain++;
                        examples.add(job.getTitle() + " - " + job.getCompanyName());
                    }
                }
                if (gain > bestGain) {
                    bestGain = gain;
                    bestTarget = target;
                    bestExamples = examples;
                }
            }
            out.add(new OpportunityDto(skill.getName(), skill.getCategory(), bestGain,
                    requiring.getOrDefault(id, 0), bestExamples.stream().limit(3).toList(),
                    current, bestTarget));
        }
        return out.stream()
                .sorted(Comparator.comparingInt(OpportunityDto::jobsUnlocked).reversed()
                        .thenComparing(Comparator.comparingInt(OpportunityDto::jobsRequiring).reversed())
                        .thenComparing(OpportunityDto::skill))
                .limit(limit)
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    /** Every skill at Beginner level. */
    public static Map<Long, Integer> beginner(Collection<Skill> skills) {
        Map<Long, Integer> out = new HashMap<>();
        for (Skill s : skills) out.put(s.getId(), SkillLevel.BEGINNER.value());
        return out;
    }

    private static List<Skill> sorted(Collection<Skill> skills) {
        return skills.stream().sorted(Comparator.comparing(Skill::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
