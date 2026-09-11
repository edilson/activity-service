package com.example.activity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_photos")
public class ActivityPhoto {
    @Id private UUID id;
    @Column(nullable = false) private UUID activityId;
    @Column(nullable = false) private String filename;
    @Column(nullable = false) private String contentType;
    @Embedded private StoredFile file;
    @Column(nullable = false) private Instant createdAt;
    protected ActivityPhoto() {}
    public ActivityPhoto(UUID activityId, String filename, String contentType, StoredFile file) {
        this.id = UUID.randomUUID(); this.activityId = activityId; this.filename = filename;
        this.contentType = contentType; this.file = file; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public StoredFile getFile() { return file; }
    public Instant getCreatedAt() { return createdAt; }
}
