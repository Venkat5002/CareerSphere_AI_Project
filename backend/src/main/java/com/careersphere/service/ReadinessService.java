package com.careersphere.service;

import com.careersphere.config.UploadsConfig.VideoFiles;
import com.careersphere.dto.ReadinessDtos.*;
import com.careersphere.entity.*;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.*;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Placement readiness: the résumé score, readiness points and the leaderboard, plus the two
 * placement-profile steps that are simple switches or files (consent and the introduction video).
 *
 * The résumé score is calculated from the profile with fixed rules, so the same profile always
 * gets the same score and every point can be explained (see {@link #score}).
 *
 * Readiness points = résumé score + 15 per verified skill + 10 per verified certificate
 *                    + 5 per open job within reach.
 */
@Service
public class ReadinessService {

    public static final String HOW_POINTS_WORK =
            "Points = résumé score + 15 for each verified skill + 10 for each verified certificate + 5 for each open job within reach.";

    private final StudentProfileService profiles;
    private final StudentProfileRepository profileRepo;
    private final ResumeRepository resumes;
    private final CertificateRepository certificates;
    private final JobRepository jobs;
    private final MatchService matchService;
    private final FileStore videos;

    public ReadinessService(StudentProfileService profiles, StudentProfileRepository profileRepo, ResumeRepository resumes,
                            CertificateRepository certificates, JobRepository jobs, MatchService matchService,
                            VideoFiles videos) {
        this.profiles = profiles;
        this.profileRepo = profileRepo;
        this.resumes = resumes;
        this.certificates = certificates;
        this.jobs = jobs;
        this.matchService = matchService;
        this.videos = videos.store();
    }

    // ------------------------------------------------------------------ résumé score

    /** The résumé score's rules. Maximum 100. Pure function of the profile, so it can be unit tested. */
    public static List<ScoreItem> score(StudentProfile p, boolean hasResume, long verifiedCertificates) {
        List<ScoreItem> out = new ArrayList<>();
        out.add(item("Résumé uploaded", hasResume ? 15 : 0, 15, "Upload your résumé as a PDF or Word file."));
        int about = p.getAbout() == null ? 0 : p.getAbout().trim().length();
        out.add(item("About you (150+ characters)", about >= 150 ? 10 : about >= 50 ? 5 : 0, 10,
                "Write 2-3 sentences on what you build and what role you want."));
        int skills = Math.min(5, p.getSkills().size());
        out.add(item("Skills listed (5 or more)", skills * 4, 20, "Add the skills you can talk about in an interview."));
        long strong = p.getSkills().stream().filter(s -> p.levelOf(s) >= SkillLevel.INTERMEDIATE.value()).count();
        out.add(item("Skills at Intermediate or above", (int) Math.min(5, strong) * 2, 10,
                "Set honest levels on your skills; Intermediate means you have built something with it."));
        int verified = p.getVerifiedLevels().size();
        out.add(item("Skills verified by an assessment or certificate", Math.min(3, verified) * 5, 15,
                "Take a 10-minute skill assessment."));
        out.add(item("Verified certificates", (int) Math.min(2, verifiedCertificates) * 5, 10,
                "Add a course or exam certificate for the placement cell to verify."));
        out.add(item("GitHub link", p.getGithubUrl() != null ? 5 : 0, 5, "Add your GitHub profile."));
        out.add(item("LinkedIn link", p.getLinkedinUrl() != null ? 5 : 0, 5, "Add your LinkedIn profile."));
        out.add(item("Introduction video", p.hasVideo() ? 5 : 0, 5, "Record a one-minute introduction."));
        boolean academics = p.getCgpa() != null && p.getTenthPercentage() != null && p.getTwelfthPercentage() != null;
        out.add(item("CGPA, 10th and 12th marks", academics ? 5 : 0, 5, "Fill in your academic details."));
        return out;
    }

    private static ScoreItem item(String label, int points, int max, String tip) {
        return new ScoreItem(label, points, max, points >= max ? null : tip);
    }

    public static int total(List<ScoreItem> items) {
        return items.stream().mapToInt(ScoreItem::points).sum();
    }

    /** The next step on the placement profile, in the same order as the university portal: résumé, consent, assessment, video. */
    public static String nextStep(boolean hasResume, boolean consent, int verifiedSkills, boolean hasVideo) {
        if (!hasResume) return "RESUME";
        if (!consent) return "CONSENT";
        if (verifiedSkills == 0) return "ASSESSMENT";
        if (!hasVideo) return "VIDEO";
        return "DONE";
    }

    // ------------------------------------------------------------------ readiness and leaderboard

    private record Row(StudentProfile p, int resumeScore, int verifiedSkills, int verifiedCerts, int reach, int points) {}

    private Row row(StudentProfile p, List<Job> open) {
        Long id = p.getUser().getId();
        long certs = certificates.countByStudentIdAndStatus(id, CertificateStatus.VERIFIED);
        int resumeScore = total(score(p, resumes.existsByStudentId(id), certs));
        int verified = p.getVerifiedLevels().size();
        int reach = (int) open.stream().filter(j -> matchService.reachable(p, j)).count();
        return new Row(p, resumeScore, verified, (int) certs, reach, resumeScore + 15 * verified + 10 * (int) certs + 5 * reach);
    }

    private List<Row> ranked() {
        List<Job> open = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        List<Row> rows = new ArrayList<>();
        for (StudentProfile p : profileRepo.findAll()) rows.add(row(p, open));
        rows.sort(Comparator.comparingInt(Row::points).reversed()
                .thenComparing(r -> r.p().getUser().getFullName(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional
    public ReadinessDto readiness(Long studentId) {
        StudentProfile p = profiles.profileOf(studentId);
        List<Row> rows = ranked();
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).p().getUser().getId().equals(studentId)) rank = i + 1;
        Row me = rank > 0 ? rows.get(rank - 1) : row(p, jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN));
        boolean hasResume = resumes.existsByStudentId(studentId);
        List<ScoreItem> breakdown = score(p, hasResume, me.verifiedCerts());
        return new ReadinessDto(total(breakdown), breakdown, me.points(), me.verifiedSkills(), me.verifiedCerts(), me.reach(),
                nextStep(hasResume, p.isPlacementConsent(), me.verifiedSkills(), p.hasVideo()),
                hasResume, p.isPlacementConsent(), p.hasVideo(), me.verifiedSkills(),
                rank == 0 ? null : rank, rows.size());
    }

    /**
     * Top 10 of the batch plus the viewer. Other students are shown by first name and initial;
     * the placement cell (forAdmin) sees full names.
     */
    @Transactional
    public LeaderboardDto leaderboard(Long viewerId, boolean forAdmin) {
        List<Row> rows = ranked();
        List<LeaderboardEntry> top = new ArrayList<>();
        LeaderboardEntry you = null;
        String myBranch = null;
        for (Row r : rows) if (r.p().getUser().getId().equals(viewerId)) myBranch = r.p().getBranch();
        int branchRank = 0, branchSize = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean mine = r.p().getUser().getId().equals(viewerId);
            if (myBranch != null && myBranch.equals(r.p().getBranch())) {
                branchSize++;
                if (mine) branchRank = branchSize;
            }
            String name = mine || forAdmin ? r.p().getUser().getFullName() : shortName(r.p().getUser().getFullName());
            LeaderboardEntry e = new LeaderboardEntry(i + 1, name, r.p().getBranch(), r.points(), r.resumeScore(),
                    r.verifiedSkills(), r.verifiedCerts(), mine);
            if (i < (forAdmin ? rows.size() : 10)) top.add(e);
            if (mine) you = e;
        }
        return new LeaderboardDto(top, you, rows.size(), branchRank == 0 ? null : branchRank, branchSize, HOW_POINTS_WORK);
    }

    static String shortName(String full) {
        String[] parts = full.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    // ------------------------------------------------------------------ consent

    @Transactional
    public ReadinessDto setConsent(Long studentId, boolean consent) {
        StudentProfile p = profiles.profileOf(studentId);
        if (consent && !resumes.existsByStudentId(studentId)) {
            throw ApiException.badRequest("Upload your résumé first, so recruiters have something to see.");
        }
        p.setPlacementConsent(consent);
        return readiness(studentId);
    }

    // ------------------------------------------------------------------ introduction video

    @Transactional
    public VideoDto uploadVideo(Long studentId, MultipartFile file) {
        StudentProfile p = profiles.profileOf(studentId);
        FileStore.Saved saved = videos.save(file);
        String previous = p.getVideoStoredName();
        p.setVideo(saved.storedName(), saved.contentType(), saved.size());
        videos.deleteQuietly(previous);
        return new VideoDto(p.getVideoContentType(), p.getVideoSize(), p.getVideoUploadedAt());
    }

    @Transactional(readOnly = true)
    public VideoDto video(Long studentId) {
        StudentProfile p = profiles.profileOf(studentId);
        return p.hasVideo() ? new VideoDto(p.getVideoContentType(), p.getVideoSize(), p.getVideoUploadedAt()) : null;
    }

    @Transactional
    public void deleteVideo(Long studentId) {
        StudentProfile p = profiles.profileOf(studentId);
        if (!p.hasVideo()) throw ApiException.notFound("No introduction video yet.");
        videos.deleteQuietly(p.getVideoStoredName());
        p.setVideo(null, null, null);
    }

    /** The stored video of a student, with its content type and size. */
    public record VideoFile(Resource resource, String contentType, long size) {}

    @Transactional(readOnly = true)
    public VideoFile videoFile(Long studentId) {
        StudentProfile p = profiles.profileOf(studentId);
        if (!p.hasVideo()) throw ApiException.notFound("No introduction video yet.");
        return new VideoFile(videos.resource(p.getVideoStoredName()), p.getVideoContentType(), p.getVideoSize());
    }
}
