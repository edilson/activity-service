package com.example.activity.ingestion;

import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.http.MediaType;
import java.net.URI;
import java.util.*;

@Service
public class FirecrawlClient {
    private final RestClient http; private final ServiceProperties properties; private final ExtractionMapper mapper;
    public FirecrawlClient(RestClient http, ServiceProperties properties, ExtractionMapper mapper) {
        this.http = http; this.properties = properties; this.mapper = mapper;
    }
    public ActivityData extract(String url) {
        return extractDetailed(url).data();
    }
    public ExtractionResult extractDetailed(String url) {
        URI uri;
        try { uri = URI.create(url); } catch (RuntimeException e) { throw new InvalidActivityException("Invalid Strava URL"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !Set.of("www.strava.com", "strava.com").contains(uri.getHost())
            || uri.getUserInfo() != null || uri.getPort() != -1 || !uri.getPath().matches("/activities/[0-9]+/?"))
            throw new InvalidActivityException("Use a full https://www.strava.com/activities/{id} URL");
        var api = properties.firecrawl();
        if (api.apiKey() == null || api.apiKey().isBlank()) throw new UpstreamException("Firecrawl is not configured");
        try {
            var response = http.post().uri(api.baseUrl().resolve("/v2/scrape"))
                .headers(h -> h.setBearerAuth(api.apiKey())).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("url", "https://www.strava.com" + uri.getPath(), "storeInCache", false,
                    "formats", List.of("markdown", "rawHtml", Map.of("type", "json", "prompt", ExtractionMapper.PROMPT, "schema", mapper.schema()))))
                .retrieve().body(JsonNode.class);
            if (response == null || !response.path("success").asBoolean() || !response.path("data").path("json").isObject())
                throw new UpstreamException("Firecrawl did not return activity data");
            return new ExtractionResult(mapper.map(response.path("data").path("json")), response);
        } catch (RestClientException e) { throw new UpstreamException("Firecrawl request failed; retry later or upload the activity file"); }
    }
}
