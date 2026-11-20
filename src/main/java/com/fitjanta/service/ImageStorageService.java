package com.fitjanta.service;

import com.fitjanta.config.WebConfig;
import com.fitjanta.model.Media;
import com.fitjanta.repository.MediaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Saves uploaded photos (posts, profile pictures) in the MySQL "media" table
 * and returns the URL path (e.g. /uploads/posts/abc.jpg) that is stored on the post/user.
 *
 * Storing images in the database means they survive cloud restarts and are
 * copied together with the rest of the data.
 */
@Service
@Order(0)
public class ImageStorageService implements CommandLineRunner {

    private static final long MAX_BYTES = 12L * 1024 * 1024;

    private final MediaRepository mediaRepository;
    private final Path legacyRoot;

    public ImageStorageService(MediaRepository mediaRepository, WebConfig webConfig) {
        this.mediaRepository = mediaRepository;
        this.legacyRoot = webConfig.uploadRoot();
    }

    /** Validates the image and saves it, e.g. save(file, "avatars") -> "/uploads/avatars/abc.jpg". */
    public String save(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a photo first.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Photo is too large (max 12 MB).");
        }
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        }
        String extension = detectImageType(bytes);
        if (extension == null) {
            throw new IllegalArgumentException("Only JPG, PNG or WEBP photos are supported.");
        }

        Media media = new Media();
        media.setFileName(UUID.randomUUID() + "." + extension);
        media.setFolder(folder);
        media.setContentType(contentTypeFor(extension));
        media.setData(bytes);
        mediaRepository.save(media);
        return "/uploads/" + folder + "/" + media.getFileName();
    }

    /** Deletes a previously saved image. */
    public void delete(String urlPath) {
        if (urlPath == null || !urlPath.matches("^/uploads/[a-z]+/[0-9a-f-]+\\.(jpg|png|webp)$")) {
            return;
        }
        String fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);
        mediaRepository.deleteById(fileName);
        try {
            Files.deleteIfExists(legacyRoot.resolve(urlPath.substring("/uploads/".length())));
        } catch (IOException ignored) {
            // old disk copy already gone
        }
    }

    /**
     * One-time move: photos uploaded before this change were saved as files
     * in the uploads/ folder. Copy them into MySQL so they keep working.
     */
    @Override
    public void run(String... args) throws IOException {
        if (!Files.isDirectory(legacyRoot)) return;
        int imported = 0;
        try (Stream<Path> files = Files.walk(legacyRoot)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = p.getFileName().toString();
                String folder = p.getParent().getFileName().toString();
                if (!name.matches("[0-9a-f-]+\\.(jpg|png|webp)") || mediaRepository.existsById(name)) continue;
                byte[] bytes = Files.readAllBytes(p);
                String ext = detectImageType(bytes);
                if (ext == null) continue;
                Media media = new Media();
                media.setFileName(name);
                media.setFolder(folder);
                media.setContentType(contentTypeFor(ext));
                media.setData(bytes);
                mediaRepository.save(media);
                imported++;
            }
        }
        if (imported > 0) {
            System.out.println("FitJanta: moved " + imported + " uploaded photo(s) into MySQL (media table).");
        }
    }

    static String contentTypeFor(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    /** Checks the file's first bytes so only real images are stored. */
    private static String detectImageType(byte[] h) {
        if (h.length < 12) return null;
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8) return "jpg";
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}
