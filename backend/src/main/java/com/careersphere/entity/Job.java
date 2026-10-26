package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The recruiter who posted the job. Only they can edit it or see its applicants. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 150) private String companyName;
    @Column(length = 100) private String location;
    @Column(length = 60) private String salary;
    @Column(length = 30) private String jobType;
    @Column(length = 60) private String experience;
    @Column(length = 4000) private String description;
    private Double minCgpa;
    private LocalDate deadline;
    private Integer openings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Required skills - used for explainable matching. */
    @ManyToMany
    @JoinTable(name = "job_skills",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();

    /** Minimum level per skill id (1 Beginner, 2 Intermediate, 3 Advanced). No entry means Beginner. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_skill_levels", joinColumns = @JoinColumn(name = "job_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "min_level", nullable = false)
    private Map<Long, Integer> minLevels = new HashMap<>();

    /** Skill ids that are nice to have rather than must-have. They count half as much in the score. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_nice_to_have", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "skill_id", nullable = false)
    private Set<Long> niceToHave = new HashSet<>();

    protected Job() {}

    public Job(User recruiter) { this.recruiter = recruiter; }

    public Long getId() { return id; }
    public User getRecruiter() { return recruiter; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { this.companyName = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getSalary() { return salary; }
    public void setSalary(String v) { this.salary = v; }
    public String getJobType() { return jobType; }
    public void setJobType(String v) { this.jobType = v; }
    public String getExperience() { return experience; }
    public void setExperience(String v) { this.experience = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Double getMinCgpa() { return minCgpa; }
    public void setMinCgpa(Double v) { this.minCgpa = v; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate v) { this.deadline = v; }
    public Integer getOpenings() { return openings; }
    public void setOpenings(Integer v) { this.openings = v; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<Skill> getSkills() { return skills; }
    public void setSkills(Set<Skill> v) { this.skills = v; }
    public Map<Long, Integer> getMinLevels() { return minLevels; }
    public Set<Long> getNiceToHave() { return niceToHave; }

    /** Minimum level this job asks for in a skill (1-3). */
    public int minLevelOf(Skill skill) { return SkillLevel.clamp(minLevels.get(skill.getId())); }

    /** True when the skill is nice to have; must-have otherwise. */
    public boolean isNiceToHave(Skill skill) { return niceToHave.contains(skill.getId()); }

    /** Replaces the level and must-have settings; entries for skills not on the job are dropped. */
    public void setRequirements(Map<Long, Integer> levels, Set<Long> nice) {
        Set<Long> ids = new HashSet<>();
        for (Skill s : skills) ids.add(s.getId());
        minLevels.clear();
        niceToHave.clear();
        if (levels != null) levels.forEach((id, lvl) -> { if (ids.contains(id) && SkillLevel.clamp(lvl) > 1) minLevels.put(id, SkillLevel.clamp(lvl)); });
        if (nice != null) for (Long id : nice) if (ids.contains(id)) niceToHave.add(id);
    }
}
