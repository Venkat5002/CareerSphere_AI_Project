package com.careersphere.service;

import com.careersphere.exception.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Saves uploaded files (certificates, introduction videos) in a folder with random names,
 * after checking the size, the extension and the first bytes of the file.
 */
public class FileStore {

    /** What an accepted file type looks like: its content type and a check on its first 12 bytes. */
    public record Kind(String contentType, Predicate<byte[]> signature) {}

    public static final Predicate<byte[]> PDF = h -> starts(h, 0x25, 0x50, 0x44, 0x46);
    public static final Predicate<byte[]> PNG = h -> starts(h, 0x89, 0x50, 0x4E, 0x47);
    public static final Predicate<byte[]> JPEG = h -> starts(h, 0xFF, 0xD8, 0xFF);
    public static final Predicate<byte[]> WEBM = h -> starts(h, 0x1A, 0x45, 0xDF, 0xA3);
    /** MP4 and QuickTime files say "ftyp" in bytes 4 to 7. */
    public static final Predicate<byte[]> ISO_MEDIA = h -> h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';

    private final Path dir;
    private final long maxBytes;
    private final Map<String, Kind> kinds;
    private final String allowedText;

    public FileStore(String folder, long maxBytes, Map<String, Kind> kinds, String allowedText) {
        this.dir = Paths.get(folder).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.kinds = kinds;
        this.allowedText = allowedText;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload folder " + dir, e);
        }
    }

    /** A checked, saved file. */
    public record Saved(String storedName, String originalName, String contentType, long size) {}

    public Saved save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please choose a file to upload.");
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File is too large. The maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        String original = Paths.get(String.valueOf(file.getOriginalFilename())).getFileName().toString();
        String ext = extension(original);
        Kind kind = kinds.get(ext);
        if (kind == null) throw ApiException.badRequest("Only " + allowedText + " files are allowed.");
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            head = Arrays.copyOf(head, in.readNBytes(head, 0, head.length));
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file.");
        }
        if (!kind.signature().test(head)) throw ApiException.badRequest("This file does not look like a real ." + ext + " file.");

        String stored = UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the file", e);
        }
        return new Saved(stored, original.length() > 200 ? original.substring(0, 200) : original, kind.contentType(), file.getSize());
    }

    public Resource resource(String storedName) {
        Path path = dir.resolve(storedName).normalize();
        if (!path.startsWith(dir) || !Files.exists(path)) throw ApiException.notFound("The file is missing on the server. Please upload it again.");
        return new FileSystemResource(path);
    }

    /** For seed data: writes bytes under a new random name. */
    public String write(byte[] bytes, String ext) {
        String stored = UUID.randomUUID() + "." + ext;
        try {
            Files.write(dir.resolve(stored), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the file", e);
        }
        return stored;
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null) return;
        try {
            Files.deleteIfExists(dir.resolve(storedName));
        } catch (IOException ignored) {
            // an orphaned file is harmless
        }
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private static boolean starts(byte[] data, int... sig) {
        if (data.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if ((data[i] & 0xFF) != sig[i]) return false;
        return true;
    }
}
