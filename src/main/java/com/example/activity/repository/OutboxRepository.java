package com.example.activity.repository;

import com.example.activity.domain.OutboxEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.published = false and e.nextAttemptAt <= :now order by e.createdAt, e.id")
    List<OutboxEvent> pending(Instant now, Pageable page);
}
