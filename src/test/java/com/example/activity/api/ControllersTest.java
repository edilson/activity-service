package com.example.activity.api;

import com.example.activity.TestSupport;
import com.example.activity.config.SecurityConfig;
import com.example.activity.domain.InvalidActivityException;
import com.example.activity.ingestion.UpstreamException;
import com.example.activity.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.*;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers = {ActivityController.class, OAuthController.class, CsrfController.class}, properties = "spring.security.user.password=test-password")
@Import(SecurityConfig.class)
class ControllersTest {
    @Autowired MockMvc mvc;
    @MockitoBean IngestionService ingestion;
    @MockitoBean ActivityService activities;
    @MockitoBean OAuthService oauth;
    @Test void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/activities")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/activities/strava").with(user("rider")).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"url\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/csrf").with(user("rider"))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
    @Test void uploadsAndReturnsCreatedLocation() throws Exception {
        var activity = TestSupport.activity(1000);
        when(ingestion.file(eq("rider"), eq("ride.gpx"), any())).thenReturn(activity);
        mvc.perform(multipart("/api/activities/files").file(new MockMultipartFile("file", "ride.gpx", "application/gpx+xml", new byte[]{1}))
            .with(user("rider")).with(csrf())).andExpect(status().isCreated()).andExpect(header().string("Location", "/api/activities/" + activity.getId()))
            .andExpect(jsonPath("$.distanceMeters").value(1000));
    }
    @Test void importsStravaAndValidatesBody() throws Exception {
        when(ingestion.strava("rider", "url")).thenReturn(TestSupport.activity(1000));
        mvc.perform(post("/api/activities/strava").with(user("rider")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"url\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/activities/strava").with(user("rider")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void mapsExtractionAndProviderErrors() throws Exception {
        when(ingestion.strava("rider", "missing")).thenThrow(new InvalidActivityException("Missing elevation"));
        when(ingestion.strava("rider", "upstream")).thenThrow(new UpstreamException("Provider unavailable"));
        mvc.perform(post("/api/activities/strava").with(user("rider")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"missing\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value("Missing elevation"));
        mvc.perform(post("/api/activities/strava").with(user("rider")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"upstream\"}"))
            .andExpect(status().isBadGateway());
    }
    @Test void passesAuthenticatedOwnerToQueries() throws Exception {
        var activity = TestSupport.activity(1000); when(activities.get("rider", activity.getId())).thenReturn(activity);
        when(activities.list("rider", 0)).thenReturn(new PageImpl<>(List.of(activity)));
        mvc.perform(get("/api/activities/" + activity.getId()).with(user("rider"))).andExpect(status().isOk());
        mvc.perform(get("/api/activities").with(user("rider"))).andExpect(status().isOk());
        verify(activities).get("rider", activity.getId()); verify(activities).list("rider", 0);
    }
    @Test void bindsOAuthStateToSessionAndConsumesItOnce() throws Exception {
        var pending = new OAuthService.Pending("garmin", "rider", "state", "verifier", java.time.Instant.now().plusSeconds(600));
        when(oauth.authorize("garmin", "rider")).thenReturn(new OAuthService.Authorization(java.net.URI.create("https://provider.test"), pending));
        var session = new MockHttpSession();
        mvc.perform(get("/oauth/garmin/authorize").session(session).with(user("rider"))).andExpect(status().isFound());
        mvc.perform(get("/oauth/garmin/callback?state=state&code=code").session(session)).andExpect(status().isOk());
        verify(oauth).complete("garmin", "state", "code", pending);
        doThrow(new InvalidActivityException("Invalid state")).when(oauth).complete("garmin", "state", "code", null);
        mvc.perform(get("/oauth/garmin/callback?state=state&code=code").session(session)).andExpect(status().isUnprocessableEntity());
    }
    @Test void refreshRequiresAuthenticatedPost() throws Exception {
        mvc.perform(post("/oauth/garmin/refresh").with(user("rider")).with(csrf())).andExpect(status().isOk());
        verify(oauth).refreshGarmin("rider");
    }
    @Test void downloadsOriginalFileAsAttachmentAndOmitsBytesFromMetadata() throws Exception {
        var id = java.util.UUID.randomUUID();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var source = new com.example.activity.domain.ActivitySource(id,
            new com.example.activity.domain.SourceData("ride.fit", "application/octet-stream", new byte[]{0, 1, -1}, null, json.createObjectNode().put("extra", 42)), json.createArrayNode());
        when(activities.source("rider", id)).thenReturn(source);
        mvc.perform(get("/api/activities/" + id + "/source").with(user("rider"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.providerResponse.extra").value(42)).andExpect(jsonPath("$.originalFile").doesNotExist());
        mvc.perform(get("/api/activities/" + id + "/file").with(user("rider"))).andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{0, 1, -1})).andExpect(header().string("Content-Type", "application/octet-stream"));
        when(activities.source("other", id)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/activities/" + id + "/file").with(user("other"))).andExpect(status().isNotFound());
    }
}
