package com.example.activity.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.web.*;
import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "activity.auth.mode", havingValue = "cognito", matchIfMissing = true)
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoSecurityConfig {
    @Bean
    public JwtDecoder cognitoDecoder(CognitoProperties properties) {
        String issuer = https(properties.issuer());
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(tokenValidator(issuer, properties.clientId()));
        return decoder;
    }
    static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String clientId) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), new CognitoTokenValidator(clientId));
    }
    @Bean
    public ClientRegistrationRepository cognitoClients(CognitoProperties properties) {
        String domain = https(properties.domain()), issuer = https(properties.issuer());
        var client = ClientRegistration.withRegistrationId("cognito").clientName("Amazon Cognito")
            .clientId(properties.clientId()).clientSecret(properties.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(properties.redirectUri()).scope("openid", "email", "profile")
            .authorizationUri(domain + "/oauth2/authorize").tokenUri(domain + "/oauth2/token")
            .userInfoUri(domain + "/oauth2/userInfo").userNameAttributeName("sub")
            .jwkSetUri(issuer + "/.well-known/jwks.json").issuerUri(issuer).build();
        return new InMemoryClientRegistrationRepository(client);
    }
    @Bean
    public SecurityFilterChain cognitoSecurity(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/oauth/*/callback", "/login/**", "/oauth2/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .defaultSuccessUrl("/api/me", true))
            .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(new BearerTokenAuthenticationEntryPoint(),
                request -> request.getServletPath().startsWith("/api/")))
            .build();
    }
    private String https(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Cognito issuer and domain must be HTTPS URLs");
        return value.replaceAll("/+$", "");
    }
}
