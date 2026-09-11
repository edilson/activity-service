package com.example.activity.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "activity_sources")
public class ActivitySource {
    @Id private UUID activityId;
    private String filename;
    private String contentType;
    @JsonIgnore @Column(columnDefinition = "bytea") private byte[] originalFile;
    @Embedded private StoredFile file;
    @Column(columnDefinition = "text") private String sourceUrl;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private JsonNode providerResponse;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode route;
    protected ActivitySource() {}
    public ActivitySource(UUID activityId, SourceData source, JsonNode route) {
        this.activityId = activityId; this.filename = source.filename(); this.contentType = source.contentType();
        this.sourceUrl = source.sourceUrl(); this.providerResponse = source.providerResponse(); this.route = route;
    }
    public UUID getActivityId() { return activityId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public byte[] getOriginalFile() { return originalFile == null ? null : originalFile.clone(); }
    public StoredFile getFile() { return file; }
    public void storeFile(StoredFile file) { this.file = file; this.originalFile = null; }
    public String getSourceUrl() { return sourceUrl; }
    public JsonNode getProviderResponse() { return providerResponse; }
    public JsonNode getRoute() { return route; }
}
