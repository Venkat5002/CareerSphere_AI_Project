package com.careersphere.service;

import com.careersphere.TestData;
import com.careersphere.dto.ReadinessDtos.AttemptDto;
import com.careersphere.dto.ReadinessDtos.ResultDto;
import com.careersphere.dto.ReadinessDtos.ScoreItem;
import com.careersphere.entity.AssessmentAttempt;
import com.careersphere.entity.Skill;
import com.careersphere.entity.StudentProfile;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Résumé score, placement steps and skill assessments. */
class PlacementProfileTest {

    private TestData data;
    private StudentProfile asha;
    private boolean hasResume;
    private final List<AssessmentAttempt> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        data = new TestData();
        asha = data.student("Asha Rao", 8.0, "Java", "SQL");
        hasResume = true;
        asha.setPlacementConsent(true);
        saved.clear();
    }

    // ------------------------------------------------------------------ résumé score

    @Test
    @DisplayName("Résumé score adds up fixed points and never passes 100")
    void resumeScoreRules() {
        List<ScoreItem> empty = ReadinessService.score(asha, false, 0);
        assertEquals(8, ReadinessService.total(empty), "Only the 2 skills count: 2 x 4 points");
        assertEquals(100, empty.stream().mapToInt(ScoreItem::max).sum());

        asha.setAbout("I build REST services in Java and Spring Boot. Final-year CSE student; built a library system, a chat app "
                + "and a placement portal as course projects, and I want a backend developer role.");
        asha.setGithubUrl("https://github.com/asha");
        asha.setLinkedinUrl("https://linkedin.com/in/asha");
        asha.setTenthPercentage(91.0);
        asha.setTwelfthPercentage(88.0);
        asha.setSkills(data.skills("Java", "SQL", "Git", "Python", "React"));
        for (Skill s : asha.getSkills()) asha.setLevel(s, 2);
        for (Skill s : asha.getSkills()) asha.verify(s, 2);
        asha.setVideo("v.webm", "video/webm", 10L);
        List<ScoreItem> full = ReadinessService.score(asha, true, 2);
        assertEquals(100, ReadinessService.total(full));
        assertTrue(full.stream().allMatch(i -> i.tip() == null), "No tips once every item is complete");
    }

    @Test
    @DisplayName("Placement steps run in order: résumé, consent, assessment, video")
    void nextStepOrder() {
        assertEquals("RESUME", ReadinessService.nextStep(false, true, 3, true));
        assertEquals("CONSENT", ReadinessService.nextStep(true, false, 0, false));
        assertEquals("ASSESSMENT", ReadinessService.nextStep(true, true, 0, false));
        assertEquals("VIDEO", ReadinessService.nextStep(true, true, 1, false));
        assertEquals("DONE", ReadinessService.nextStep(true, true, 1, true));
    }

    @Test
    @DisplayName("Other students on the leaderboard are shown by first name and initial")
    void leaderboardNames() {
        assertEquals("Ananya R.", ReadinessService.shortName("Ananya Reddy"));
        assertEquals("V. R.", ReadinessService.shortName("V. Venkata Rupin"));
        assertEquals("Zaid", ReadinessService.shortName("Zaid"));
    }

    // ------------------------------------------------------------------ skill assessment

    private AssessmentService service() {
        Map<String, List<QuestionBank.Question>> q = new HashMap<>();
        List<QuestionBank.Question> java = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            java.add(new QuestionBank.Question("java-" + i, i <= 4 ? 1 : 2, "Question " + i,
                    List.of("right " + i, "wrong a", "wrong b", "wrong c")));
        }
        q.put("Java", java);
        QuestionBank bank = new QuestionBank(q);

        StudentProfileRepository profileRepo = TestData.repo(StudentProfileRepository.class,
                Map.of("findByUserId", a -> Optional.of(asha)));
        StudentProfileService profiles = new StudentProfileService(profileRepo,
                TestData.repo(UserRepository.class, Map.of()), TestData.repo(ResumeRepository.class, Map.of()),
                new SkillService(TestData.repo(SkillRepository.class, Map.of())));
        ResumeRepository resumes = TestData.repo(ResumeRepository.class, Map.of("existsByStudentId", a -> hasResume));
        AssessmentAttemptRepository attempts = TestData.repo(AssessmentAttemptRepository.class, Map.of(
                "save", a -> { AssessmentAttempt at = (AssessmentAttempt) a[0]; if (at.getId() == null) data.withId(at); saved.add(0, at); return at; },
                "findByStudentIdOrderByStartedAtDesc", a -> List.copyOf(saved),
                "findById", a -> saved.stream().filter(x -> x.getId().equals(a[0])).findFirst()));
        UserRepository users = TestData.repo(UserRepository.class, Map.of("findById", a -> Optional.of(asha.getUser())));
        SkillService skills = new SkillService(TestData.repo(SkillRepository.class,
                Map.of("findByNameIgnoreCase", a -> Optional.of(data.skill("Java")))));
        return new AssessmentService(profiles, resumes, attempts, users, skills, bank, new Random(7));
    }

    /** The shown option index of the correct answer for each question of the attempt. */
    private List<Integer> rightAnswers(AssessmentAttempt a) {
        List<Integer> out = new ArrayList<>();
        for (String order : a.getOptionOrder().split(";")) out.add(order.indexOf('0'));
        return out;
    }

    @Test
    @DisplayName("Correct answers decide the verified level: 8 Advanced, 6-7 Intermediate, 4-5 Beginner")
    void levelRule() {
        assertEquals(3, AssessmentService.levelFor(8));
        assertEquals(2, AssessmentService.levelFor(7));
        assertEquals(2, AssessmentService.levelFor(6));
        assertEquals(1, AssessmentService.levelFor(4));
        assertEquals(0, AssessmentService.levelFor(3));
    }

    @Test
    @DisplayName("The assessment stays locked until there is a résumé and placement consent")
    void lockedUntilConsent() {
        asha.setPlacementConsent(false);
        ApiException e = assertThrows(ApiException.class, () -> service().start(asha.getUser().getId(), "Java"));
        assertTrue(e.getMessage().contains("consent"));
        hasResume = false;
        ApiException e2 = assertThrows(ApiException.class, () -> service().start(asha.getUser().getId(), "Java"));
        assertTrue(e2.getMessage().contains("résumé"));
    }

    @Test
    @DisplayName("The test sends shuffled options but never the answers; grading happens on the server")
    void gradedOnServer() {
        AssessmentService svc = service();
        AttemptDto attempt = svc.start(asha.getUser().getId(), "Java");
        assertEquals(8, attempt.questions().size());
        assertEquals(4, attempt.questions().get(0).options().size());

        List<Integer> answers = new ArrayList<>(rightAnswers(saved.get(0)));
        answers.set(0, (answers.get(0) + 1) % 4);       // one wrong answer: 7 of 8
        ResultDto r = svc.submit(asha.getUser().getId(), attempt.attemptId(), answers);
        assertEquals(7, r.correct());
        assertEquals(2, r.verifiedLevel());
        assertEquals(List.of(false, true, true, true, true, true, true, true), r.correctByQuestion());
        assertEquals(2, asha.levelOf(data.skill("Java")), "The profile level becomes the verified level");
        assertEquals(2, asha.verifiedLevelOf(data.skill("Java")));
        assertThrows(ApiException.class, () -> svc.submit(asha.getUser().getId(), attempt.attemptId(), answers),
                "An attempt can be submitted only once");
    }

    @Test
    @DisplayName("Fewer than 4 correct verifies nothing and blocks a retake for 24 hours")
    void failAndWait() {
        AssessmentService svc = service();
        AttemptDto attempt = svc.start(asha.getUser().getId(), "Java");
        List<Integer> wrong = new ArrayList<>();
        for (int right : rightAnswers(saved.get(0))) wrong.add((right + 1) % 4);
        ResultDto r = svc.submit(asha.getUser().getId(), attempt.attemptId(), wrong);
        assertEquals(0, r.correct());
        assertEquals(0, r.verifiedLevel());
        assertEquals(0, asha.verifiedLevelOf(data.skill("Java")));
        assertThrows(ApiException.class, () -> svc.start(asha.getUser().getId(), "Java"));
    }

    @Test
    @DisplayName("Starting again while a test is running returns the same questions")
    void resumeRunningAttempt() {
        AssessmentService svc = service();
        AttemptDto first = svc.start(asha.getUser().getId(), "Java");
        AttemptDto again = svc.start(asha.getUser().getId(), "Java");
        assertEquals(first.attemptId(), again.attemptId());
        assertEquals(first.questions().get(3).question(), again.questions().get(3).question());
    }
}
