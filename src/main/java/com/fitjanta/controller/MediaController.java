package com.fitjanta.controller;

import com.fitjanta.repository.MediaRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Serves photos stored in the MySQL "media" table at /uploads/{folder}/{fileName}.
 */
@RestController
public class MediaController {

    private final MediaRepository mediaRepository;

    public MediaController(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @GetMapping("/uploads/{folder}/{fileName:.+}")
    public ResponseEntity<byte[]> image(@PathVariable String folder, @PathVariable String fileName) {
        return mediaRepository.findById(fileName)
                .filter(m -> m.getFolder().equals(folder))
                .map(m -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(m.getContentType()))
                        // file names are unique (UUID), so they can be cached for a long time
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                        .body(m.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}
