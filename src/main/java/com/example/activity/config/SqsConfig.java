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
        if (config.shortQueueUrl() == null || !config.shortQueueUrl().endsWith(".fifo") || config.longQueueUrl() == null
            || !config.longQueueUrl().endsWith(".fifo") || config.shortQueueUrl().equals(config.longQueueUrl()))
            throw new IllegalArgumentException("Two distinct FIFO queue URLs are required");
        var builder = SqsClient.builder().region(Region.of(config.region()))
            .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(20))
                .apiCallAttemptTimeout(Duration.ofSeconds(8)).build());
        if (config.endpoint() != null && !config.endpoint().toString().isBlank()) builder.endpointOverride(config.endpoint());
        return builder.build();
    }
}
