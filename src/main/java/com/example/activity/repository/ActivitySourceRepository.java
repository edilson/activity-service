package com.example.activity.repository;
import com.example.activity.domain.ActivitySource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ActivitySourceRepository extends JpaRepository<ActivitySource, UUID> {}
