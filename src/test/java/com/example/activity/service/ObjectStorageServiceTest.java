package com.example.activity.service;

import com.example.activity.config.S3Config;
import com.example.activity.ingestion.UpstreamException;
import com.example.activity.domain.StoredFile;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ObjectStorageServiceTest {
    S3Client s3 = mock(S3Client.class);
    ObjectStorageService storage = new ObjectStorageService(s3, new S3Config.Properties("test-bucket", "us-east-1", null));
    @AfterEach void clear() { if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization(); }
    @Test void uploadsEncryptedPrivateObjectAndReturnsStablePath() throws Exception {
        byte[] bytes = {0, -1, 5}; var id = UUID.randomUUID();
        var file = storage.upload(id, "source", bytes, "application/octet-stream");
        var request = ArgumentCaptor.forClass(PutObjectRequest.class); var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().acl()).isNull();
        assertThat(file.url()).startsWith("s3://test-bucket/activities/" + id + "/source/");
        assertThat(file.sizeBytes()).isEqualTo(3);
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).containsExactly(bytes);
    }
    @Test void deletesUploadWhenTransactionRollsBackButNotOnCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var file = storage.upload(UUID.randomUUID(), "photos", new byte[]{1}, "image/png");
        var callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
        callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED); verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket(file.bucket()).key(file.objectKey()).build());
    }
    @Test void retainsCleanupHookWhenUploadTimesOutAndSanitizesFailure() {
        TransactionSynchronizationManager.initSynchronization();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new IllegalStateException("provider internals"));
        assertThatThrownBy(() -> storage.upload(UUID.randomUUID(), "source", new byte[]{1}, "image/png")).isInstanceOf(UpstreamException.class)
            .hasMessageNotContaining("provider internals");
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }
    @Test void downloadsByPersistedBucketAndKey() {
        var file = new StoredFile("old-bucket", "original/key", "s3://old-bucket/original/key", 2L);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1, 2}));
        assertThat(storage.download(file)).containsExactly(1, 2);
        verify(s3).getObjectAsBytes(GetObjectRequest.builder().bucket("old-bucket").key("original/key").build());
    }
}
