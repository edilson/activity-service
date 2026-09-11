package com.example.activity.service;

import com.example.activity.repository.ActivitySourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

/** Each batch commits only after S3 accepts all uploaded files. Failed batches retain the original bytes. */
@Service
public class LegacyFileMigration {
    private final ActivitySourceRepository sources; private final ObjectStorageService storage;
    public LegacyFileMigration(ActivitySourceRepository sources, ObjectStorageService storage) { this.sources = sources; this.storage = storage; }
    @Scheduled(initialDelayString = "${activity.s3.migration-delay-ms:60000}", fixedDelayString = "${activity.s3.migration-delay-ms:60000}")
    @Transactional
    public void migrate() {
        for (var source : sources.legacyFiles(PageRequest.of(0, 10))) {
            source.storeFile(storage.upload(source.getActivityId(), "source", source.getOriginalFile(), source.getContentType()));
        }
    }
}
