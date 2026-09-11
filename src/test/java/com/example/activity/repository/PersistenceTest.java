package com.example.activity.repository;

import com.example.activity.TestSupport;
import com.example.activity.domain.*;
import com.example.activity.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:persistence;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.security.user.password=test-password", "activity.sqs.enabled=false"})
class PersistenceTest {
    @Autowired ActivityService service;
    @Autowired ActivityRepository activities;
    @Autowired OutboxRepository outbox;
    @Autowired ActivitySourceRepository sources;
    @Autowired ActivityPhotoRepository photos;
    @Autowired com.example.activity.service.LegacyFileMigration migration;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ProviderConnectionRepository connections;
    @Autowired PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.activity.service.ObjectStorageService storage;
    @Test void migratesAndPersistsActivityAndOutbox() {
        String owner = UUID.randomUUID().toString(); var saved = service.save(owner, "GPX", TestSupport.data(100000));
        assertThat(service.get(owner, saved.getId()).getDistanceMeters()).isEqualTo(100000);
        assertThat(activities.findByIdAndOwner(saved.getId(), "another-rider")).isEmpty();
        assertThat(activities.findByOwner(owner, PageRequest.of(0, 20))).hasSize(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var pending = outbox.pending(Instant.now().plusSeconds(1), PageRequest.of(0, 100));
            var event = pending.stream().filter(e -> e.getActivityId().equals(saved.getId())).findFirst().orElseThrow();
            event.markPublished();
        });
        assertThat(outbox.findAll().stream().filter(e -> e.getActivityId().equals(saved.getId())).findFirst().orElseThrow().isPublished()).isTrue();
    }
    @Test void rollsBackActivityAndEventTogether() {
        long activityCount = activities.count(), eventCount = outbox.count(), sourceCount = sources.count();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.save("rollback-rider", "FIT", TestSupport.data(1000));
            throw new IllegalStateException("transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(activities.count()).isEqualTo(activityCount); assertThat(outbox.count()).isEqualTo(eventCount);
        assertThat(sources.count()).isEqualTo(sourceCount);
    }
    @Test void retainsBinaryAndAdditionalProviderDataWithoutExposingOtherOwners() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        byte[] binary = {0, 1, -1, 42};
        var stored = new StoredFile("test-bucket", "source/key", "s3://test-bucket/source/key", 4L);
        org.mockito.Mockito.when(storage.upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("source"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(stored);
        org.mockito.Mockito.when(storage.download(stored)).thenReturn(binary);
        var response = json.readTree("{\"data\":{\"heartRate\":157,\"unknownField\":[1,2]},\"rawHtml\":\"<p>ride</p>\"}");
        var data = new ActivityData(120000.0, 3600.0, 123.0, java.util.List.of(new RoutePoint(-3.7, -38.5), new RoutePoint(-3.8, -38.6)));
        var saved = service.save("source-owner", "FIT", data, new SourceData("ride.fit", "application/octet-stream", binary, "https://source.test", response));
        var source = service.source("source-owner", saved.getId());
        assertThat(source.getOriginalFile()).isNull();
        assertThat(source.getFile()).isEqualTo(stored);
        assertThat(service.download("source-owner", saved.getId())).containsExactly(binary);
        assertThat(source.getProviderResponse()).isEqualTo(response);
        assertThat(source.getRoute().size()).isEqualTo(2);
        assertThat(source.getFilename()).isEqualTo("ride.fit");
        assertThat(source.getSourceUrl()).isEqualTo("https://source.test");
        assertThat(json.valueToTree(source).has("originalFile")).isFalse();
        assertThatThrownBy(() -> service.source("other-owner", saved.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void storesAndLocksProviderConnectionByOwnerAndProvider() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var connection = new ProviderConnection("oauth-rider", "garmin"); connection.update("provider-id", "encrypted", Instant.now());
            connections.saveAndFlush(connection);
            assertThat(connections.findByOwnerAndProvider("oauth-rider", "garmin")).isPresent();
            assertThat(connections.findByOwnerAndProvider("wrong-owner", "garmin")).isEmpty();
        });
    }
    @Test void migratesLegacyBytesOnlyAfterSuccessfulS3Upload() {
        var saved = service.save("legacy-owner", "GPX", TestSupport.data(1000));
        byte[] old = {1, 2, 3};
        jdbc.update("update activity_sources set original_file = ?, content_type = ? where activity_id = ?", old, "application/gpx+xml", saved.getId());
        org.mockito.Mockito.when(storage.upload(org.mockito.ArgumentMatchers.eq(saved.getId()), org.mockito.ArgumentMatchers.eq("source"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new com.example.activity.ingestion.UpstreamException("S3 unavailable"));
        assertThatThrownBy(() -> migration.migrate()).isInstanceOf(com.example.activity.ingestion.UpstreamException.class);
        assertThat(sources.findById(saved.getId()).orElseThrow().getOriginalFile()).containsExactly(old);
        var file = new StoredFile("bucket", "key", "s3://bucket/key", 3L);
        org.mockito.Mockito.when(storage.upload(org.mockito.ArgumentMatchers.eq(saved.getId()), org.mockito.ArgumentMatchers.eq("source"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(file);
        migration.migrate();
        var migrated = sources.findById(saved.getId()).orElseThrow();
        assertThat(migrated.getFile()).isEqualTo(file); assertThat(migrated.getOriginalFile()).isNull();
    }
    @Test void persistsOptionalNameAndPhotoPaths() {
        var activity = service.save("photo-owner", "GPX", TestSupport.data(1000), new SourceData(null, null, null, null, null), "Evening ride");
        assertThat(service.get("photo-owner", activity.getId()).getName()).isEqualTo("Evening ride");
        var file = new StoredFile("bucket", "photo/key", "s3://bucket/photo/key", 123L);
        var photo = photos.saveAndFlush(new ActivityPhoto(activity.getId(), "ride.jpg", "image/jpeg", file));
        assertThat(photos.findByIdAndActivityId(photo.getId(), activity.getId()).orElseThrow().getFile()).isEqualTo(file);
        assertThat(photos.findByIdAndActivityId(photo.getId(), UUID.randomUUID())).isEmpty();
        assertThat(photos.findByActivityId(activity.getId(), PageRequest.of(0, 20))).hasSize(1);
    }
}
