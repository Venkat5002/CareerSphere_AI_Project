package com.careersphere;

import com.careersphere.entity.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;

/**
 * Builds entities for unit tests without a database.
 * Ids are normally set by the database, so tests set them through reflection.
 */
public class TestData {

    private long nextId = 1;
    private final Map<String, Skill> skills = new HashMap<>();

    public Skill skill(String name) {
        return skills.computeIfAbsent(name, n -> withId(new Skill(n, "Test")));
    }

    public Set<Skill> skills(String... names) {
        Set<Skill> set = new HashSet<>();
        for (String n : names) set.add(skill(n));
        return set;
    }

    public Job job(String title, Double minCgpa, String... skillNames) {
        Job j = withId(new Job(withId(new User("Recruiter", "r" + nextId + "@test", "x", Role.RECRUITER))));
        j.setTitle(title);
        j.setCompanyName("Test Co");
        j.setMinCgpa(minCgpa);
        j.setSkills(skills(skillNames));
        return j;
    }

    public StudentProfile student(String name, Double cgpa, String... skillNames) {
        User u = withId(new User(name, name.toLowerCase().replace(' ', '.') + "@test", "x", Role.STUDENT));
        StudentProfile p = withId(new StudentProfile(u));
        p.setCgpa(cgpa);
        p.setBranch("CSE");
        p.setSkills(skills(skillNames));
        return p;
    }

    public <T> T withId(T entity) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, nextId++);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A repository stand-in: only the named methods are answered, anything else fails the test.
     * Plain Java instead of Mockito, so these tests also run without any test libraries.
     */
    @SuppressWarnings("unchecked")
    public static <T> T repo(Class<T> type, Map<String, Function<Object[], Object>> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Function<Object[], Object> answer = answers.get(method.getName());
            if (answer == null) throw new UnsupportedOperationException("Not stubbed: " + method.getName());
            return answer.apply(args);
        });
    }
}
