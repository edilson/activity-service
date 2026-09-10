package com.example.activity.service;

import com.example.activity.TestSupport;
import com.example.activity.domain.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SqsPublisherTest {
    @ParameterizedTest @CsvSource({"99999.99,short", "100000,long", "100000.01,long"})
    void routesBoundariesWithStableFifoIdentifiers(double distance, String queue) throws Exception {
        var sqs = mock(SqsClient.class); var json = new ObjectMapper();
        var publisher = new SqsPublisher(sqs, TestSupport.properties(), json); var event = new OutboxEvent(TestSupport.activity(distance));
        publisher.publish(event); publisher.publish(event);
        var captor = ArgumentCaptor.forClass(SendMessageRequest.class); verify(sqs, times(2)).sendMessage(captor.capture());
        var request = captor.getValue(); assertThat(request.queueUrl()).isEqualTo("https://sqs.test/" + queue + ".fifo");
        assertThat(request.messageDeduplicationId()).isEqualTo(event.getId().toString()); assertThat(request.messageGroupId()).isNotBlank();
        assertThat(captor.getAllValues().getFirst()).isEqualTo(request);
        assertThat(json.readTree(request.messageBody()).path("activityId").asText()).isEqualTo(event.getActivityId().toString());
    }
}
