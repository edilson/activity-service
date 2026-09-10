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
    @Autowired ProviderConnectionRepository connections;
    @Autowired PlatformTransactionManager transactionManager;
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
        var response = json.readTree("{\"data\":{\"heartRate\":157,\"unknownField\":[1,2]},\"rawHtml\":\"<p>ride</p>\"}");
        var data = new ActivityData(120000.0, 3600.0, 123.0, java.util.List.of(new RoutePoint(-3.7, -38.5), new RoutePoint(-3.8, -38.6)));
        var saved = service.save("source-owner", "FIT", data, new SourceData("ride.fit", "application/octet-stream", binary, "https://source.test", response));
        var source = service.source("source-owner", saved.getId());
        assertThat(source.getOriginalFile()).containsExactly(binary);
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
}
