package com.example.activity.config;

import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("activity")
public record ServiceProperties(Api firecrawl, Api groq, Queue sqs, OAuth oauth) {
    public record Api(URI baseUrl, String apiKey, String model) {}
    public record Queue(boolean enabled, String region, URI endpoint, String shortQueueUrl, String longQueueUrl) {}
    public record OAuth(String encryptionKey, String publicBaseUrl, Map<String, Provider> providers) {}
    public record Provider(String clientId, String clientSecret, URI authorizationUri, URI tokenUri, URI userUri) {}
}
