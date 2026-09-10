package com.example.activity.service;

import com.example.activity.TestSupport;
import com.example.activity.domain.*;
import com.example.activity.repository.ProviderConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import java.time.Instant;
import java.util.Optional;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;

class OAuthServiceTest {
    MockRestServiceServer server; OAuthService service; ProviderConnectionRepository connections; TokenCipher cipher;
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        connections = mock(ProviderConnectionRepository.class); cipher = new TokenCipher(TestSupport.properties());
        when(connections.findByOwnerAndProvider(anyString(), anyString())).thenReturn(Optional.empty());
        service = new OAuthService(TestSupport.properties(), builder.build(), cipher, connections, new ObjectMapper());
    }
    @Test void usesGarminPkceAndUniqueSessionState() {
        var auth = service.authorize("garmin", "rider");
        assertThat(auth.url().toString()).contains("code_challenge_method=S256", "code_challenge=" + OAuthService.challenge(auth.pending().verifier()));
        assertThat(auth.pending().state()).hasSize(43).isNotEqualTo(service.authorize("garmin", "rider").pending().state());
        assertThat(service.authorize("polar", "rider").url().toString()).doesNotContain("code_challenge");
        assertThat(OAuthService.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }
    @Test void validatesStateExpiryProviderAndDenialBeforeExchange() {
        var pending = service.authorize("garmin", "rider").pending();
        assertThatThrownBy(() -> service.complete("garmin", "wrong", "code", pending)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> service.complete("polar", pending.state(), "code", pending)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> service.complete("garmin", pending.state(), null, pending)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> service.complete("garmin", pending.state(), "code", null)).isInstanceOf(InvalidActivityException.class);
        var expired = new OAuthService.Pending("garmin", "rider", "state", "verifier", Instant.EPOCH);
        assertThatThrownBy(() -> service.complete("garmin", "state", "code", expired)).isInstanceOf(InvalidActivityException.class);
        verifyNoInteractions(connections); server.verify();
    }
    @Test void exchangesGarminCodeAndStoresEncryptedTokens() {
        var pending = service.authorize("garmin", "rider").pending();
        server.expect(requestTo("https://upstream.test/token")).andExpect(content().string(containsString("code_verifier=" + pending.verifier())))
            .andExpect(content().string(containsString("client_secret=client-secret")))
            .andRespond(withSuccess("{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":86400}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://upstream.test/user")).andExpect(header("Authorization", "Bearer access"))
            .andRespond(withSuccess("{\"userId\":\"garmin-user\"}", MediaType.APPLICATION_JSON));
        service.complete("garmin", pending.state(), "code", pending);
        var captor = ArgumentCaptor.forClass(ProviderConnection.class); verify(connections).save(captor.capture());
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("garmin-user");
        assertThat(captor.getValue().getEncryptedTokens()).doesNotContain("access");
        assertThat(cipher.decrypt(captor.getValue().getEncryptedTokens(), "rider:garmin")).contains("refresh"); server.verify();
    }
    @Test void polarUsesBasicAuthAndRegistersUser() {
        var pending = service.authorize("polar", "rider").pending();
        server.expect(requestTo("https://upstream.test/token")).andExpect(header("Authorization", "Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="))
            .andRespond(withSuccess("{\"access_token\":\"access\",\"x_user_id\":123}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://upstream.test/user")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.member-id").value("rider")).andRespond(withStatus(HttpStatus.CREATED));
        service.complete("polar", pending.state(), "code", pending); verify(connections).save(any()); server.verify();
    }
    @Test void rotatesGarminRefreshToken() {
        var connection = new ProviderConnection("rider", "garmin");
        connection.update("id", cipher.encrypt("{\"refresh_token\":\"old-refresh\"}", "rider:garmin"), Instant.now());
        when(connections.findByOwnerAndProvider("rider", "garmin")).thenReturn(Optional.of(connection));
        server.expect(requestTo("https://upstream.test/token")).andExpect(content().string(containsString("refresh_token=old-refresh")))
            .andRespond(withSuccess("{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":86400}", MediaType.APPLICATION_JSON));
        service.refreshGarmin("rider");
        assertThat(cipher.decrypt(connection.getEncryptedTokens(), "rider:garmin")).contains("new-refresh").doesNotContain("old-refresh");
    }
}
