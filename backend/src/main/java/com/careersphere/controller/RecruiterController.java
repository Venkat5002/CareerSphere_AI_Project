package com.careersphere.controller;

import com.careersphere.dto.DashboardDtos.*;
import com.careersphere.dto.JobDtos.*;
import com.careersphere.security.AuthUser;
import com.careersphere.service.*;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Everything a logged-in RECRUITER can do - always limited to their own jobs. */
@RestController
@RequestMapping("/api/recruiter")
public class RecruiterController {

    private final RecruiterProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final DashboardService dashboardService;
    private final ResumeService resumeService;
    private final ReadinessService readinessService;
    private final TalentService talentService;

    public RecruiterController(RecruiterProfileService profileService, JobService jobService,
                               ApplicationService applicationService, DashboardService dashboardService,
                               ResumeService resumeService, ReadinessService readinessService,
                               TalentService talentService) {
        this.talentService = talentService;
        this.readinessService = readinessService;
        this.profileService = profileService;
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.dashboardService = dashboardService;
        this.resumeService = resumeService;
    }

    @GetMapping("/dashboard")
    public RecruiterDashboardDto dashboard(@AuthenticationPrincipal AuthUser me) {
        return dashboardService.recruiter(me.id());
    }

    @GetMapping("/profile")
    public RecruiterProfileDto profile(@AuthenticationPrincipal AuthUser me) {
        return profileService.get(me.id());
    }

    @PutMapping("/profile")
    public RecruiterProfileDto updateProfile(@AuthenticationPrincipal AuthUser me,
                                             @Valid @RequestBody RecruiterProfileUpdateRequest req) {
        return profileService.update(me.id(), req);
    }

    // ---- jobs
    @GetMapping("/jobs")
    public List<JobDto> jobs(@AuthenticationPrincipal AuthUser me) {
        return jobService.recruiterJobs(me.id());
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody JobRequest req) {
        return jobService.create(me.id(), req);
    }

    @PutMapping("/jobs/{id}")
    public JobDto update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody JobRequest req) {
        return jobService.update(me.id(), id, req);
    }

    @PatchMapping("/jobs/{id}/status")
    public JobDto setStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                            @Valid @RequestBody JobStatusRequest req) {
        return jobService.setStatus(me.id(), id, req.status());
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        jobService.delete(me.id(), id);
    }

    // ---- applicants
    @GetMapping("/jobs/{id}/applicants")
    public List<ApplicantDto> applicants(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return applicationService.applicants(me.id(), id);
    }

    @PatchMapping("/applications/{id}/status")
    public ApplicantDto updateStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateRequest req) {
        return applicationService.updateStatus(me.id(), id, req);
    }

    /** Resume of an applicant - only for applications to this recruiter's own jobs. */
    @GetMapping("/applications/{id}/resume")
    public ResponseEntity<Resource> applicantResume(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Long studentId = applicationService.studentIdForResume(me.id(), id);
        return StudentController.fileResponse(resumeService, resumeService.metadata(studentId));
    }

    /** Introduction video of an applicant - only for applications to this recruiter's own jobs. */
    @GetMapping("/applications/{id}/video")
    public ResponseEntity<Resource> applicantVideo(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Long studentId = applicationService.studentIdForResume(me.id(), id);
        return StudentController.videoResponse(readinessService.videoFile(studentId));
    }

    /** Hidden Talent: consented students who haven't applied but fit this job, or are one step away. */
    @GetMapping("/jobs/{id}/talent")
    public TalentService.TalentDto talent(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return talentService.forJob(me.id(), id);
    }

    public record InviteRequest(@jakarta.validation.constraints.Size(max = 300) String message) {}

    @PostMapping("/jobs/{id}/invite/{studentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TalentService.Candidate invite(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long studentId,
                                          @RequestBody(required = false) InviteRequest req) {
        return talentService.invite(me.id(), id, studentId, req == null ? null : req.message());
    }
}
