package com.example.activity.config;

import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.security.interfaces.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class CognitoTokenValidatorTest {
    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_pool";
    private static com.nimbusds.jose.jwk.RSAKey key;
    private NimbusJwtDecoder decoder;
    @BeforeAll static void keys() throws Exception { key = new RSAKeyGenerator(2048).generate(); }
    @BeforeEach void setup() throws Exception {
        decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(CognitoSecurityConfig.tokenValidator(ISSUER, "app-client"));
    }
    private String token(String issuer, String client, String use, Instant expiry, String subject) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).claim("client_id", client).claim("token_use", use)
            .issueTime(Date.from(Instant.now().minusSeconds(10)));
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build()); signed.sign(new RSASSASigner(key)); return signed.serialize();
    }
    @Test void acceptsSignedAccessTokenWithSubject() throws Exception {
        assertThat(decoder.decode(token(ISSUER, "app-client", "access", Instant.now().plusSeconds(300), "user-sub")).getSubject()).isEqualTo("user-sub");
    }
    @Test void rejectsWrongIssuerClientIdTokenExpiredAndMissingSubject() throws Exception {
        for (String token : new String[]{token("https://wrong.test", "app-client", "access", Instant.now().plusSeconds(300), "user"),
            token(ISSUER, "wrong-client", "access", Instant.now().plusSeconds(300), "user"),
            token(ISSUER, "app-client", "id", Instant.now().plusSeconds(300), "user"),
            token(ISSUER, "app-client", "access", Instant.now().minusSeconds(300), "user"),
            token(ISSUER, "app-client", "access", Instant.now().plusSeconds(300), null),
            token(ISSUER, "app-client", "access", null, "user")})
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsSignatureFromAnotherKey() throws Exception {
        var otherDecoder = NimbusJwtDecoder.withPublicKey(new RSAKeyGenerator(2048).generate().toRSAPublicKey()).build();
        String token = token(ISSUER, "app-client", "access", Instant.now().plusSeconds(300), "user");
        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void configuresCognitoEndpointsWithoutStartupNetwork() {
        var properties = new CognitoProperties(ISSUER, "app-client", "secret", "https://pool.auth.us-east-1.amazoncognito.com", "http://localhost:8080/login/oauth2/code/cognito");
        var config = new CognitoSecurityConfig();
        var client = config.cognitoClients(properties).findByRegistrationId("cognito");
        assertThat(client.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
        assertThat(config.cognitoDecoder(properties)).isNotNull();
    }
}
