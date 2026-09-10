package com.example.activity.config;

import com.example.activity.TestSupport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigTest {
    @Test void constructsBoundedHttpAndSqsClients() {
        assertThat(new HttpConfig().externalRestClient()).isNotNull();
        try (var sqs = new SqsConfig().sqsClient(TestSupport.properties())) { assertThat(sqs.serviceName()).isEqualTo("sqs"); }
    }
    @Test void requiresDistinctFifoQueueUrls() {
        var p = TestSupport.properties();
        for (var queue : new ServiceProperties.Queue[]{
            new ServiceProperties.Queue(true, "us-east-1", null, "standard", "long.fifo"),
            new ServiceProperties.Queue(true, "us-east-1", null, "same.fifo", "same.fifo")}) {
            var invalid = new ServiceProperties(p.firecrawl(), p.groq(), queue, p.oauth());
            assertThatThrownBy(() -> new SqsConfig().sqsClient(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
