package com.example.activity.repository;

import com.example.activity.domain.ProviderConnection;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ProviderConnectionRepository extends JpaRepository<ProviderConnection, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderConnection> findByOwnerAndProvider(String owner, String provider);
}
