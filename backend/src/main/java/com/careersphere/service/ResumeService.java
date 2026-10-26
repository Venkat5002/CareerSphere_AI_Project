package com.careersphere.service;

import com.careersphere.dto.StudentDtos.ResumeDto;
import com.careersphere.entity.Resume;
import com.careersphere.entity.User;
import com.careersphere.exception.ApiException;
import com.careersphere.repository.ResumeRepository;
import com.careersphere.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Stores resume files on disk and their metadata in the database.
 * Files get random names (UUID), so a user-supplied name can never point outside the folder.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Allowed extensions and the content type we serve them with. */
    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ResumeRepository resumes;
    private final UserRepository users;
    private final Path dir;

    public ResumeService(ResumeRepository resumes, UserRepository users,
                         @Value("${app.upload.dir}") String uploadDir) throws IOException {
        this.resumes = resumes;
        this.users = users;
        this.dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.dir);
        log.info("Resume files are stored in {}", this.dir);
    }

    @Transactional
    public ResumeDto upload(Long studentId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please choose a file to upload.");
        if (file.getSize() > MAX_BYTES) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                "File is too large. The maximum size is 5 MB.");

        String original = Paths.get(String.valueOf(file.getOriginalFilename())).getFileName().toString();
        String ext = extension(original);
        if (!TYPES.containsKey(ext)) throw ApiException.badRequest("Only PDF, DOC or DOCX files are allowed.");
        verifySignature(file, ext);

        String storedName = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the file", e);
        }

        User student = users.findById(studentId).orElseThrow(() -> ApiException.notFound("User not found."));
        Resume resume = resumes.findByStudentId(studentId).orElseGet(() -> new Resume(student));
        String previousFile = resume.getStoredFileName();

        resume.setOriginalFileName(original);
        resume.setStoredFileName(storedName);
        resume.setContentType(TYPES.get(ext));
        resume.setFileSize(file.getSize());
        resume.setUploadedAt(LocalDateTime.now());
        resumes.save(resume);

        if (previousFile != null) deleteQuietly(previousFile);   // replacing: remove the old file
        log.info("Resume uploaded for user {} -> {}", studentId, storedName);
        return toDto(resume);
    }

    @Transactional(readOnly = true)
    public ResumeDto get(Long studentId) {
        return resumes.findByStudentId(studentId).map(this::toDto).orElse(null);
    }

    @Transactional(readOnly = true)
    public Resume metadata(Long studentId) {
        return resumes.findByStudentId(studentId)
                .orElseThrow(() -> ApiException.notFound("No resume uploaded yet."));
    }

    public Resource file(Resume resume) {
        return new FileSystemResource(path(resume));
    }

    /** Location of the stored file on disk, checked to be inside the upload folder. */
    public Path path(Resume resume) {
        Path path = dir.resolve(resume.getStoredFileName()).normalize();
        if (!path.startsWith(dir) || !Files.exists(path)) {
            throw ApiException.notFound("The resume file is missing on the server. Please upload it again.");
        }
        return path;
    }

    @Transactional
    public void delete(Long studentId) {
        Resume resume = metadata(studentId);
        resumes.delete(resume);
        deleteQuietly(resume.getStoredFileName());
    }

    /** Checks the first bytes of the file, so a renamed .exe cannot pass as a .pdf. */
    private void verifySignature(MultipartFile file, String ext) {
        byte[] head = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int n = in.readNBytes(head, 0, head.length);
            head = Arrays.copyOf(head, n);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file.");
        }
        boolean ok = switch (ext) {
            case "pdf" -> startsWith(head, 0x25, 0x50, 0x44, 0x46);                          // %PDF
            case "docx" -> startsWith(head, 0x50, 0x4B, 0x03, 0x04);                         // ZIP (PK..)
            case "doc" -> startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);  // OLE2
            default -> false;
        };
        if (!ok) throw ApiException.badRequest("This file does not look like a real ." + ext + " document.");
    }

    private static boolean startsWith(byte[] data, int... sig) {
        if (data.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if ((data[i] & 0xFF) != sig[i]) return false;
        return true;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(dir.resolve(storedName));
        } catch (IOException e) {
            log.warn("Could not delete old resume file {}", storedName, e);
        }
    }

    private ResumeDto toDto(Resume r) {
        return new ResumeDto(r.getOriginalFileName(), r.getContentType(), r.getFileSize(), r.getUploadedAt());
    }
}
