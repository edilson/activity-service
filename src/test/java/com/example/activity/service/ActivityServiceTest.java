package com.example.activity.service;

import com.example.activity.TestSupport;
import com.example.activity.domain.*;
import com.example.activity.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ActivityServiceTest {
    ActivityRepository repository = mock(ActivityRepository.class); OutboxRepository outbox = mock(OutboxRepository.class);
    ActivitySourceRepository sources = mock(ActivitySourceRepository.class);
    ActivityService service = new ActivityService(repository, outbox, new PolylineService(), sources, new com.fasterxml.jackson.databind.ObjectMapper());
    @Test void savesActivityAndMatchingOutboxEvent() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var activity = service.save("rider", "FIT", TestSupport.data(100000));
        var captor = ArgumentCaptor.forClass(OutboxEvent.class); verify(outbox).save(captor.capture());
        assertThat(captor.getValue().getActivityId()).isEqualTo(activity.getId());
        assertThat(captor.getValue().getDistanceMeters()).isEqualTo(100000); assertThat(captor.getValue().isPublished()).isFalse();
    }
    @Test void enforcesOwnerOnLookup() {
        UUID id = UUID.randomUUID(); when(repository.findByIdAndOwner(id, "rider")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("rider", id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(repository).findByIdAndOwner(id, "rider");
    }
    @Test void clampsNegativePages() { service.list("rider", -1); verify(repository).findByOwner(eq("rider"), argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 20)); }
}
