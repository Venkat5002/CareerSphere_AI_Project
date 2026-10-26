package com.careersphere.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Metadata for a student's uploaded resume. The file itself lives on disk. */
@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One resume per student; uploading again replaces it. */
    @OneToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private User student;

    @Column(nullable = false) private String originalFileName;
    @Column(nullable = false, unique = true) private String storedFileName;
    @Column(nullable = false, length = 120) private String contentType;
    @Column(nullable = false) private long fileSize;
    @Column(nullable = false) private LocalDateTime uploadedAt;

    protected Resume() {}

    public Resume(User student) { this.student = student; }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String v) { this.originalFileName = v; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String v) { this.storedFileName = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long v) { this.fileSize = v; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime v) { this.uploadedAt = v; }
}
