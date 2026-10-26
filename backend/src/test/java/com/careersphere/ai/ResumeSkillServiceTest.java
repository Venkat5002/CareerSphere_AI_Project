package com.careersphere.ai;

import com.careersphere.dto.StudentDtos.ResumeSkillsResponse;
import com.careersphere.dto.StudentDtos.SkillSuggestion;
import com.careersphere.entity.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Offline (keyword) resume skill extraction. The Gemini path is covered by its evidence check in the service. */
class ResumeSkillServiceTest {

    private static final List<Skill> CATALOG = List.of(
            new Skill("Java", "Programming"), new Skill("JavaScript", "Programming"), new Skill("Python", "Programming"),
            new Skill("C", "Programming"), new Skill("Spring Boot", "Backend"), new Skill("REST APIs", "Backend"),
            new Skill("Node.js", "Backend"), new Skill("React", "Frontend"), new Skill("MySQL", "Database"),
            new Skill("SQL", "Database"), new Skill("Git", "Tools"), new Skill("Communication", "Soft Skills"));

    private final ResumeSkillService service = new ResumeSkillService(null, null, null, null, null, null);

    private List<String> found(String text) {
        return service.offline(text, CATALOG, Set.of()).suggestions().stream().map(SkillSuggestion::skill).toList();
    }

    @Test
    @DisplayName("Finds skills written inside project descriptions, with that line as evidence")
    void findsSkillsInProjects() {
        String text = "Projects\n- Built RESTful APIs with Spring Boot for issuing books.\n- Stored data in MySQL.";
        ResumeSkillsResponse r = service.offline(text, CATALOG, Set.of());
        assertEquals("offline", r.mode());
        SkillSuggestion spring = r.suggestions().stream().filter(s -> s.skill().equals("Spring Boot")).findFirst().orElseThrow();
        assertEquals("Built RESTful APIs with Spring Boot for issuing books.", spring.evidence(), "Bullet mark is removed");
        assertTrue(found(text).containsAll(List.of("Spring Boot", "REST APIs", "MySQL")));
    }

    @Test
    @DisplayName("Recognises common spellings: springboot, spring-boot, Node.js")
    void recognisesSpellings() {
        assertTrue(found("Worked with springboot").contains("Spring Boot"));
        assertTrue(found("Worked with Spring-Boot").contains("Spring Boot"));
        assertTrue(found("Backend in Node.js").contains("Node.js"));
    }

    @Test
    @DisplayName("JavaScript does not count as Java")
    void javascriptIsNotJava() {
        List<String> skills = found("Languages: JavaScript");
        assertTrue(skills.contains("JavaScript"));
        assertFalse(skills.contains("Java"));
    }

    @Test
    @DisplayName("Ordinary words are not mistaken for skills")
    void avoidsFalsePositives() {
        List<String> skills = found("Grade: C in Physics. I react quickly to change. Each node in a tree.");
        assertFalse(skills.contains("React"), "lower-case 'react' is a verb");
        assertFalse(skills.contains("Node.js"));
        assertFalse(skills.contains("C"), "a grade is not the C language");
    }

    @Test
    @DisplayName("Skills already in the profile are marked and listed after new ones")
    void marksKnownSkills() {
        ResumeSkillsResponse r = service.offline("Java, Git and React", CATALOG, Set.of("java"));
        SkillSuggestion java = r.suggestions().stream().filter(s -> s.skill().equals("Java")).findFirst().orElseThrow();
        assertTrue(java.alreadyInProfile());
        assertFalse(r.suggestions().get(0).alreadyInProfile(), "New skills come first");
    }

    @Test
    @DisplayName("Long evidence lines are trimmed around the match")
    void trimsLongLines() {
        String line = "x".repeat(200) + " Spring Boot " + "y".repeat(200);
        String evidence = ResumeSkillService.lineAround(line, 201);
        assertTrue(evidence.length() <= 116);
        assertTrue(evidence.contains("Spring Boot"));
    }
}
