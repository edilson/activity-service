package com.example.activity.service;

import com.example.activity.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.slf4j.*;
import java.time.Instant;

@Service
@ConditionalOnProperty(name = "activity.sqs.enabled", havingValue = "true")
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxRepository outbox; private final SqsPublisher publisher;
    public OutboxDispatcher(OutboxRepository outbox, SqsPublisher publisher) { this.outbox = outbox; this.publisher = publisher; }
    @Scheduled(fixedDelayString = "${activity.sqs.poll-delay-ms:5000}")
    @Transactional
    public void dispatch() {
        for (var event : outbox.pending(Instant.now(), PageRequest.of(0, 20))) {
            try { publisher.publish(event); event.markPublished(); }
            catch (RuntimeException e) {
                event.retryLater();
                log.warn("SQS delivery failed for event {}; attempt {} will be retried", event.getId(), event.getAttempts());
            }
        }
    }
}
