package com.example.activity.config;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** Cognito access tokens use client_id rather than the ID token's aud claim. */
public class CognitoTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final String clientId;
    public CognitoTokenValidator(String clientId) { this.clientId = clientId; }
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!"access".equals(token.getClaimAsString("token_use")) || !clientId.equals(token.getClaimAsString("client_id"))
            || token.getSubject() == null || token.getSubject().isBlank() || token.getExpiresAt() == null)
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "A Cognito user access token for this app client is required", null));
        return OAuth2TokenValidatorResult.success();
    }
}
