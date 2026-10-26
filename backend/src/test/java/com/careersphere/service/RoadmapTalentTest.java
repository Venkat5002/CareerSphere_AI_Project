package com.careersphere.service;

import com.careersphere.TestData;
import com.careersphere.entity.Job;
import com.careersphere.entity.StudentProfile;
import com.careersphere.repository.ApplicationRepository;
import com.careersphere.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Personal Skill Roadmap and Hidden Talent. */
class RoadmapTalentTest {

    private TestData data;
    private MatchService match;
    private RoadmapService roadmap;

    @BeforeEach
    void setUp() {
        data = new TestData();
        match = new MatchService();
        roadmap = new RoadmapService(null, null, match);
    }

    private void require(Job job, Map<String, Integer> levels) {
        Map<Long, Integer> byId = new HashMap<>();
        levels.forEach((n, l) -> byId.put(data.skill(n).getId(), l));
        job.setRequirements(byId, Set.of());
    }

    @Test
    @DisplayName("Roadmap finds a 2-step bundle when no single skill helps, and marks it as working together")
    void synergy() {
        Job job = data.job("Backend", null, "Java", "Spring Boot", "Git");
        StudentProfile p = data.student("Bala", 8.0, "Git");
        RoadmapService.RoadmapDto r = roadmap.plan(p.levels(), p.getCgpa(), List.of(job));
        assertEquals(0, r.jobsWithinReachNow());
        assertEquals(0, r.bestSingleSkillGain(), "Java alone or Spring Boot alone is 2 of 3 = 67%");
        assertEquals(1, r.bundles().size());
        RoadmapService.BundleDto b = r.bundles().get(0);
        assertEquals(2, b.size());
        assertEquals(1, b.newJobs());
        assertTrue(b.synergy());
        assertEquals(Set.of("Java", "Spring Boot"), Set.of(b.steps().get(0).skill(), b.steps().get(1).skill()));
    }

    @Test
    @DisplayName("Roadmap prefers the bundle with less effort when two unlock the same jobs")
    void leastEffort() {
        Job a = data.job("A", null, "Java", "SQL", "Git", "Docker");
        require(a, Map.of("Docker", 3));
        StudentProfile p = data.student("Chitra", 8.0, "Java", "SQL");
        RoadmapService.RoadmapDto r = roadmap.plan(p.levels(), p.getCgpa(), List.of(a));
        // Git (effort 1) gives 3 of 4 must-haves = 75%; Docker to Advanced costs 3.
        assertEquals("Learn Git", r.bundles().get(0).steps().get(0).action());
        assertEquals(1, r.bundles().get(0).effort());
    }

    @Test
    @DisplayName("Path to a job: the cheapest steps for each nearby job, and CGPA named as the blocker when it is")
    void pathToJob() {
        Job levelJob = data.job("Level", null, "Java", "Spring Boot", "Git", "SQL");
        require(levelJob, Map.of("Java", 3, "Spring Boot", 3));
        Job cgpaJob = data.job("Strict", 9.0, "Java", "Git");
        StudentProfile p = data.student("Dev", 8.0, "Java", "Spring Boot", "Git");
        // levelJob today: Java and Spring Boot below Advanced (half credit), Git full, SQL missing = (1 + 1 + 2 + 0) / 8 = 50%.
        RoadmapService.RoadmapDto r = roadmap.plan(p.levels(), p.getCgpa(), List.of(levelJob, cgpaJob));
        RoadmapService.PathDto path = r.closestJobs().stream().filter(x -> x.job().equals("Level")).findFirst().orElseThrow();
        assertNull(path.blocker());
        assertTrue(match.reachable(RoadmapService.apply(p.levels(), RoadmapService.candidateActions(p.levels(), List.of(levelJob))), 8.0, levelJob));
        assertFalse(path.actions().isEmpty());
        RoadmapService.PathDto strict = r.closestJobs().stream().filter(x -> x.job().equals("Strict")).findFirst().orElseThrow();
        assertTrue(strict.blocker().contains("CGPA"));
    }

    @Test
    @DisplayName("Hidden Talent shows only consented students who haven't applied, within reach or one step away")
    void hiddenTalent() {
        Job job = data.job("Backend", null, "Java", "Spring Boot", "SQL", "Git");
        StudentProfile fit = data.student("Asha", 8.0, "Java", "Spring Boot", "SQL");      // 75% within reach
        StudentProfile near = data.student("Bala", 8.0, "Java", "SQL");                    // 50%, Spring Boot or Git -> 75%
        StudentProfile far = data.student("Chitra", 8.0, "Python");                         // 0%
        StudentProfile private_ = data.student("Dev", 8.0, "Java", "Spring Boot", "SQL", "Git");
        StudentProfile applied = data.student("Esha", 8.0, "Java", "Spring Boot", "SQL", "Git");
        for (StudentProfile s : List.of(fit, near, far, applied)) s.setPlacementConsent(true);
        Long appliedId = applied.getUser().getId();
        TalentService talent = new TalentService(null, null,
                TestData.repo(ApplicationRepository.class, Map.of("existsByJobIdAndStudentId", a -> a[1].equals(appliedId))),
                TestData.repo(InvitationRepository.class, Map.of("existsByJobIdAndStudentId", a -> false)),
                match, null);
        TalentService.TalentDto t = talent.find(job, List.of(fit, near, far, private_, applied));
        assertEquals(4, t.consentedStudents());
        assertEquals(List.of(fit.getUser().getFullName()), t.withinReach().stream().map(TalentService.Candidate::name).toList(),
                "Dev fits but gave no consent; Esha fits but already applied");
        assertEquals(1, t.oneStepAway().size());
        assertEquals(near.getUser().getFullName(), t.oneStepAway().get(0).name());
        assertTrue(t.oneStepAway().get(0).stepNeeded().startsWith("Learn "));
    }
}
