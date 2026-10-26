package com.careersphere.dto;

import com.careersphere.entity.CertificateStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Placement readiness: résumé score, leaderboard, skill assessments, certificates, consent, video. */
public final class ReadinessDtos {

    private ReadinessDtos() {}

    // ---- résumé score and the guided steps
    /** One line of the résumé score: what it checks, points earned out of the maximum, and how to earn the rest. */
    public record ScoreItem(String label, int points, int max, String tip) {}

    /** step: RESUME, CONSENT, ASSESSMENT, VIDEO or DONE - the next thing to do on the placement profile. */
    public record ReadinessDto(int resumeScore, List<ScoreItem> breakdown, int points, int verifiedSkills,
                               int verifiedCertificates, int jobsWithinReach, String nextStep,
                               boolean hasResume, boolean consent, boolean hasVideo, int assessmentsPassed,
                               Integer rankInBatch, Integer batchSize) {}

    public record LeaderboardEntry(int rank, String name, String branch, int points, int resumeScore,
                                   int verifiedSkills, int verifiedCertificates, boolean you) {}

    public record LeaderboardDto(List<LeaderboardEntry> top, LeaderboardEntry you, int batchSize,
                                 Integer rankInBranch, int branchSize, String howPointsWork) {}

    public record ConsentRequest(@NotNull(message = "Say yes or no") Boolean consent) {}

    public record VideoDto(String contentType, long size, LocalDateTime uploadedAt) {}

    // ---- skill assessments
    /** One skill the student can be tested on. verifiedLevel 0 = not verified yet. */
    public record AssessmentInfo(String skill, boolean inProfile, int yourLevel, int verifiedLevel,
                                 String verifiedLabel, Integer lastCorrect, Integer lastTotal,
                                 LocalDateTime lastTakenAt, LocalDateTime canRetakeAt) {}

    public record AssessmentsDto(boolean unlocked, String lockedReason, int minutes, int questions,
                                 String passRule, List<AssessmentInfo> skills) {}

    public record QuestionDto(int index, String question, List<String> options) {}

    public record AttemptDto(Long attemptId, String skill, int minutes, LocalDateTime startedAt,
                             LocalDateTime endsAt, List<QuestionDto> questions) {}

    public record AnswersRequest(@NotNull(message = "Answers are required") @Size(max = 20) List<Integer> answers) {}

    /** correct[i] says whether question i was answered correctly; the right answers are not revealed. */
    public record ResultDto(String skill, int correct, int total, int verifiedLevel, String verifiedLabel,
                            String levelBefore, String levelAfter, List<Boolean> correctByQuestion, String message) {}

    // ---- certificates
    public record CertificateDto(Long id, String name, String issuer, String credentialId, String verificationUrl,
                                 LocalDate issueDate, LocalDate validUntil, String skill, String fileName,
                                 CertificateStatus status, String reviewNote, LocalDateTime submittedAt,
                                 LocalDateTime reviewedAt, String studentName, String studentBranch) {}

    public record ReviewRequest(@NotNull(message = "Choose Verified or Rejected") CertificateStatus status,
                                @Size(max = 500) String note) {}
}
