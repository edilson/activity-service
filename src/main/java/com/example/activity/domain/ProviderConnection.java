package com.example.activity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_connections", uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "provider"}))
public class ProviderConnection {
    @Id private UUID id;
    @Column(nullable = false) private String owner;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String providerUserId;
    @Column(nullable = false, columnDefinition = "text") private String encryptedTokens;
    @Column(nullable = false) private Instant expiresAt;
    protected ProviderConnection() {}
    public ProviderConnection(String owner, String provider) { this.id = UUID.randomUUID(); this.owner = owner; this.provider = provider; }
    public void update(String userId, String tokens, Instant expiry) { providerUserId = userId; encryptedTokens = tokens; expiresAt = expiry; }
    public String getProviderUserId() { return providerUserId; }
    public String getEncryptedTokens() { return encryptedTokens; }
    public Instant getExpiresAt() { return expiresAt; }
}
