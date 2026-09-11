package com.example.activity.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.net.URI;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(S3Config.Properties.class)
public class S3Config {
    @ConfigurationProperties("activity.s3")
    public record Properties(String bucket, String region, URI endpoint) {}
    @Bean
    public S3Client s3Client(Properties properties) {
        if (properties.bucket() == null || !properties.bucket().matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalArgumentException("A valid S3_BUCKET is required");
        var builder = S3Client.builder().region(Region.of(properties.region()))
            .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(60))
                .apiCallAttemptTimeout(Duration.ofSeconds(25)).build());
        if (properties.endpoint() != null && !properties.endpoint().toString().isBlank())
            builder.endpointOverride(properties.endpoint()).forcePathStyle(true);
        return builder.build();
    }
}
