package com.careersphere.service;

import com.careersphere.dto.ReadinessDtos.*;
import com.careersphere.entity.*;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.AssessmentAttemptRepository;
import com.careersphere.repository.ResumeRepository;
import com.careersphere.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Skill assessments: an 8-question, 10-minute multiple-choice test per skill.
 *
 * The questions and the option order are fixed when the test starts and stored with the attempt,
 * so answers are graded on the server and the browser never receives the correct answers.
 *
 *   8 of 8 correct  -> Advanced verified
 *   6 or 7          -> Intermediate verified
 *   4 or 5          -> Beginner verified
 *   fewer than 4    -> not verified, try again in 24 hours
 *
 * A verified result sets the student's level for that skill (and adds the skill if it was missing),
 * so the match score uses a level that has been checked. Recruiters see a "verified" mark.
 */
@Service
public class AssessmentService {

    public static final int MINUTES = 10;
    public static final int QUESTIONS = 8;
    /** Submissions this long after the end are refused (covers slow networks). */
    static final int GRACE_SECONDS = 90;
    static final int RETAKE_HOURS = 24;
    public static final String PASS_RULE =
            "8 of 8 correct verifies Advanced, 6-7 Intermediate, 4-5 Beginner. Below 4 you can try again after 24 hours.";

    private final StudentProfileService profiles;
    private final ResumeRepository resumes;
    private final AssessmentAttemptRepository attempts;
    private final UserRepository users;
    private final SkillService skillService;
    private final QuestionBank bank;
    private final Random random;

    @Autowired
    public AssessmentService(StudentProfileService profiles, ResumeRepository resumes,
                             AssessmentAttemptRepository attempts, UserRepository users,
                             SkillService skillService, QuestionBank bank) {
        this(profiles, resumes, attempts, users, skillService, bank, new Random());
    }

