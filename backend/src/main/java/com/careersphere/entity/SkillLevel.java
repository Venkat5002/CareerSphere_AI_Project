package com.careersphere.entity;

/**
 * How well a student knows a skill, or the minimum a job asks for.
 * Stored as a number (1-3) so the scoring stays simple arithmetic.
 */
public enum SkillLevel {
    BEGINNER(1, "Beginner"),
    INTERMEDIATE(2, "Intermediate"),
    ADVANCED(3, "Advanced");

    public static final int MIN = 1;
    public static final int MAX = 3;

    private final int value;
    private final String label;

    SkillLevel(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int value() { return value; }
    public String label() { return label; }

    /** Clamps any number to 1..3; null means Beginner, the default. */
    public static int clamp(Integer level) {
        if (level == null) return MIN;
        return Math.max(MIN, Math.min(MAX, level));
    }

    public static String label(int level) {
        return switch (level) {
            case 1 -> BEGINNER.label;
            case 2 -> INTERMEDIATE.label;
            case 3 -> ADVANCED.label;
            default -> "None";
        };
    }
}
