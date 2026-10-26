package com.careersphere.service;

import com.careersphere.config.UploadsConfig.CertificateFiles;
import com.careersphere.dto.ReadinessDtos.CertificateDto;
import com.careersphere.entity.*;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.CertificateRepository;
import com.careersphere.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Certificates students earned elsewhere (NPTEL, Coursera, AWS, a workshop...).
 * The student uploads the certificate; the placement cell verifies or rejects it.
 * A verified certificate that names a skill adds that skill to the profile and marks it
 * verified at Intermediate (a completed course), unless an assessment already proved more.
 */
@Service
public class CertificateService {

    public static final int CERTIFICATE_LEVEL = SkillLevel.INTERMEDIATE.value();

    private final CertificateRepository certificates;
    private final UserRepository users;
    private final StudentProfileService profiles;
    private final SkillService skillService;
    private final FileStore files;

    public CertificateService(CertificateRepository certificates, UserRepository users, StudentProfileService profiles,
                              SkillService skillService, CertificateFiles files) {
        this.certificates = certificates;
        this.users = users;
        this.profiles = profiles;
        this.skillService = skillService;
        this.files = files.store();
    }

    @Transactional(readOnly = true)
    public List<CertificateDto> mine(Long studentId) {
        return certificates.findByStudentIdOrderBySubmittedAtDesc(studentId).stream().map(this::toDto).toList();
    }

    @Transactional
    public CertificateDto submit(Long studentId, String name, String issuer, String credentialId, String url,
                                 LocalDate issued, LocalDate validUntil, String skillName, MultipartFile file) {
        if (blank(name)) throw ApiException.badRequest("Enter the certificate name.");
        if (blank(issuer)) throw ApiException.badRequest("Enter who issued the certificate.");
        if (name.length() > 150 || issuer.length() > 120) throw ApiException.badRequest("Name or issuer is too long.");
        if (!blank(url) && !url.trim().matches("(?i)^https?://\\S{3,290}$")) {
            throw ApiException.badRequest("The verification link must start with http:// or https://.");
        }
        if (issued != null && issued.isAfter(LocalDate.now())) throw ApiException.badRequest("The issue date can't be in the future.");
        if (issued != null && validUntil != null && validUntil.isBefore(issued)) {
            throw ApiException.badRequest("'Valid until' must be after the issue date.");
        }
        User student = users.findById(studentId).orElseThrow(() -> ApiException.notFound("User not found."));
        FileStore.Saved saved = files.save(file);

        Certificate c = new Certificate(student);
        c.setName(name.trim());
        c.setIssuer(issuer.trim());
        c.setCredentialId(blank(credentialId) ? null : credentialId.trim());
        c.setVerificationUrl(blank(url) ? null : url.trim());
        c.setIssueDate(issued);
        c.setValidUntil(validUntil);
        if (!blank(skillName)) c.setSkill(skillService.resolve(List.of(skillName)).iterator().next());
        c.setOriginalFileName(saved.originalName());
        c.setStoredFileName(saved.storedName());
        c.setContentType(saved.contentType());
        c.setFileSize(saved.size());
        return toDto(certificates.save(c));
    }

    /** A student can withdraw a certificate that hasn't been verified. */
    @Transactional
    public void delete(Long studentId, Long id) {
        Certificate c = owned(studentId, id);
        if (c.getStatus() == CertificateStatus.VERIFIED) {
            throw ApiException.badRequest("A verified certificate can't be deleted. Ask the placement cell if it is wrong.");
        }
        certificates.delete(c);
        files.deleteQuietly(c.getStoredFileName());
    }

    // ------------------------------------------------------------------ placement cell

    @Transactional(readOnly = true)
    public List<CertificateDto> forReview(CertificateStatus status) {
        List<Certificate> list = status == null ? certificates.findAllByOrderBySubmittedAtDesc()
                : certificates.findByStatusOrderBySubmittedAtAsc(status);
        return list.stream().map(this::toDto).toList();
    }

    @Transactional
    public CertificateDto review(Long id, CertificateStatus status, String note) {
        Certificate c = certificates.findById(id).orElseThrow(() -> ApiException.notFound("Certificate not found."));
        if (status == CertificateStatus.PENDING) throw ApiException.badRequest("Choose Verified or Rejected.");
        if (status == CertificateStatus.REJECTED && blank(note)) {
            throw ApiException.badRequest("Add a note saying why, so the student can fix it.");
        }
        c.review(status, blank(note) ? null : note.trim());
        if (status == CertificateStatus.VERIFIED && c.getSkill() != null) {
            StudentProfile p = profiles.profileOf(c.getStudent().getId());
            Skill skill = c.getSkill();
            if (p.getSkills().stream().noneMatch(s -> s.getId().equals(skill.getId()))) {
                Set<Skill> skills = new HashSet<>(p.getSkills());
                skills.add(skill);
                p.setSkills(skills);
            }
            if (p.levelOf(skill) < CERTIFICATE_LEVEL) p.setLevel(skill, CERTIFICATE_LEVEL);
            p.verify(skill, CERTIFICATE_LEVEL);
        }
        return toDto(c);
    }

    // ------------------------------------------------------------------ files

    @Transactional(readOnly = true)
    public Certificate forStudentFile(Long studentId, Long id) { return owned(studentId, id); }

    @Transactional(readOnly = true)
    public Certificate forAdminFile(Long id) {
        return certificates.findById(id).orElseThrow(() -> ApiException.notFound("Certificate not found."));
    }

    public Resource file(Certificate c) { return files.resource(c.getStoredFileName()); }

    @Transactional(readOnly = true)
    public long verifiedCount(Long studentId) {
        return certificates.countByStudentIdAndStatus(studentId, CertificateStatus.VERIFIED);
    }

    /** Names of a student's verified certificates, for recruiters. */
    @Transactional(readOnly = true)
    public List<String> verifiedNames(Long studentId) {
        return certificates.findByStudentIdOrderBySubmittedAtDesc(studentId).stream()
                .filter(c -> c.getStatus() == CertificateStatus.VERIFIED)
                .map(c -> c.getName() + " (" + c.getIssuer() + ")").toList();
    }

    private Certificate owned(Long studentId, Long id) {
        Certificate c = certificates.findById(id).orElseThrow(() -> ApiException.notFound("Certificate not found."));
        if (!c.getStudent().getId().equals(studentId)) throw ApiException.forbidden("This certificate belongs to another student.");
        return c;
    }

    private CertificateDto toDto(Certificate c) {
        StudentProfile p = null;
        try { p = profiles.profileOf(c.getStudent().getId()); } catch (RuntimeException ignored) { }
        return new CertificateDto(c.getId(), c.getName(), c.getIssuer(), c.getCredentialId(), c.getVerificationUrl(),
                c.getIssueDate(), c.getValidUntil(), c.getSkill() == null ? null : c.getSkill().getName(),
                c.getOriginalFileName(), c.getStatus(), c.getReviewNote(), c.getSubmittedAt(), c.getReviewedAt(),
                c.getStudent().getFullName(), p == null ? null : p.getBranch());
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
