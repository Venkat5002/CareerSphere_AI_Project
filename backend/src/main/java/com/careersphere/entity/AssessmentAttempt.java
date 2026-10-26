package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One sitting of a skill assessment. The questions given are stored so the answers are
 * graded on the server against exactly that set; the browser never sees the correct answers.
 */
@Entity
@Table(name = "assessment_attempts")
public class AssessmentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    /** Question ids in the order shown, comma separated. */
    @Column(nullable = false, length = 400) private String questionIds;
    /** For each question, the order its options were shown in, e.g. "2103;0321;...". */
    @Column(nullable = false, length = 400) private String optionOrder;

    @Column(nullable = false) private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime submittedAt;
    private Integer correct;
    private Integer total;
    /** 0 = not verified, 1-3 = level proved. */
    private Integer verifiedLevel;

    protected AssessmentAttempt() {}

    public AssessmentAttempt(User student, Skill skill, String questionIds, String optionOrder) {
        this.student = student;
        this.skill = skill;
        this.questionIds = questionIds;
        this.optionOrder = optionOrder;
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public Skill getSkill() { return skill; }
    public String getQuestionIds() { return questionIds; }
    public String getOptionOrder() { return optionOrder; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Integer getCorrect() { return correct; }
    public Integer getTotal() { return total; }
    public Integer getVerifiedLevel() { return verifiedLevel; }
    public boolean isSubmitted() { return submittedAt != null; }

    public void finish(int correct, int total, int verifiedLevel) {
        this.correct = correct;
        this.total = total;
        this.verifiedLevel = verifiedLevel;
        this.submittedAt = LocalDateTime.now();
    }
}
