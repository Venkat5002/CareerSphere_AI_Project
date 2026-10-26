package com.careersphere.service;

import com.careersphere.TestData;
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

import static org.junit.jupiter.api.Assertions.*;

/** Workshop Planner: batch-level Opportunity Math, exact search over workshop combinations, greedy comparison. */
class WorkshopServiceTest {

    private TestData data;
    private List<Job> jobs;
    private List<StudentProfile> students;

    @BeforeEach
    void setUp() {
        data = new TestData();
        jobs = List.of(
                data.job("Web Developer", null, "HTML", "CSS", "JavaScript"),
                data.job("Backend 1", null, "Java", "Spring Boot", "SQL"),
                data.job("Backend 2", null, "Java", "Spring Boot", "Git"),
                data.job("Analyst", null, "Python", "SQL", "Excel"));
        students = List.of(
                data.student("Asha", 8.0, "Java", "SQL"),              // 67%, 33%: nothing within reach yet
                data.student("Bala", 7.5, "Java", "Git"),              // nothing within reach yet
                data.student("Chitra", 8.5, "HTML", "CSS"),            // 67% web: nothing yet
                data.student("Dev", 9.0, "Python", "SQL", "Excel"));   // analyst already 100%
    }

    private WorkshopService service() {
        JobRepository jobRepo = TestData.repo(JobRepository.class,
                Map.of("findByStatusOrderByCreatedAtDesc", args -> jobs));
        StudentProfileRepository profileRepo = TestData.repo(StudentProfileRepository.class,
                Map.of("findAll", args -> students));
        return new WorkshopService(jobRepo, profileRepo, new MatchService());
    }

    @Test
    @DisplayName("Baseline counts students and matches within reach today")
    void baseline() {
        WorkshopPlan plan = service().plan(1, Goal.REACH);
        assertEquals(4, plan.totalStudents());
        assertEquals(1, plan.studentsWithReachNow(), "Only Dev has a job within reach");
        assertEquals(1, plan.matchesNow());
    }

    @Test
    @DisplayName("Spring Boot comes first: it gives two students their first job within reach")
    void bestFirstWorkshop() {
        WorkshopPlan plan = service().plan(1, Goal.REACH);
        assertEquals(1, plan.plan().size());
        WorkshopService.PlanStep step = plan.plan().get(0);
        assertEquals("Spring Boot", step.skill());
        assertEquals(2, step.firstReach());
        assertEquals(2, step.invited());
        assertEquals(3, step.studentsWithReachAfter());
        List<String> invited = step.invite().stream().map(WorkshopService.StudentGain::name).toList();
        assertTrue(invited.containsAll(List.of("Asha", "Bala")));
    }

    @Test
    @DisplayName("Three workshops reach all four students, with more matches than one-at-a-time")
    void threeWorkshopsReachEveryone() {
        WorkshopPlan plan = service().plan(3, Goal.REACH);
        assertEquals("Spring Boot", plan.plan().get(0).skill(), "Biggest immediate effect goes first");
        assertEquals(4, plan.studentsWithReachAfterPlan(), "All four students end with a job within reach");
        assertEquals(4, plan.oneAtATime().studentsWithReach());
        assertTrue(plan.matchesAfterPlan() >= plan.oneAtATime().matches(), "Exact search is never worse than greedy");
        assertTrue(plan.matchesAfterPlan() > plan.matchesNow());
    }

    @Test
    @DisplayName("Workshops nobody needs are left out of the plan")
    void dropsWorkshopsNobodyNeeds() {
        jobs = List.of(data.job("Web Developer", null, "HTML", "CSS", "JavaScript"));
        students = List.of(data.student("Chitra", 8.5, "HTML", "CSS"));
        WorkshopPlan plan = service().plan(6, Goal.REACH);
        assertEquals(1, plan.plan().size(), "Only JavaScript helps anyone");
        assertEquals("JavaScript", plan.plan().get(0).skill());
        plan.plan().forEach(step -> assertTrue(step.invited() > 0));
    }

    @Test
    @DisplayName("A student who already has the skill is never invited")
    void doesNotInviteStudentsWhoHaveTheSkill() {
        WorkshopPlan plan = service().plan(1, Goal.MATCHES);
        WorkshopService.SkillImpact sql = plan.ranking().stream()
                .filter(k -> k.skill().equals("SQL")).findFirst().orElseThrow();
        sql.invite().forEach(g -> assertNotEquals("Asha", g.name()));
        sql.invite().forEach(g -> assertNotEquals("Dev", g.name()));
    }

    @Test
    @DisplayName("Exact search finds skills that only help together; one-at-a-time misses them")
    void exactSearchFindsComplementarySkills() {
        jobs = List.of(
                data.job("Java Developer", null, "Java", "Spring Boot", "Git"),
                data.job("Analyst", null, "Python", "SQL", "Excel"));
        students = List.of(
                data.student("Esha", 8.0, "Git"),      // needs Java AND Spring Boot: either alone gives 67%
                data.student("Farid", 7.5, "Git"),     // same
                data.student("Gita", 8.0, "Python", "SQL"));  // Excel alone gives 100%
        WorkshopPlan plan = service().plan(2, Goal.REACH);

        assertEquals("EXACT", plan.method());
        assertEquals(15L, plan.combinationsChecked(), "6 candidate skills, choose 2");
        assertEquals(List.of("Java", "Spring Boot"), plan.plan().stream().map(WorkshopService.PlanStep::skill).toList());
        assertEquals(2, plan.studentsWithReachAfterPlan());
        plan.plan().forEach(step -> assertEquals(2, step.invited(), "Esha and Farid attend both"));
        assertEquals(2, plan.plan().get(1).firstReach(), "The payoff comes when the second workshop is done");

        assertEquals(List.of("Excel"), plan.oneAtATime().skills(), "Greedy sees no gain from Java or Spring Boot alone");
        assertEquals(1, plan.oneAtATime().studentsWithReach());
    }
}
