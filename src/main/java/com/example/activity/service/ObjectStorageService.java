package com.example.activity.service;

import com.example.activity.config.S3Config;
import com.example.activity.domain.StoredFile;
import com.example.activity.ingestion.UpstreamException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import java.util.UUID;
import org.slf4j.*;

@Service
public class ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);
    private final S3Client s3;
    private final S3Config.Properties properties;
    public ObjectStorageService(S3Client s3, S3Config.Properties properties) { this.s3 = s3; this.properties = properties; }
    public StoredFile upload(UUID activityId, String category, byte[] bytes, String contentType) {
        String key = "activities/" + activityId + "/" + category + "/" + UUID.randomUUID();
        var file = new StoredFile(properties.bucket(), key, "s3://" + properties.bucket() + "/" + key, (long) bytes.length);
        // Register before PUT: a timeout may occur after S3 has accepted the object.
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) cleanup(file);
                }
            });
        try {
            s3.putObject(PutObjectRequest.builder().bucket(file.bucket()).key(key).contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256).build(), RequestBody.fromBytes(bytes));
            return file;
        } catch (RuntimeException e) { throw new UpstreamException("S3 upload failed; the activity file was not saved"); }
    }
    public byte[] download(StoredFile file) {
        try { return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(file.bucket()).key(file.objectKey()).build()).asByteArray(); }
        catch (RuntimeException e) { throw new UpstreamException("S3 download failed; retry later"); }
    }
    private void cleanup(StoredFile file) {
        try { s3.deleteObject(DeleteObjectRequest.builder().bucket(file.bucket()).key(file.objectKey()).build()); }
        catch (RuntimeException e) { log.error("S3 rollback cleanup failed for {}; reconcile unreferenced objects", file.url()); }
    }
}
