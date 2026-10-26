package com.careersphere.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Multiple-choice questions per skill, from src/main/resources/assessments/questions.json.
 * Each skill has 8 questions: 4 basic (level 1) and 4 advanced (level 2).
 * In the file the first option is always the correct one; options are shuffled for every attempt.
 */
public class QuestionBank {

    public record Question(String id, int level, String question, List<String> options) {}

    private final Map<String, String> names = new LinkedHashMap<>();          // lower-case -> display name
    private final Map<String, List<Question>> bySkill = new HashMap<>();      // lower-case -> questions
    private final Map<String, Question> byId = new HashMap<>();

    public QuestionBank(Map<String, List<Question>> questions) {
        questions.forEach((skill, list) -> {
            names.put(skill.toLowerCase(), skill);
            bySkill.put(skill.toLowerCase(), List.copyOf(list));
            for (Question q : list) byId.put(q.id(), q);
        });
    }

    public static QuestionBank load(ObjectMapper mapper) {
        try (InputStream in = QuestionBank.class.getResourceAsStream("/assessments/questions.json")) {
            if (in == null) return new QuestionBank(Map.of());
            JsonNode root = mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, List<Question>> out = new LinkedHashMap<>();
            for (JsonNode skill : root) {
                List<Question> list = new ArrayList<>();
                for (JsonNode q : skill.path("questions")) {
                    List<String> options = new ArrayList<>();
                    for (JsonNode o : q.path("options")) options.add(o.asText());
                    list.add(new Question(q.path("id").asText(), q.path("level").asInt(1), q.path("question").asText(), options));
                }
                out.put(skill.path("skill").asText(), list);
            }
            return new QuestionBank(out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the assessment questions", e);
        }
    }

    public List<String> skills() { return List.copyOf(names.values()); }
    public boolean has(String skill) { return skill != null && bySkill.containsKey(skill.toLowerCase()); }
    public List<Question> forSkill(String skill) { return bySkill.getOrDefault(skill.toLowerCase(), List.of()); }
    public Question byId(String id) { return byId.get(id); }
}
