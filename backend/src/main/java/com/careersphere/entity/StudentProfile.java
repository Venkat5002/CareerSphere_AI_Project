package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Exactly one profile per student user. */
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 20) private String phone;
    @Column(length = 150) private String college;
    @Column(length = 100) private String branch;
    private Integer graduationYear;
    private Double cgpa;
    private Double tenthPercentage;
    private Double twelfthPercentage;
    @Column(length = 2000) private String about;
    @Column(length = 255) private String linkedinUrl;
    @Column(length = 255) private String githubUrl;

    @ManyToMany
    @JoinTable(name = "student_skills",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();

    /** Level per skill id (1 Beginner, 2 Intermediate, 3 Advanced). A skill with no entry is Beginner. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "student_skill_levels", joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "skill_level", nullable = false)
    private Map<Long, Integer> skillLevels = new HashMap<>();

    /** Skill id to the level a skill assessment or a verified certificate proved. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "student_verified_skills", joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "skill_id")
    @Column(name = "verified_level", nullable = false)
    private Map<Long, Integer> verifiedLevels = new HashMap<>();

    /** Role the student is aiming for, shown under their name ("Java Backend Developer"). */
    @Column(length = 80) private String headline;

    /** The student agreed to share their profile with recruiters. Needed to apply. */
    @Column(nullable = false) private boolean placementConsent = false;
    private LocalDateTime consentAt;

    /** One-minute introduction video, stored on disk like the resume. */
    @Column(length = 80) private String videoStoredName;
    @Column(length = 60) private String videoContentType;
    private Long videoSize;
    private LocalDateTime videoUploadedAt;

    protected StudentProfile() {}

    public StudentProfile(User user) { this.user = user; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
    public Double getCgpa() { return cgpa; }
    public void setCgpa(Double cgpa) { this.cgpa = cgpa; }
    public Double getTenthPercentage() { return tenthPercentage; }
    public void setTenthPercentage(Double tenthPercentage) { this.tenthPercentage = tenthPercentage; }
    public Double getTwelfthPercentage() { return twelfthPercentage; }
    public void setTwelfthPercentage(Double twelfthPercentage) { this.twelfthPercentage = twelfthPercentage; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public Set<Skill> getSkills() { return skills; }
    public void setSkills(Set<Skill> skills) { this.skills = skills; }
    public Map<Long, Integer> getSkillLevels() { return skillLevels; }

    /** The student's level for a skill: 0 if they don't have it, else 1-3. */
    public int levelOf(Skill skill) {
        for (Skill s : skills) {
            if (s.getId() != null && s.getId().equals(skill.getId())) return SkillLevel.clamp(skillLevels.get(s.getId()));
        }
        return 0;
    }

    /** Sets the level for a skill the student has (ignored for skills they don't have). */
    public void setLevel(Skill skill, int level) {
        skillLevels.put(skill.getId(), SkillLevel.clamp(level));
    }

    /** Skill id to level for every skill the student has. Used by matching. */
    public Map<Long, Integer> levels() {
        Map<Long, Integer> out = new HashMap<>();
        for (Skill s : skills) out.put(s.getId(), SkillLevel.clamp(skillLevels.get(s.getId())));
        return out;
    }

    /** Drops levels for skills the student no longer has. */
    public void pruneLevels() {
        Set<Long> ids = new HashSet<>();
        for (Skill s : skills) ids.add(s.getId());
        skillLevels.keySet().retainAll(ids);
        verifiedLevels.keySet().retainAll(ids);
    }

    public Map<Long, Integer> getVerifiedLevels() { return verifiedLevels; }

    /** Level proved for a skill (0 = not verified). */
    public int verifiedLevelOf(Skill skill) { return verifiedLevels.getOrDefault(skill.getId(), 0); }

    /** Records a proved level; never lowers an earlier proof. */
    public void verify(Skill skill, int level) { verifiedLevels.merge(skill.getId(), SkillLevel.clamp(level), Math::max); }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public boolean isPlacementConsent() { return placementConsent; }
    public LocalDateTime getConsentAt() { return consentAt; }
    public void setPlacementConsent(boolean consent) {
        this.placementConsent = consent;
        this.consentAt = consent ? LocalDateTime.now() : null;
    }
    public String getVideoStoredName() { return videoStoredName; }
    public String getVideoContentType() { return videoContentType; }
    public Long getVideoSize() { return videoSize; }
    public LocalDateTime getVideoUploadedAt() { return videoUploadedAt; }
    public boolean hasVideo() { return videoStoredName != null; }
    public void setVideo(String storedName, String contentType, Long size) {
        this.videoStoredName = storedName;
        this.videoContentType = contentType;
        this.videoSize = size;
        this.videoUploadedAt = storedName == null ? null : LocalDateTime.now();
    }
}
