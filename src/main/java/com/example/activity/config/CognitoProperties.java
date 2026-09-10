package com.example.activity.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("activity.cognito")
public record CognitoProperties(@NotBlank String issuer, @NotBlank String clientId, @NotBlank String clientSecret,
                                @NotBlank String domain, @NotBlank String redirectUri) {}
