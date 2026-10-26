package com.careersphere.controller;

import com.careersphere.ai.ResumeSkillService;
import com.careersphere.dto.DashboardDtos.OpportunityDto;
import com.careersphere.dto.DashboardDtos.StudentDashboardDto;
import com.careersphere.dto.JobDtos.*;
import com.careersphere.dto.StudentDtos.*;
import com.careersphere.dto.ReadinessDtos.*;
import com.careersphere.entity.Certificate;
import com.careersphere.entity.Resume;
import com.careersphere.security.AuthUser;
import com.careersphere.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Everything a logged-in STUDENT can do. The student is always taken from the JWT
 * (@AuthenticationPrincipal), so one student can never read or change another's data.
 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentProfileService profileService;
    private final ResumeService resumeService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final DashboardService dashboardService;
    private final ResumeSkillService resumeSkillService;
    private final ReadinessService readinessService;
    private final AssessmentService assessmentService;
    private final CertificateService certificateService;
    private final RoadmapService roadmapService;
    private final TalentService talentService;

    public StudentController(StudentProfileService profileService, ResumeService resumeService, JobService jobService,
                             ApplicationService applicationService, DashboardService dashboardService,
                             ResumeSkillService resumeSkillService, ReadinessService readinessService,
                             AssessmentService assessmentService, CertificateService certificateService,
                             RoadmapService roadmapService, TalentService talentService) {
        this.roadmapService = roadmapService;
        this.talentService = talentService;
        this.readinessService = readinessService;
        this.assessmentService = assessmentService;
        this.certificateService = certificateService;
        this.profileService = profileService;
        this.resumeService = resumeService;
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.dashboardService = dashboardService;
        this.resumeSkillService = resumeSkillService;
    }

    // ---- dashboard & opportunity math
    @GetMapping("/dashboard")
    public StudentDashboardDto dashboard(@AuthenticationPrincipal AuthUser me) {
        return dashboardService.student(me.id());
    }

    @GetMapping("/opportunities")
    public List<OpportunityDto> opportunities(@AuthenticationPrincipal AuthUser me) {
        return dashboardService.opportunities(me.id());
    }

    /** GET /api/student/what-if?skills=Docker,Spring Boot */
    @GetMapping("/what-if")
    public Map<String, Object> whatIf(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "") String skills) {
        List<String> names = Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return dashboardService.whatIf(me.id(), names);
    }

    // ---- profile
    @GetMapping("/profile")
    public ProfileDto profile(@AuthenticationPrincipal AuthUser me) {
        return profileService.get(me.id());
    }

    @PutMapping("/profile")
    public ProfileDto updateProfile(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ProfileUpdateRequest req) {
        return profileService.update(me.id(), req);
    }

    // ---- resume
    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeDto uploadResume(@AuthenticationPrincipal AuthUser me, @RequestPart("resume") MultipartFile file) {
        return resumeService.upload(me.id(), file);
    }

    /** Returns the resume details, or 204 No Content if none uploaded yet. */
    @GetMapping("/resume")
    public ResponseEntity<ResumeDto> resume(@AuthenticationPrincipal AuthUser me) {
        ResumeDto dto = resumeService.get(me.id());
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }

    @GetMapping("/resume/file")
    public ResponseEntity<Resource> downloadResume(@AuthenticationPrincipal AuthUser me) {
        return fileResponse(resumeService, resumeService.metadata(me.id()));
    }

    @DeleteMapping("/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResume(@AuthenticationPrincipal AuthUser me) {
        resumeService.delete(me.id());
    }

    /** Reads the uploaded resume and suggests skills, each with evidence quoted from the resume. Adds nothing. */
    @PostMapping("/resume/skills")
    public ResumeSkillsResponse resumeSkills(@AuthenticationPrincipal AuthUser me) {
        return resumeSkillService.analyze(me.id());
    }

    /** Adds the skills the student confirmed. Existing skills are kept. */
    @PostMapping("/profile/skills")
    public ProfileDto addSkills(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AddSkillsRequest req) {
        return profileService.addSkills(me.id(), req.skills());
    }

    // ---- jobs & applications
    @GetMapping("/jobs")
    public List<JobDto> jobs(@AuthenticationPrincipal AuthUser me,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "match") String sort) {
        return jobService.openJobsForStudent(me.id(), q, sort);
    }

    @GetMapping("/jobs/{id}")
    public JobDto job(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return jobService.jobForStudent(me.id(), id);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDto apply(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ApplyRequest req) {
        return applicationService.apply(me.id(), req.jobId());
    }

    @GetMapping("/applications")
    public List<ApplicationDto> applications(@AuthenticationPrincipal AuthUser me) {
        return applicationService.myApplications(me.id());
    }

    // ---- personal skill roadmap and invitations
    @GetMapping("/roadmap")
    public RoadmapService.RoadmapDto roadmap(@AuthenticationPrincipal AuthUser me) {
        return roadmapService.forStudent(me.id());
    }

    @GetMapping("/invitations")
    public List<TalentService.InvitationDto> invitations(@AuthenticationPrincipal AuthUser me) {
        return talentService.forStudent(me.id());
    }

    // ---- placement profile: readiness, consent, leaderboard
    @GetMapping("/readiness")
    public ReadinessDto readiness(@AuthenticationPrincipal AuthUser me) {
        return readinessService.readiness(me.id());
    }

    @PutMapping("/consent")
    public ReadinessDto consent(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ConsentRequest req) {
        return readinessService.setConsent(me.id(), req.consent());
    }

    @GetMapping("/leaderboard")
    public LeaderboardDto leaderboard(@AuthenticationPrincipal AuthUser me) {
        return readinessService.leaderboard(me.id(), false);
    }

    // ---- introduction video
    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoDto uploadVideo(@AuthenticationPrincipal AuthUser me, @RequestPart("video") MultipartFile file) {
        return readinessService.uploadVideo(me.id(), file);
    }

    @GetMapping("/video")
    public ResponseEntity<VideoDto> video(@AuthenticationPrincipal AuthUser me) {
        VideoDto v = readinessService.video(me.id());
        return v == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/video/file")
    public ResponseEntity<Resource> videoFile(@AuthenticationPrincipal AuthUser me) {
        return videoResponse(readinessService.videoFile(me.id()));
    }

    @DeleteMapping("/video")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVideo(@AuthenticationPrincipal AuthUser me) {
        readinessService.deleteVideo(me.id());
    }

    // ---- skill assessments
    @GetMapping("/assessments")
    public AssessmentsDto assessments(@AuthenticationPrincipal AuthUser me) {
        return assessmentService.overview(me.id());
    }

    /** POST /api/student/assessments/start?skill=Java */
    @PostMapping("/assessments/start")
    public AttemptDto startAssessment(@AuthenticationPrincipal AuthUser me, @RequestParam String skill) {
        return assessmentService.start(me.id(), skill);
    }

    @PostMapping("/assessments/{attemptId}/submit")
    public ResultDto submitAssessment(@AuthenticationPrincipal AuthUser me, @PathVariable Long attemptId,
                                      @Valid @RequestBody AnswersRequest req) {
        return assessmentService.submit(me.id(), attemptId, req.answers());
    }

    // ---- certificates
    @GetMapping("/certificates")
    public List<CertificateDto> certificates(@AuthenticationPrincipal AuthUser me) {
        return certificateService.mine(me.id());
    }

    @PostMapping(value = "/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateDto addCertificate(@AuthenticationPrincipal AuthUser me,
                                         @RequestParam String name, @RequestParam String issuer,
                                         @RequestParam(required = false) String credentialId,
                                         @RequestParam(required = false) String verificationUrl,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
                                         @RequestParam(required = false) String skill,
                                         @RequestPart("file") MultipartFile file) {
        return certificateService.submit(me.id(), name, issuer, credentialId, verificationUrl, issueDate, validUntil, skill, file);
    }

    @DeleteMapping("/certificates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCertificate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        certificateService.delete(me.id(), id);
    }

    @GetMapping("/certificates/{id}/file")
    public ResponseEntity<Resource> certificateFile(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return certificateResponse(certificateService, certificateService.forStudentFile(me.id(), id));
    }

    static ResponseEntity<Resource> certificateResponse(CertificateService service, Certificate c) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.getContentType()))
                .contentLength(c.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(c.getOriginalFileName(), StandardCharsets.UTF_8).build().toString())
                .body(service.file(c));
    }

    static ResponseEntity<Resource> videoResponse(ReadinessService.VideoFile v) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(v.contentType()))
                .contentLength(v.size())
                .body(v.resource());
    }

    /** Shared by student and recruiter resume downloads. */
    static ResponseEntity<Resource> fileResponse(ResumeService resumeService, Resume r) {
        Resource file = resumeService.file(r);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(r.getContentType()))
                .contentLength(r.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(r.getOriginalFileName(), StandardCharsets.UTF_8).build().toString())
                .body(file);
    }
}
