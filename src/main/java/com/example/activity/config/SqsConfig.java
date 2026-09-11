package com.example.activity.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "activity.sqs.enabled", havingValue = "true")
public class SqsConfig {
    @Bean
    public SqsClient sqsClient(ServiceProperties properties) {
        var config = properties.sqs();
        if (!validQueueUrl(config.shortQueueUrl(), false) || !validQueueUrl(config.longQueueUrl(), true))
            throw new IllegalArgumentException("A standard short queue URL and a FIFO long queue URL are required");
        var builder = SqsClient.builder().region(Region.of(config.region()))
            .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(20))
                .apiCallAttemptTimeout(Duration.ofSeconds(8)).build());
        if (config.endpoint() != null && !config.endpoint().toString().isBlank()) builder.endpointOverride(config.endpoint());
        return builder.build();
    }
    private static boolean validQueueUrl(String value, boolean fifo) {
        if (value == null || value.isBlank()) return false;
        try {
            var uri = java.net.URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                && uri.getQuery() == null && uri.getFragment() == null && uri.getUserInfo() == null
                && uri.getPath() != null && uri.getPath().length() > 1 && !uri.getPath().endsWith("/")
                && uri.getPath().endsWith(".fifo") == fifo;
        } catch (IllegalArgumentException e) { return false; }
    }
}
