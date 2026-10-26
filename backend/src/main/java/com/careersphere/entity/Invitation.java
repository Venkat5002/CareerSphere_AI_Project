package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A recruiter invited a student to apply for a job (Hidden Talent). One invitation per job and student. */
@Entity
@Table(name = "invitations",
        uniqueConstraints = @UniqueConstraint(name = "uk_invitation_job_student", columnNames = {"job_id", "student_id"}))
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(length = 300) private String message;
    @Column(nullable = false) private LocalDateTime invitedAt = LocalDateTime.now();

    protected Invitation() {}

    public Invitation(Job job, User student, String message) {
        this.job = job;
        this.student = student;
        this.message = message;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getStudent() { return student; }
    public String getMessage() { return message; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
}
