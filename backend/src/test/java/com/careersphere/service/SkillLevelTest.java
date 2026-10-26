package com.careersphere.service;

import com.careersphere.TestData;
import com.careersphere.dto.DashboardDtos.OpportunityDto;
import com.careersphere.dto.JobDtos.MatchDto;
import com.careersphere.entity.Job;
import com.careersphere.entity.StudentProfile;
import com.careersphere.repository.JobRepository;
import com.careersphere.repository.StudentProfileRepository;
import com.careersphere.service.WorkshopService.Goal;
import com.careersphere.service.WorkshopService.WorkshopPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Skill levels (Beginner / Intermediate / Advanced) and must-have vs nice-to-have skills. */
class SkillLevelTest {

    private TestData data;
    private MatchService match;

    @BeforeEach
    void setUp() {
        data = new TestData();
        match = new MatchService();
    }

    /** Sets a job's minimum levels and nice-to-have skills by name. */
    private void require(Job job, Map<String, Integer> levels, Set<String> nice) {
        Map<Long, Integer> byId = new java.util.HashMap<>();
        levels.forEach((name, lvl) -> byId.put(data.skill(name).getId(), lvl));
        Set<Long> niceIds = new java.util.HashSet<>();
        for (String n : nice) niceIds.add(data.skill(n).getId());
        job.setRequirements(byId, niceIds);
    }

    private void level(StudentProfile p, String skill, int lvl) {
        p.setLevel(data.skill(skill), lvl);
    }

    @Test
    @DisplayName("With the defaults (all Beginner, all must-have) the score is the original share of skills")
    void defaultsMatchOriginalRule() {
        Job job = data.job("Backend", null, "Java", "Spring Boot", "SQL", "Git");
        StudentProfile p = data.student("Asha", 8.0, "Java", "SQL", "Git");
        level(p, "Java", 3);                              // a higher level than asked changes nothing
        assertEquals(75, match.match(p, job).score());
        assertEquals(75, match.match(data.skills("Java", "SQL", "Git"), 8.0, job).score());
    }

    @Test
    @DisplayName("A skill below the level asked earns half credit and is listed as a level gap")
    void belowLevelEarnsHalf() {
        Job job = data.job("Backend", null, "Java", "SQL", "Git");
        require(job, Map.of("Java", 2), Set.of());
        StudentProfile p = data.student("Bala", 8.0, "Java", "SQL", "Git");   // Java at Beginner
        MatchDto m = match.match(p, job);
        assertEquals(83, m.score(), "(2 + 4 + 4) / 12 half-units = 83%");
        assertEquals(1, m.levelGaps().size());
        assertEquals("Java", m.levelGaps().get(0).skill());
        assertEquals("Beginner", m.levelGaps().get(0).have());
        assertEquals("Intermediate", m.levelGaps().get(0).need());
        assertTrue(m.explanation().contains("Below the level asked"));

        level(p, "Java", 2);
        MatchDto after = match.match(p, job);
        assertEquals(100, after.score());
        assertTrue(after.levelGaps().isEmpty());
    }

    @Test
    @DisplayName("A nice-to-have skill counts half as much as a must-have")
    void niceToHaveCountsHalf() {
        Job job = data.job("Backend", null, "Java", "Spring Boot", "Docker");
        require(job, Map.of(), Set.of("Docker"));
        assertEquals(80, match.match(data.skills("Java", "Spring Boot"), 8.0, job).score(), "8 of 10 half-units");
        MatchDto m = match.match(data.skills("Java", "Docker"), 8.0, job);
        assertEquals(60, m.score(), "6 of 10 half-units");
        assertEquals(List.of("Docker"), m.niceToHave());
        assertTrue(m.explanation().contains("Spring Boot"));
    }

    @Test
    @DisplayName("Opportunity Math suggests levelling up a skill when that is what brings a job within reach")
    void opportunityMathLevelsUp() {
        Job job = data.job("Senior Backend", null, "Java", "Spring Boot", "Git");
        require(job, Map.of("Java", 3, "Spring Boot", 3), Set.of());
        StudentProfile p = data.student("Chitra", 8.0, "Java", "Spring Boot", "Git");   // 8/12 = 67%
        assertFalse(match.reachable(p, job));

        List<OpportunityDto> ops = match.opportunities(p, List.of(job), 5);
        OpportunityDto top = ops.get(0);
        assertEquals("Java", top.skill());
        assertEquals(1, top.jobsUnlocked());
        assertEquals(1, top.currentLevel());
        assertEquals(3, top.targetLevel(), "Intermediate is not enough; Advanced brings it to 83%");
    }

    @Test
    @DisplayName("A workshop brings attendees to Intermediate, so a Beginner can still gain from it")
    void workshopRaisesToIntermediate() {
        Job job = data.job("Backend", null, "Java", "Spring Boot", "Git");
        require(job, Map.of("Java", 2, "Spring Boot", 2), Set.of());
        StudentProfile beginner = data.student("Dev", 8.0, "Java", "Spring Boot", "Git");   // 67%
        StudentProfile already = data.student("Esha", 8.0, "Java", "Git");                  // Java at Intermediate, no Spring Boot: 67%
        level(already, "Java", 2);
        List<Job> jobs = List.of(job);
        List<StudentProfile> students = List.of(beginner, already);
        WorkshopService service = new WorkshopService(
                TestData.repo(JobRepository.class, Map.of("findByStatusOrderByCreatedAtDesc", a -> jobs)),
                TestData.repo(StudentProfileRepository.class, Map.of("findAll", a -> students)),
                new MatchService());

        WorkshopPlan plan = service.plan(1, Goal.REACH);
        assertEquals(0, plan.studentsWithReachNow());
        assertEquals("Spring Boot", plan.plan().get(0).skill());
        assertEquals(2, plan.studentsWithReachAfterPlan(),
                "Dev goes from Beginner to Intermediate (83%), Esha learns it (100%)");
        assertEquals(2, plan.plan().get(0).invited());
    }

    @Test
    @DisplayName("What-if accepts 'Skill' (learn it) and 'Skill:2' (reach Intermediate)")
    void whatIfParsesLevels() {
        assertEquals(new DashboardService.SimulatedSkill("Java", 2), DashboardService.SimulatedSkill.parse(" Java:2 "));
        assertEquals(new DashboardService.SimulatedSkill("C++", 1), DashboardService.SimulatedSkill.parse("C++"));
        assertEquals(new DashboardService.SimulatedSkill("Node.js", 3), DashboardService.SimulatedSkill.parse("Node.js:3"));
        assertNull(DashboardService.SimulatedSkill.parse("  "));
    }
}
