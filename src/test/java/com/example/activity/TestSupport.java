package com.example.activity;

import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.*;
import java.net.URI;
import java.util.*;

public final class TestSupport {
    private TestSupport() {}
    public static ServiceProperties properties() {
        var api = new ServiceProperties.Api(URI.create("https://upstream.test"), "test-api-key", "vision-model");
        var provider = new ServiceProperties.Provider("client-id", "client-secret", URI.create("https://upstream.test/authorize"),
            URI.create("https://upstream.test/token"), URI.create("https://upstream.test/user"));
        return new ServiceProperties(api, api, new ServiceProperties.Queue(true, "us-east-1", null, "https://sqs.test/short", "https://sqs.test/long.fifo"),
            new ServiceProperties.OAuth(Base64.getEncoder().encodeToString(new byte[32]), "https://service.test", Map.of("polar", provider, "garmin", provider)));
    }
    public static ActivityData data(double distance) { return new ActivityData(distance, 3600.0, 150.0, List.of()); }
    public static Activity activity(double distance) { return new Activity("rider", "GPX", data(distance), null); }
    public static final String EXTRACTED = """
        {"sport":"cycling","distanceMeters":125000,"durationSeconds":14400,"elevationGainMeters":500,"route":[]}
        """;
}
