package com.fitjanta.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Location of the old uploads/ folder. Photos are now stored in MySQL
 * (see ImageStorageService); this folder is only read once to move
 * older photos into the database.
 */
@Configuration
public class WebConfig {

    private final Path uploadRoot;

    public WebConfig(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path uploadRoot() {
        return uploadRoot;
    }
}
