package com.example.activity.service;

import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.*;
import com.example.activity.ingestion.UpstreamException;
import com.example.activity.repository.ProviderConnectionRepository;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class OAuthService {
    public record Pending(String provider, String owner, String state, String verifier, Instant expiresAt) implements java.io.Serializable {}
    public record Authorization(URI url, Pending pending) {}
    private final ServiceProperties properties; private final RestClient http; private final TokenCipher cipher;
    private final ProviderConnectionRepository connections; private final ObjectMapper json;
    public OAuthService(ServiceProperties properties, RestClient http, TokenCipher cipher, ProviderConnectionRepository connections, ObjectMapper json) {
        this.properties = properties; this.http = http; this.cipher = cipher; this.connections = connections; this.json = json;
    }
    public Authorization authorize(String provider, String owner) {
        var config = provider(provider); cipher.validateKey();
        String state = random(), verifier = random();
        var url = UriComponentsBuilder.fromUri(config.authorizationUri()).queryParam("response_type", "code")
            .queryParam("client_id", config.clientId()).queryParam("redirect_uri", callback(provider)).queryParam("state", state);
        if (provider.equals("garmin")) url.queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256");
        return new Authorization(url.build().encode().toUri(), new Pending(provider, owner, state, verifier, Instant.now().plusSeconds(600)));
    }
    @Transactional
    public void complete(String provider, String state, String code, Pending pending) {
        if (pending == null || state == null || !pending.provider().equals(provider) || !pending.expiresAt().isAfter(Instant.now())
            || !MessageDigest.isEqual(pending.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8)))
            throw new InvalidActivityException("Invalid or expired OAuth state; start authorization again");
        if (code == null || code.isBlank()) throw new InvalidActivityException("Authorization was declined or no code was returned");
        var config = provider(provider);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code"); form.add("code", code); form.add("redirect_uri", callback(provider));
        if (provider.equals("garmin")) form.add("code_verifier", pending.verifier());
        JsonNode tokens = tokens(provider, form);
        String access = tokens.path("access_token").asText(); String userId;
        try {
            if (provider.equals("polar")) {
                userId = tokens.path("x_user_id").asText();
                if (userId.isBlank()) throw new UpstreamException("Polar did not return a user ID");
                try {
                    http.post().uri(config.userUri()).headers(h -> h.setBearerAuth(access)).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("member-id", pending.owner())).retrieve().toBodilessEntity();
                } catch (HttpClientErrorException.Conflict alreadyRegistered) { /* Polar user has already registered with this client. */ }
            } else {
                var user = http.get().uri(config.userUri()).headers(h -> h.setBearerAuth(access)).retrieve().body(JsonNode.class);
                userId = user == null ? "" : user.path("userId").asText();
                if (userId.isBlank()) throw new UpstreamException("Garmin did not return a user ID");
            }
        } catch (RestClientException e) { throw new UpstreamException("Provider user registration or lookup failed; reconnect the provider"); }
        var connection = connections.findByOwnerAndProvider(pending.owner(), provider).orElseGet(() -> new ProviderConnection(pending.owner(), provider));
        store(connection, pending.owner(), provider, userId, tokens);
    }
    @Transactional
    public void refreshGarmin(String owner) {
        var connection = connections.findByOwnerAndProvider(owner, "garmin").orElseThrow(() -> new InvalidActivityException("Garmin is not connected"));
        try {
            var old = json.readTree(cipher.decrypt(connection.getEncryptedTokens(), owner + ":garmin"));
            String refresh = old.path("refresh_token").asText();
            if (refresh.isBlank()) throw new InvalidActivityException("No refresh token; reconnect Garmin");
            var form = new LinkedMultiValueMap<String, String>(); form.add("grant_type", "refresh_token"); form.add("refresh_token", refresh);
            var updated = tokens("garmin", form);
            if (updated.path("refresh_token").asText().isBlank()) throw new UpstreamException("Garmin did not return a rotated refresh token");
            store(connection, owner, "garmin", connection.getProviderUserId(), updated);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Stored token data is invalid"); }
    }
    private void store(ProviderConnection connection, String owner, String provider, String userId, JsonNode tokens) {
        long seconds = tokens.path("expires_in").asLong(31536000);
        if (seconds <= 0) throw new UpstreamException("Provider returned invalid token expiry");
        connection.update(userId, cipher.encrypt(tokens.toString(), owner + ":" + provider), Instant.now().plusSeconds(seconds));
        connections.save(connection);
    }
    private JsonNode tokens(String provider, LinkedMultiValueMap<String, String> form) {
        var config = provider(provider);
        if (provider.equals("garmin")) { form.add("client_id", config.clientId()); form.add("client_secret", config.clientSecret()); }
        try {
            JsonNode tokens = http.post().uri(config.tokenUri()).headers(h -> { if (provider.equals("polar")) h.setBasicAuth(config.clientId(), config.clientSecret()); })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(JsonNode.class);
            if (tokens == null || tokens.path("access_token").asText().isBlank()) throw new UpstreamException("Provider did not return an access token");
            return tokens;
        } catch (RestClientException e) { throw new UpstreamException("OAuth token exchange failed; reconnect the provider"); }
    }
    private ServiceProperties.Provider provider(String name) {
        if (!Set.of("polar", "garmin").contains(name)) throw new InvalidActivityException("Unknown OAuth provider");
        var p = properties.oauth().providers().get(name);
        if (p == null || p.clientId() == null || p.clientId().isBlank() || p.clientSecret() == null || p.clientSecret().isBlank())
            throw new UpstreamException("OAuth provider is not configured");
        return p;
    }
    private String callback(String provider) { return properties.oauth().publicBaseUrl().replaceAll("/+$", "") + "/oauth/" + provider + "/callback"; }
    private String random() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String challenge(String verifier) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
