package com.careersphere.service;

import com.careersphere.entity.*;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.ApplicationRepository;
import com.careersphere.repository.InvitationRepository;
import com.careersphere.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Hidden Talent: Opportunity Math turned around for recruiters.
 *
 * For one job, it finds students who have NOT applied but would fit: those already within reach,
 * and those one learning step away (one skill to learn, or one level to raise). Only students who
 * gave placement consent are ever shown. The recruiter can invite a student to apply; the student
 * sees the invitation and decides.
 */
@Service
public class TalentService {

    public record Candidate(Long studentId, String name, String branch, Double cgpa, String headline, int score,
                            String stepNeeded, List<String> verified, boolean invited) {}

    public record TalentDto(Long jobId, String job, int consentedStudents, List<Candidate> withinReach,
                            List<Candidate> oneStepAway, String note) {}

    public record InvitationDto(Long id, Long jobId, String job, String company, String message, LocalDateTime invitedAt,
                                boolean applied, int score) {}

    private final JobService jobService;
    private final StudentProfileRepository profiles;
    private final ApplicationRepository applications;
    private final InvitationRepository invitations;
    private final MatchService match;
    private final StudentProfileService profileService;

    public TalentService(JobService jobService, StudentProfileRepository profiles, ApplicationRepository applications,
                         InvitationRepository invitations, MatchService match, StudentProfileService profileService) {
        this.jobService = jobService;
        this.profiles = profiles;
        this.applications = applications;
        this.invitations = invitations;
        this.match = match;
        this.profileService = profileService;
    }

    @Transactional(readOnly = true)
    public TalentDto forJob(Long recruiterId, Long jobId) {
        Job job = jobService.ownedJob(recruiterId, jobId);
        return find(job, profiles.findAll());
    }

    /** Pure enough to unit test: the job plus every student profile. */
    public TalentDto find(Job job, List<StudentProfile> all) {
        List<Candidate> reach = new ArrayList<>(), near = new ArrayList<>();
        int consented = 0;
        for (StudentProfile p : all) {
            if (!p.isPlacementConsent()) continue;
            consented++;
            Long sid = p.getUser().getId();
            if (applications.existsByJobIdAndStudentId(job.getId(), sid)) continue;
            Map<Long, Integer> levels = p.levels();
            int score = match.score(levels, job);
            if (!match.cgpaEligible(p.getCgpa(), job)) continue;
            boolean invited = invitations.existsByJobIdAndStudentId(job.getId(), sid);
            List<String> verified = new ArrayList<>(StudentProfileService.verifiedByName(p).keySet());
            if (score >= MatchService.MATCH_THRESHOLD) {
                reach.add(new Candidate(sid, p.getUser().getFullName(), p.getBranch(), p.getCgpa(), p.getHeadline(), score, null, verified, invited));
                continue;
            }
            for (RoadmapService.Action a : RoadmapService.candidateActions(levels, List.of(job))) {
                if (match.reachable(RoadmapService.apply(levels, List.of(a)), p.getCgpa(), job)) {
                    near.add(new Candidate(sid, p.getUser().getFullName(), p.getBranch(), p.getCgpa(), p.getHeadline(), score, a.label(), verified, invited));
                    break;
                }
            }
        }
        Comparator<Candidate> order = Comparator.comparingInt(Candidate::score).reversed()
                .thenComparing(c -> -c.verified().size()).thenComparing(Candidate::name);
        reach.sort(order);
        near.sort(order);
        return new TalentDto(job.getId(), job.getTitle(), consented, reach, near,
                "Only students who gave placement consent are shown. Students who already applied are left out.");
    }

    @Transactional
    public Candidate invite(Long recruiterId, Long jobId, Long studentId, String message) {
        Job job = jobService.ownedJob(recruiterId, jobId);
        if (job.getStatus() != JobStatus.OPEN) throw ApiException.badRequest("This job is closed.");
        StudentProfile p = profiles.findByUserId(studentId).orElseThrow(() -> ApiException.notFound("Student not found."));
        if (!p.isPlacementConsent()) throw ApiException.forbidden("This student hasn't given placement consent.");
        if (invitations.existsByJobIdAndStudentId(jobId, studentId)) throw ApiException.conflict("You already invited this student.");
        String text = message == null || message.isBlank() ? null : message.trim();
        if (text != null && text.length() > 300) text = text.substring(0, 300);
        invitations.save(new Invitation(job, p.getUser(), text));
        return new Candidate(studentId, p.getUser().getFullName(), p.getBranch(), p.getCgpa(), p.getHeadline(),
                match.score(p.levels(), job), null, new ArrayList<>(StudentProfileService.verifiedByName(p).keySet()), true);
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> forStudent(Long studentId) {
        StudentProfile p = profileService.profileOf(studentId);
        return invitations.findByStudentIdOrderByInvitedAtDesc(studentId).stream()
                .map(i -> new InvitationDto(i.getId(), i.getJob().getId(), i.getJob().getTitle(), i.getJob().getCompanyName(),
                        i.getMessage(), i.getInvitedAt(), applications.existsByJobIdAndStudentId(i.getJob().getId(), studentId),
                        match.score(p.levels(), i.getJob())))
                .toList();
    }
}
