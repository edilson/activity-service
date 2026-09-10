package com.example.activity.service;

import com.example.activity.TestSupport;
import com.example.activity.domain.OutboxEvent;
import com.example.activity.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class OutboxDispatcherTest {
    @Test void marksSuccessAndRetainsFailedEventsForRetry() {
        var repository = mock(OutboxRepository.class); var publisher = mock(SqsPublisher.class);
        var failed = new OutboxEvent(TestSupport.activity(1000)); var success = new OutboxEvent(TestSupport.activity(200000));
        when(repository.pending(any(), any())).thenReturn(List.of(failed, success));
        doThrow(new IllegalStateException("network failure")).when(publisher).publish(failed);
        new OutboxDispatcher(repository, publisher).dispatch();
        assertThat(success.isPublished()).isTrue(); assertThat(failed.isPublished()).isFalse();
        assertThat(failed.getAttempts()).isEqualTo(1); assertThat(failed.getNextAttemptAt()).isAfter(Instant.now());
        verify(publisher).publish(success);
    }
}
