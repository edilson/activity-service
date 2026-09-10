package com.example.activity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {
    @Id private UUID id;
    @Column(nullable = false) private String owner;
    @Column(nullable = false) private String source;
    @Column(nullable = false) private double distanceMeters;
    @Column(nullable = false) private double durationSeconds;
    @Column(nullable = false) private double elevationGainMeters;
    @Column(columnDefinition = "text") private String polyline;
    @Column(nullable = false) private Instant createdAt;
    protected Activity() {}
    public Activity(String owner, String source, ActivityData data, String polyline) {
        this.id = UUID.randomUUID(); this.owner = owner; this.source = source;
        this.distanceMeters = data.distanceMeters(); this.durationSeconds = data.durationSeconds();
        this.elevationGainMeters = data.elevationGainMeters(); this.polyline = polyline; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getOwner() { return owner; }
    public String getSource() { return source; }
    public double getDistanceMeters() { return distanceMeters; }
    public double getDurationSeconds() { return durationSeconds; }
    public double getElevationGainMeters() { return elevationGainMeters; }
    public String getPolyline() { return polyline; }
    public Instant getCreatedAt() { return createdAt; }
}
