package com.example.activity.ingestion;

import com.example.activity.TestSupport;
import com.example.activity.domain.InvalidActivityException;
import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class FirecrawlClientTest {
    MockRestServiceServer server; FirecrawlClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        client = new FirecrawlClient(builder.build(), TestSupport.properties(), new ExtractionMapper());
    }
    @Test void sendsV2StructuredExtractionAndParsesResponse() {
        server.expect(requestTo("https://upstream.test/v2/scrape")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-api-key"))
            .andExpect(jsonPath("$.formats[0]").value("markdown"))
            .andExpect(jsonPath("$.formats[1]").value("rawHtml"))
            .andExpect(jsonPath("$.formats[2].type").value("json"))
            .andExpect(jsonPath("$.url").value("https://www.strava.com/activities/123"))
            .andRespond(withSuccess("{\"success\":true,\"data\":{\"json\":" + TestSupport.EXTRACTED + "}}", MediaType.APPLICATION_JSON));
        assertThat(client.extract("https://strava.com/activities/123?secret=redacted").distanceMeters()).isEqualTo(125000.0);
        server.verify();
    }
    @Test void rejectsOffsiteUrlsBeforeNetwork() {
        for (String url : new String[]{"http://strava.com/activities/1", "https://strava.com.evil.test/activities/1", "https://localhost/activities/1", "https://strava.com@evil.test/activities/1", "https://strava.com/athletes/1", "https://strava.com:443/activities/1", "not a url"})
            assertThatThrownBy(() -> client.extract(url)).isInstanceOf(InvalidActivityException.class);
        server.verify();
    }
    @Test void handlesProviderFailure() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.extract("https://strava.com/activities/1")).isInstanceOf(UpstreamException.class);
    }
    @Test void handlesMissingExtraction() {
        server.expect(anything()).andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extract("https://strava.com/activities/1")).isInstanceOf(UpstreamException.class);
    }
}
