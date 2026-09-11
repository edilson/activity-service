package com.example.activity.repository;
import com.example.activity.domain.ActivitySource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ActivitySourceRepository extends JpaRepository<ActivitySource, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from ActivitySource s where s.originalFile is not null order by s.activityId")
    java.util.List<ActivitySource> legacyFiles(org.springframework.data.domain.Pageable page);
}
