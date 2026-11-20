package com.fitjanta.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An uploaded image (post photo or profile picture) stored inside MySQL,
 * so photos survive cloud restarts and move with the database.
 * Served at /uploads/{folder}/{fileName}.
 */
@Entity
@Table(name = "media")
public class Media {

    // File name, e.g. "3f2a...-...-....jpg"
    @Id
    @Column(length = 64)
    private String fileName;

    // "posts" or "avatars"
    @Column(length = 20)
    private String folder;

    @Column(length = 40)
    private String contentType;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] data;

    private long sizeBytes;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; this.sizeBytes = data == null ? 0 : data.length; }

    public long getSizeBytes() { return sizeBytes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
