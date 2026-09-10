package com.example.activity.service;

import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@ConditionalOnProperty(name = "activity.sqs.enabled", havingValue = "true")
public class SqsPublisher {
    private final SqsClient sqs; private final ServiceProperties properties; private final ObjectMapper json;
    public SqsPublisher(SqsClient sqs, ServiceProperties properties, ObjectMapper json) { this.sqs = sqs; this.properties = properties; this.json = json; }
    public void publish(OutboxEvent event) {
        String queue = event.getDistanceMeters() < 100000 ? properties.sqs().shortQueueUrl() : properties.sqs().longQueueUrl();
        try {
            String payload = json.writeValueAsString(Map.of("schemaVersion", 1, "eventId", event.getId(), "activityId", event.getActivityId(),
                "type", "ActivityImported", "distanceMeters", event.getDistanceMeters(), "createdAt", event.getCreatedAt().toString()));
            sqs.sendMessage(SendMessageRequest.builder().queueUrl(queue).messageBody(payload)
                .messageGroupId(UUID.nameUUIDFromBytes(event.getOwner().getBytes(StandardCharsets.UTF_8)).toString())
                .messageDeduplicationId(event.getId().toString()).build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize activity event", e); }
    }
}
