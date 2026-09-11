package com.example.activity.api;

import com.example.activity.config.CognitoSecurityConfig;
import com.example.activity.service.*;
import com.example.activity.TestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.http.MediaType;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = {UserController.class, ActivityController.class}, properties = {
    "activity.auth.mode=cognito", "activity.cognito.issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_pool",
    "activity.cognito.client-id=client", "activity.cognito.client-secret=secret", "activity.cognito.domain=https://pool.auth.us-east-1.amazoncognito.com",
    "activity.cognito.redirect-uri=http://localhost:8080/login/oauth2/code/cognito"})
@Import(CognitoSecurityConfig.class)
class CognitoSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean(name = "cognitoDecoder") JwtDecoder decoder;
    @MockitoBean IngestionService ingestion;
    @MockitoBean ActivityService activities;
    @BeforeEach void setup() {
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token").header("alg", "RS256").subject("cognito-sub")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("token_use", "access").claim("client_id", "client").build());
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid signature"));
    }
    @Test void requiresAuthenticationAndRejectsBadBearer() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(httpBasic("activity", "test-password"))).andExpect(status().isUnauthorized());
    }
    @Test void returnsStableSubjectForBearerAndBrowserLogin() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer valid-token")).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("cognito-sub"));
        mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("cognito-sub"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("cognito-sub"));
    }
    @Test void bearerWritesUseTokenSubjectAndSessionWritesRequireCsrf() throws Exception {
        when(ingestion.strava("cognito-sub", "url", null)).thenReturn(TestSupport.activity(1000));
        mvc.perform(post("/api/activities/strava").header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"url\"}")).andExpect(status().isCreated());
        verify(ingestion).strava("cognito-sub", "url", null);
        mvc.perform(post("/api/activities/strava").with(oidcLogin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"url\"}")).andExpect(status().isForbidden());
    }
    @Test void startsCognitoAuthorizationWithPkceAndState() throws Exception {
        mvc.perform(get("/oauth2/authorization/cognito")).andExpect(status().isFound())
            .andExpect(header().string("Location", containsString("code_challenge_method=S256")))
            .andExpect(header().string("Location", containsString("state=")));
    }
}

