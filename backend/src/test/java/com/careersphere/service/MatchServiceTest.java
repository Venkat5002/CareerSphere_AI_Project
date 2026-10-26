package com.careersphere.service;

import com.careersphere.TestData;
import com.careersphere.dto.DashboardDtos.OpportunityDto;
import com.careersphere.dto.JobDtos.MatchDto;
import com.careersphere.entity.Job;
import com.careersphere.entity.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Explainable matching and Opportunity Math (the core of the project). */
class MatchServiceTest {

    private TestData data;
    private MatchService match;

    @BeforeEach
    void setUp() {
        data = new TestData();
        match = new MatchService();
    }

    @Test
    @DisplayName("Score is the share of required skills the student has (3 of 5 = 60%)")
    void scoreIsShareOfRequiredSkills() {
        Job job = data.job("Java Backend Developer", null, "Java", "Spring Boot", "SQL", "Git", "REST APIs");
        MatchDto m = match.match(data.skills("Java", "SQL", "Git", "Python"), 8.0, job);
        assertEquals(60, m.score());
        assertEquals(List.of("Git", "Java", "SQL"), m.matchedSkills());
        assertEquals(List.of("REST APIs", "Spring Boot"), m.missingSkills());
        assertTrue(m.explanation().contains("3 of 5"));
    }

    @Test
    @DisplayName("Scores are rounded to the nearest whole percent (2 of 3 = 67%)")
    void scoreIsRounded() {
        Job job = data.job("Intern", null, "Java", "Spring Boot", "Git");
        assertEquals(67, match.match(data.skills("Java", "Git"), null, job).score());
    }

    @Test
    @DisplayName("Having every required skill gives 100% and says so")
    void allSkillsGive100() {
        Job job = data.job("Web Developer", null, "HTML", "CSS");
        MatchDto m = match.match(data.skills("HTML", "CSS", "Java"), 7.0, job);
        assertEquals(100, m.score());
        assertTrue(m.missingSkills().isEmpty());
        assertEquals("You have all 2 required skills.", m.explanation());
    }

    @Test
    @DisplayName("CGPA below the cutoff is reported separately and blocks 'within reach'")
    void cgpaCutoff() {
        Job job = data.job("ML Intern", 8.5, "Python", "SQL");
        Set<Skill> both = data.skills("Python", "SQL");
        MatchDto m = match.match(both, 8.2, job);
        assertEquals(100, m.score(), "CGPA must not change the skill score");
        assertFalse(m.cgpaEligible());
        assertTrue(m.explanation().contains("below the minimum of 8.5"));
        assertFalse(match.reachable(both, 8.2, job));
        assertTrue(match.reachable(both, 8.5, job), "A CGPA equal to the cutoff is eligible");
    }

    @Test
    @DisplayName("A missing CGPA asks the student to add it when the job has a cutoff")
    void missingCgpa() {
        Job job = data.job("Trainee", 6.5, "Java");
        MatchDto m = match.match(data.skills("Java"), null, job);
        assertFalse(m.cgpaEligible());
        assertTrue(m.explanation().contains("Add your CGPA"));
    }

    @Test
    @DisplayName("The 70% threshold is inclusive (7 of 10 is within reach, 6 of 10 is not)")
    void thresholdIsInclusive() {
        String[] ten = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};
        Job job = data.job("Ten skills", null, ten);
        assertTrue(match.reachable(data.skills("A", "B", "C", "D", "E", "F", "G"), null, job));
        assertFalse(match.reachable(data.skills("A", "B", "C", "D", "E", "F"), null, job));
    }

    @Test
    @DisplayName("Opportunity Math ranks the skill that brings the most jobs within reach first")
    void opportunityRanking() {
        // Student has Java, SQL, Git. Spring Boot pushes three jobs over 70%; Docker only one.
        List<Job> open = List.of(
                data.job("Intern", null, "Java", "Spring Boot", "Git"),               // 67% -> 100%
                data.job("Backend", null, "Java", "Spring Boot", "SQL", "Git"),       // 75% already
                data.job("Payments", null, "Java", "Spring Boot", "SQL", "Docker"),   // 50% -> 75% with Spring Boot
                data.job("Full stack", null, "Java", "SQL", "Spring Boot", "React"),  // 50% -> 75%
                data.job("DevOps", null, "Docker", "Git", "Linux"));                  // 33% -> 67%: not enough
        List<OpportunityDto> ranking = match.opportunities(data.skills("Java", "SQL", "Git"), 8.0, open, 10);

        OpportunityDto top = ranking.get(0);
        assertEquals("Spring Boot", top.skill());
        assertEquals(3, top.jobsUnlocked());
        assertEquals(4, top.jobsRequiring());
        OpportunityDto docker = ranking.stream().filter(o -> o.skill().equals("Docker")).findFirst().orElseThrow();
        assertEquals(1, docker.jobsUnlocked());
    }

    @Test
    @DisplayName("Opportunity Math does not count jobs the student's CGPA rules out")
    void opportunityRespectsCgpa() {
        List<Job> open = List.of(data.job("Strict", 9.0, "Java", "Spring Boot"));
        OpportunityDto o = match.opportunities(data.skills("Java"), 8.0, open, 5).get(0);
        assertEquals("Spring Boot", o.skill());
        assertEquals(0, o.jobsUnlocked());
        assertEquals(1, o.jobsRequiring());
    }

    @Test
    @DisplayName("Skills the student already has are never suggested")
    void noSuggestionsForKnownSkills() {
        List<Job> open = List.of(data.job("Web", null, "HTML", "CSS"));
        assertTrue(match.opportunities(data.skills("HTML", "CSS"), null, open, 5).isEmpty());
    }
}