    AssessmentService(StudentProfileService profiles, ResumeRepository resumes, AssessmentAttemptRepository attempts,
                      UserRepository users, SkillService skillService, QuestionBank bank, Random random) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.attempts = attempts;
        this.users = users;
        this.skillService = skillService;
        this.bank = bank;
        this.random = random;
    }

    /** Level proved by a number of correct answers out of 8. */
    public static int levelFor(int correct) {
        if (correct >= 8) return 3;
        if (correct >= 6) return 2;
        if (correct >= 4) return 1;
        return 0;
    }

    /** Why the assessment is locked, or null when it is open. Same order as the placement profile steps. */
    public String lockedReason(StudentProfile p) {
        if (!resumes.existsByStudentId(p.getUser().getId())) return "Upload your résumé first.";
        if (!p.isPlacementConsent()) return "Give placement consent on your placement profile first.";
        return null;
    }

    @Transactional(readOnly = true)
    public AssessmentsDto overview(Long studentId) {
        StudentProfile p = profiles.profileOf(studentId);
        List<AssessmentAttempt> mine = attempts.findByStudentIdOrderByStartedAtDesc(studentId);
        List<AssessmentInfo> out = new ArrayList<>();
        for (String name : bank.skills()) {
            Skill inProfile = p.getSkills().stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
            AssessmentAttempt last = mine.stream()
                    .filter(a -> a.isSubmitted() && a.getSkill().getName().equalsIgnoreCase(name)).findFirst().orElse(null);
            int verified = inProfile == null ? 0 : p.verifiedLevelOf(inProfile);
            LocalDateTime retake = last != null && last.getVerifiedLevel() != null && last.getVerifiedLevel() == 0
                    ? last.getSubmittedAt().plusHours(RETAKE_HOURS) : null;
            if (retake != null && retake.isBefore(LocalDateTime.now())) retake = null;
            out.add(new AssessmentInfo(name, inProfile != null, inProfile == null ? 0 : p.levelOf(inProfile), verified,
                    verified == 0 ? null : SkillLevel.label(verified),
                    last == null ? null : last.getCorrect(), last == null ? null : last.getTotal(),
                    last == null ? null : last.getSubmittedAt(), retake));
        }
        // Skills in the profile first, then the rest alphabetically.
        out.sort(Comparator.comparing((AssessmentInfo a) -> !a.inProfile()).thenComparing(AssessmentInfo::skill));
        String locked = lockedReason(p);
        return new AssessmentsDto(locked == null, locked, MINUTES, QUESTIONS, PASS_RULE, out);
    }

    @Transactional
    public AttemptDto start(Long studentId, String skillName) {
        StudentProfile p = profiles.profileOf(studentId);
        String locked = lockedReason(p);
        if (locked != null) throw ApiException.badRequest(locked);
        if (!bank.has(skillName)) throw ApiException.notFound("There is no assessment for " + skillName + " yet.");
        List<QuestionBank.Question> pool = bank.forSkill(skillName);
        if (pool.size() < QUESTIONS) throw ApiException.notFound("There is no assessment for " + skillName + " yet.");

        List<AssessmentAttempt> mine = attempts.findByStudentIdOrderByStartedAtDesc(studentId);
        LocalDateTime now = LocalDateTime.now();
        for (AssessmentAttempt a : mine) {
            if (!a.getSkill().getName().equalsIgnoreCase(skillName)) continue;
            // An unfinished attempt that is still running is handed back, so a page refresh doesn't restart the clock.
            if (!a.isSubmitted() && a.getStartedAt().plusMinutes(MINUTES).isAfter(now)) return toDto(a);
            if (a.isSubmitted() && a.getVerifiedLevel() != null && a.getVerifiedLevel() == 0
                    && a.getSubmittedAt().plusHours(RETAKE_HOURS).isAfter(now)) {
                throw ApiException.badRequest("You can take the " + a.getSkill().getName()
                        + " assessment again after " + a.getSubmittedAt().plusHours(RETAKE_HOURS).toLocalTime().withNano(0) + " tomorrow.");
            }
            if (a.isSubmitted()) break;   // the latest finished attempt decides
        }

        List<QuestionBank.Question> chosen = new ArrayList<>(pool);
        Collections.shuffle(chosen, random);
        chosen = chosen.subList(0, QUESTIONS);
        StringJoiner ids = new StringJoiner(","), orders = new StringJoiner(";");
        for (QuestionBank.Question q : chosen) {
            ids.add(q.id());
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(order, random);
            StringBuilder o = new StringBuilder();
            order.forEach(o::append);
            orders.add(o);
        }
        Skill skill = skillService.resolve(List.of(skillName)).iterator().next();
        User student = users.findById(studentId).orElseThrow(() -> ApiException.notFound("User not found."));
        return toDto(attempts.save(new AssessmentAttempt(student, skill, ids.toString(), orders.toString())));
    }

    @Transactional
    public ResultDto submit(Long studentId, Long attemptId, List<Integer> answers) {
        AssessmentAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Assessment not found."));
        if (!a.getStudent().getId().equals(studentId)) throw ApiException.forbidden("This assessment belongs to another student.");
        if (a.isSubmitted()) throw ApiException.conflict("This assessment was already submitted.");

        String[] ids = a.getQuestionIds().split(",");
        String[] orders = a.getOptionOrder().split(";");
        List<Boolean> right = new ArrayList<>();
        boolean late = LocalDateTime.now().isAfter(a.getStartedAt().plusMinutes(MINUTES).plusSeconds(GRACE_SECONDS));
        int correct = 0;
        for (int i = 0; i < ids.length; i++) {
            Integer shown = answers != null && i < answers.size() ? answers.get(i) : null;
            boolean ok = !late && shown != null && shown >= 0 && shown < orders[i].length()
                    && orders[i].charAt(shown) == '0';     // option 0 in the bank is the correct one
            right.add(ok);
            if (ok) correct++;
        }
        int level = levelFor(correct);
        a.finish(correct, ids.length, level);

        StudentProfile p = profiles.profileOf(studentId);
        Skill skill = a.getSkill();
        String before = SkillLevel.label(p.levelOf(skill));
        String message;
        if (level > 0) {
            if (p.getSkills().stream().noneMatch(s -> s.getId().equals(skill.getId()))) {
                Set<Skill> skills = new HashSet<>(p.getSkills());
                skills.add(skill);
                p.setSkills(skills);
            }
            p.setLevel(skill, level);
            p.getVerifiedLevels().put(skill.getId(), level);
            message = "Verified: " + skill.getName() + " at " + SkillLevel.label(level)
                    + ". Your match scores now use this level, and recruiters see it as verified.";
        } else {
            message = late ? "The time ran out before the answers arrived, so this attempt didn't count. Try again after 24 hours."
                    : "Not verified this time (" + correct + " of " + ids.length + "). You can try again after 24 hours.";
        }
        String after = SkillLevel.label(p.levelOf(skill));
        return new ResultDto(skill.getName(), correct, ids.length, level, level == 0 ? null : SkillLevel.label(level),
                before, after, right, message);
    }

    private AttemptDto toDto(AssessmentAttempt a) {
        String[] ids = a.getQuestionIds().split(",");
        String[] orders = a.getOptionOrder().split(";");
        List<QuestionDto> qs = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            QuestionBank.Question q = bank.byId(ids[i]);
            List<String> shown = new ArrayList<>();
            for (char c : orders[i].toCharArray()) shown.add(q.options().get(c - '0'));
            qs.add(new QuestionDto(i, q.question(), shown));
        }
        return new AttemptDto(a.getId(), a.getSkill().getName(), MINUTES, a.getStartedAt(),
                a.getStartedAt().plusMinutes(MINUTES), qs);
    }
}
