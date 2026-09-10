package com.example.activity.repository;

import com.example.activity.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    Optional<Activity> findByIdAndOwner(UUID id, String owner);
    Page<Activity> findByOwner(String owner, Pageable pageable);
}
