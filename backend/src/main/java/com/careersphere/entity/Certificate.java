package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A certificate a student earned elsewhere (NPTEL, Coursera, AWS...), checked by the placement cell. */
@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 120) private String issuer;
    @Column(length = 120) private String credentialId;
    @Column(length = 300) private String verificationUrl;
    private LocalDate issueDate;
    private LocalDate validUntil;

    /** The skill this certificate proves, if any. Verifying the certificate adds it to the profile. */
    @ManyToOne
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(nullable = false, length = 200) private String originalFileName;
    @Column(nullable = false, unique = true, length = 80) private String storedFileName;
    @Column(nullable = false, length = 60) private String contentType;
    @Column(nullable = false) private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificateStatus status = CertificateStatus.PENDING;
    @Column(length = 500) private String reviewNote;
    @Column(nullable = false) private LocalDateTime submittedAt = LocalDateTime.now();
    private LocalDateTime reviewedAt;

    protected Certificate() {}

    public Certificate(User student) { this.student = student; }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String v) { this.issuer = v; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String v) { this.credentialId = v; }
    public String getVerificationUrl() { return verificationUrl; }
    public void setVerificationUrl(String v) { this.verificationUrl = v; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate v) { this.issueDate = v; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate v) { this.validUntil = v; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill v) { this.skill = v; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String v) { this.originalFileName = v; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String v) { this.storedFileName = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long v) { this.fileSize = v; }
    public CertificateStatus getStatus() { return status; }
    public String getReviewNote() { return reviewNote; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }

    public void review(CertificateStatus status, String note) {
        this.status = status;
        this.reviewNote = note;
        this.reviewedAt = status == CertificateStatus.PENDING ? null : LocalDateTime.now();
    }
}
