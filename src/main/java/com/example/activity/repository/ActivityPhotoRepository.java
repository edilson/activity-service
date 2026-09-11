package com.example.activity.repository;
import com.example.activity.domain.ActivityPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface ActivityPhotoRepository extends JpaRepository<ActivityPhoto, UUID> {
    Page<ActivityPhoto> findByActivityId(UUID activityId, Pageable page);
    Optional<ActivityPhoto> findByIdAndActivityId(UUID id, UUID activityId);
}
