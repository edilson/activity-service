package com.example.activity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(nullable = false) private UUID activityId;
    @Column(nullable = false) private String owner;
    @Column(nullable = false) private double distanceMeters;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private boolean published;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    protected OutboxEvent() {}
    public OutboxEvent(Activity activity) {
        id = UUID.randomUUID(); activityId = activity.getId(); owner = activity.getOwner();
        distanceMeters = activity.getDistanceMeters(); createdAt = Instant.now(); nextAttemptAt = createdAt;
    }
    public void markPublished() { published = true; }
    public void retryLater() { attempts++; nextAttemptAt = Instant.now().plusSeconds(Math.min(3600, 1L << Math.min(attempts, 12))); }
    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public String getOwner() { return owner; }
    public double getDistanceMeters() { return distanceMeters; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isPublished() { return published; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
