package com.careersphere.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 150) private String companyName;
    @Column(length = 100) private String designation;
    @Column(length = 255) private String website;
    @Column(length = 100) private String location;
    @Column(length = 2000) private String description;

    protected RecruiterProfile() {}

    public RecruiterProfile(User user) { this.user = user; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { this.companyName = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { this.designation = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { this.website = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
}
