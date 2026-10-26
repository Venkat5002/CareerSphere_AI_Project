package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A student's application to a job.
 * The unique constraint makes duplicate applications impossible at database level.
 */
@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_job_student",
                columnNames = {"job_id", "student_id"}))
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    /** Optional note from the recruiter, shown to the student (e.g. interview time, rejection reason). */
    @Column(length = 1000)
    private String recruiterNote;

    @Column(nullable = false) private LocalDateTime appliedAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    protected Application() {}

    public Application(Job job, User student) {
        this.job = job;
        this.student = student;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getStudent() { return student; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    public String getRecruiterNote() { return recruiterNote; }
    public void setRecruiterNote(String recruiterNote) { this.recruiterNote = recruiterNote; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
