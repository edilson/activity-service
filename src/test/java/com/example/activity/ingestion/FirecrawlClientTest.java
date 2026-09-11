package com.example.activity.ingestion;

import com.example.activity.TestSupport;
import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.InvalidActivityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class FirecrawlClientTest {
    HttpServer server; FirecrawlClient client; ObjectMapper json = new ObjectMapper();
    AtomicInteger calls; String body; String authorization; String requestPath;
    int status; String response;
    @BeforeEach void setup() throws Exception {
        calls = new AtomicInteger(); status = 200;
        response = "{\"success\":true,\"data\":{\"json\":" + TestSupport.EXTRACTED + ",\"rawHtml\":\"<html>ride</html>\",\"metadata\":{\"title\":\"Ride\"}}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization"); requestPath = exchange.getRequestURI().getPath();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var p = TestSupport.properties();
        var api = new ServiceProperties.Api(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-api-key", null);
        client = new FirecrawlClient(new ServiceProperties(api, p.groq(), p.sqs(), p.oauth()), new ExtractionMapper(), json);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void sdkSendsStructuredExtractionAndRetainsDocument() throws Exception {
        var result = client.extractDetailed("https://strava.com/activities/123?secret=redacted");
        assertThat(result.data().distanceMeters()).isEqualTo(125000);
        var request = json.readTree(body);
        assertThat(requestPath).isEqualTo("/v2/scrape");
        assertThat(authorization).isEqualTo("Bearer test-api-key");
        assertThat(request.path("url").asText()).isEqualTo("https://www.strava.com/activities/123");
        assertThat(request.path("storeInCache").asBoolean()).isFalse();
        assertThat(request.path("formats").get(0).asText()).isEqualTo("markdown");
        assertThat(request.path("formats").get(1).asText()).isEqualTo("rawHtml");
        assertThat(request.path("formats").get(2).path("schema").isObject()).isTrue();
        assertThat(json.valueToTree(result).toString()).contains("<html>ride</html>", "Ride");
    }
    @Test void rejectsOffsiteUrlsBeforeNetwork() {
        for (String url : new String[]{"http://strava.com/activities/1", "https://strava.com.evil.test/activities/1", "https://localhost/activities/1", "https://strava.com@evil.test/activities/1", "https://strava.com/athletes/1", "https://strava.com:443/activities/1", "not a url"})
            assertThatThrownBy(() -> client.extract(url)).isInstanceOf(InvalidActivityException.class);
        assertThat(calls.get()).isZero();
    }
    @Test void providerFailureIsNotRetried() {
        status = 429; response = "{\"success\":false,\"error\":\"rate limited\"}";
        assertThatThrownBy(() -> client.extract("https://strava.com/activities/1")).isInstanceOf(UpstreamException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void handlesMissingExtraction() {
        response = "{\"success\":true,\"data\":{}}";
        assertThatThrownBy(() -> client.extract("https://strava.com/activities/1")).isInstanceOf(UpstreamException.class);
    }
}
