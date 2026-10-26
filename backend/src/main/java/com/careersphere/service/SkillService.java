package com.careersphere.service;

import com.careersphere.entity.Skill;
import com.careersphere.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SkillService {

    private final SkillRepository skills;

    public SkillService(SkillRepository skills) {
        this.skills = skills;
    }

    @Transactional(readOnly = true)
    public List<Skill> all() {
        return skills.findAllByOrderByCategoryAscNameAsc();
    }

    /**
     * Turns names typed by the user into catalog skills (case-insensitive),
     * so "java" and "Java" are the same skill. Unknown names are added under "Other".
     */
    @Transactional
    public Set<Skill> resolve(List<String> names) {
        Set<Skill> result = new LinkedHashSet<>();
        if (names == null) return result;
        for (String raw : names) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.trim().replaceAll("\\s+", " ");
            Skill skill = skills.findByNameIgnoreCase(name)
                    .orElseGet(() -> skills.save(new Skill(name, "Other")));
            result.add(skill);
        }
        return result;
    }

    public static List<String> names(Collection<Skill> set) {
        return set.stream().map(Skill::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
