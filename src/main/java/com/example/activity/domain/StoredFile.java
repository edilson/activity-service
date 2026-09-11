package com.example.activity.domain;

import jakarta.persistence.*;

@Embeddable
public record StoredFile(@Column(name = "storage_bucket") String bucket,
                         @Column(name = "object_key", columnDefinition = "text") String objectKey,
                         @Column(name = "object_url", columnDefinition = "text") String url,
                         @Column(name = "file_size") Long sizeBytes) {}
