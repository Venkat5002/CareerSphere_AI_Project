package com.careersphere.entity;

import jakarta.persistence.*;

/** A skill from the shared catalog (e.g. "Java", category "Programming"). */
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    protected Skill() {}

    public Skill(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
}
