package com.careersphere.config;

import com.careersphere.service.FileStore;
import com.careersphere.service.QuestionBank;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.Map;

/** Where certificates and introduction videos are stored, what is accepted, and the assessment questions. */
@Configuration
public class UploadsConfig {

    public static final long CERTIFICATE_MAX = 5L * 1024 * 1024;
    public static final long VIDEO_MAX = 25L * 1024 * 1024;

    /** Certificates: PDF or image, 5 MB. Stored next to the resumes folder. */
    @Bean
    public CertificateFiles certificateFiles(@Value("${app.upload.dir}") String uploadDir) {
        return new CertificateFiles(new FileStore(sibling(uploadDir, "certificates"), CERTIFICATE_MAX, Map.of(
                "pdf", new FileStore.Kind("application/pdf", FileStore.PDF),
                "png", new FileStore.Kind("image/png", FileStore.PNG),
                "jpg", new FileStore.Kind("image/jpeg", FileStore.JPEG),
                "jpeg", new FileStore.Kind("image/jpeg", FileStore.JPEG)), "PDF, PNG or JPG"));
    }

    /** Introduction videos: WebM (recorded in the browser), MP4 or MOV, 25 MB. */
    @Bean
    public VideoFiles videoFiles(@Value("${app.upload.dir}") String uploadDir) {
        return new VideoFiles(new FileStore(sibling(uploadDir, "videos"), VIDEO_MAX, Map.of(
                "webm", new FileStore.Kind("video/webm", FileStore.WEBM),
                "mp4", new FileStore.Kind("video/mp4", FileStore.ISO_MEDIA),
                "mov", new FileStore.Kind("video/quicktime", FileStore.ISO_MEDIA)), "WebM, MP4 or MOV"));
    }

    @Bean
    public QuestionBank questionBank(ObjectMapper mapper) {
        return QuestionBank.load(mapper);
    }

    /** uploads/resumes -> uploads/<name> */
    static String sibling(String uploadDir, String name) {
        java.nio.file.Path p = Paths.get(uploadDir).toAbsolutePath().normalize();
        return (p.getParent() == null ? p.resolve(name) : p.getParent().resolve(name)).toString();
    }

    /** Typed wrappers so Spring can tell the two stores apart. */
    public record CertificateFiles(FileStore store) {}
    public record VideoFiles(FileStore store) {}
}
