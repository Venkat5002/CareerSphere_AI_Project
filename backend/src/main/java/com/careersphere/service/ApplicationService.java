package com.careersphere.service;

import com.careersphere.dto.JobDtos.*;
import com.careersphere.entity.*;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.ApplicationRepository;
import com.careersphere.repository.JobRepository;
import com.careersphere.repository.ResumeRepository;
import com.careersphere.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final UserRepository users;
    private final ResumeRepository resumes;
    private final JobService jobService;
    private final StudentProfileService studentProfileService;
    private final MatchService matchService;
    private final CertificateService certificateService;

    public ApplicationService(ApplicationRepository applications, JobRepository jobs, UserRepository users,
                              ResumeRepository resumes, JobService jobService,
                              StudentProfileService studentProfileService, MatchService matchService,
                              CertificateService certificateService) {
        this.certificateService = certificateService;
        this.applications = applications;
        this.jobs = jobs;
        this.users = users;
        this.resumes = resumes;
        this.jobService = jobService;
        this.studentProfileService = studentProfileService;
        this.matchService = matchService;
    }

    // ------------------------------------------------------------------ student side

    @Transactional
    public ApplicationDto apply(Long studentId, Long jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (job.getStatus() != JobStatus.OPEN) throw ApiException.badRequest("This job is no longer accepting applications.");
        if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("The application deadline for this job has passed.");
        }
        if (!resumes.existsByStudentId(studentId)) {
            throw ApiException.badRequest("Upload your resume before applying, so the recruiter can review it.");
        }
        if (!studentProfileService.profileOf(studentId).isPlacementConsent()) {
            throw ApiException.badRequest("Give placement consent on your placement profile before applying, so recruiters may see your profile.");
        }
        // Friendly check first; the database unique constraint is the real guarantee.
        if (applications.existsByJobIdAndStudentId(jobId, studentId)) {
            throw ApiException.conflict("You have already applied to this job.");
        }
        User student = users.findById(studentId).orElseThrow(() -> ApiException.notFound("User not found."));
        try {
            Application saved = applications.saveAndFlush(new Application(job, student));
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("You have already applied to this job.");
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> myApplications(Long studentId) {
        return applications.findByStudentIdOrderByAppliedAtDesc(studentId).stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------ recruiter side

    @Transactional
    public List<ApplicantDto> applicants(Long recruiterId, Long jobId) {
        Job job = jobService.ownedJob(recruiterId, jobId);
        return applications.findByJobIdOrderByAppliedAtDesc(job.getId()).stream()
                .map(this::toApplicantDto)
                .sorted((a, b) -> Integer.compare(b.match().score(), a.match().score()))
                .toList();
    }

    @Transactional
    public ApplicantDto updateStatus(Long recruiterId, Long applicationId, StatusUpdateRequest req) {
        Application app = ownedApplication(recruiterId, applicationId);
        if (req.status() == ApplicationStatus.APPLIED) {
            throw ApiException.badRequest("An application cannot be moved back to Applied.");
        }
        app.setStatus(req.status());
        app.setRecruiterNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        return toApplicantDto(app);
    }

    /** A recruiter may only open a resume attached to an application for one of their own jobs. */
    @Transactional(readOnly = true)
    public Long studentIdForResume(Long recruiterId, Long applicationId) {
        return ownedApplication(recruiterId, applicationId).getStudent().getId();
    }

    private Application ownedApplication(Long recruiterId, Long applicationId) {
        Application app = applications.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        if (!app.getJob().getRecruiter().getId().equals(recruiterId)) {
            throw ApiException.forbidden("This application belongs to another recruiter's job.");
        }
        return app;
    }

    // ------------------------------------------------------------------ mapping

    private ApplicationDto toDto(Application a) {
        Job j = a.getJob();
        return new ApplicationDto(a.getId(), j.getId(), j.getTitle(), j.getCompanyName(), j.getLocation(),
                a.getStatus(), a.getRecruiterNote(), a.getAppliedAt(), a.getUpdatedAt());
    }

    private ApplicantDto toApplicantDto(Application a) {
        User s = a.getStudent();
        StudentProfile p = studentProfileService.profileOf(s.getId());
        MatchDto match = matchService.match(p, a.getJob());
        return new ApplicantDto(a.getId(), s.getId(), s.getFullName(), s.getEmail(), p.getCollege(), p.getBranch(),
                p.getGraduationYear(), p.getCgpa(), SkillService.names(p.getSkills()),
                StudentProfileService.levelsByName(p), StudentProfileService.verifiedByName(p),
                certificateService.verifiedNames(s.getId()), p.hasVideo(), p.getHeadline(), p.getGithubUrl(),
                p.getLinkedinUrl(), resumes.existsByStudentId(s.getId()), a.getStatus(), a.getRecruiterNote(),
                a.getAppliedAt(), match);
    }
}
