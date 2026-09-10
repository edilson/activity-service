package com.example.activity.ingestion;

import com.example.activity.TestSupport;
import com.example.activity.domain.InvalidActivityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import java.util.Map;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class GroqClientTest {
    MockRestServiceServer server; GroqClient client; ObjectMapper json = new ObjectMapper();
    byte[] png = {(byte)137,80,78,71,13,10,26,10};
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        client = new GroqClient(builder.build(), TestSupport.properties(), new ExtractionMapper(), json);
    }
    private String response(String content, String finish) throws Exception {
        return json.writeValueAsString(Map.of("choices", java.util.List.of(Map.of("finish_reason", finish, "message", Map.of("content", content)))));
    }
    @Test void sendsBase64ImageAndJsonMode() throws Exception {
        server.expect(requestTo("https://upstream.test/openai/v1/chat/completions"))
            .andExpect(header("Authorization", "Bearer test-api-key"))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andExpect(jsonPath("$.messages[0].content[1].image_url.url").value("data:image/png;base64,iVBORw0KGgo="))
            .andRespond(withSuccess(response(TestSupport.EXTRACTED, "stop"), MediaType.APPLICATION_JSON));
        assertThat(client.extract(png).elevationGainMeters()).isEqualTo(500.0); server.verify();
    }
    @Test void rejectsBadAndOversizedImage() {
        assertThatThrownBy(() -> client.extract(new byte[]{1})).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> client.extract(new byte[4 * 1024 * 1024 + 1])).isInstanceOf(InvalidActivityException.class);
    }
    @Test void rejectsTruncatedResponse() throws Exception {
        server.expect(anything()).andRespond(withSuccess(response(TestSupport.EXTRACTED, "length"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extract(png)).isInstanceOf(UpstreamException.class);
    }
    @Test void rejectsMalformedJson() throws Exception {
        server.expect(anything()).andRespond(withSuccess(response("not json", "stop"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extract(png)).isInstanceOf(UpstreamException.class);
    }
    @Test void handlesUpstreamFailure() {
        server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(() -> client.extract(png)).isInstanceOf(UpstreamException.class);
    }
}
