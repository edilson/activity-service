package com.example.activity.ingestion;

import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firecrawl.models.ScrapeOptions;
import com.firecrawl.errors.FirecrawlException;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.util.*;

@Service
public class FirecrawlClient {
    private final com.firecrawl.client.FirecrawlClient sdk;
    private final ServiceProperties properties; private final ExtractionMapper mapper; private final ObjectMapper json;
    public FirecrawlClient(ServiceProperties properties, ExtractionMapper mapper, ObjectMapper json) {
        this.properties = properties; this.mapper = mapper; this.json = json;
        var api = properties.firecrawl();
        this.sdk = com.firecrawl.client.FirecrawlClient.builder()
            .apiKey(api.apiKey() == null || api.apiKey().isBlank() ? "not-configured" : api.apiKey())
            .apiUrl(api.baseUrl().toString()).maxRetries(0)
            .httpClient(new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()).build();
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
            var document = sdk.scrape("https://www.strava.com" + uri.getPath(), ScrapeOptions.builder()
                .storeInCache(false).formats(List.of("markdown", "rawHtml",
                    Map.of("type", "json", "prompt", ExtractionMapper.PROMPT, "schema", mapper.schema()))).build());
            JsonNode data = json.valueToTree(document);
            if (document == null || !data.path("json").isObject())
                throw new UpstreamException("Firecrawl did not return activity data");
            var response = json.createObjectNode().put("success", true).set("data", data);
            return new ExtractionResult(mapper.map(data.path("json")), response);
        } catch (FirecrawlException e) { throw new UpstreamException("Firecrawl request failed; retry later or upload the activity file"); }
    }
}
