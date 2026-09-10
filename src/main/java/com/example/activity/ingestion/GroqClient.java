package com.example.activity.ingestion;

import com.example.activity.config.ServiceProperties;
import com.example.activity.domain.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.http.MediaType;
import java.util.*;

@Service
public class GroqClient {
    private final RestClient http; private final ServiceProperties properties; private final ExtractionMapper mapper; private final ObjectMapper json;
    public GroqClient(RestClient http, ServiceProperties properties, ExtractionMapper mapper, ObjectMapper json) {
        this.http = http; this.properties = properties; this.mapper = mapper; this.json = json;
    }
    public ActivityData extract(byte[] bytes) {
        return extractDetailed(bytes).data();
    }
    public ExtractionResult extractDetailed(byte[] bytes) {
        if (bytes.length > 4 * 1024 * 1024) throw new InvalidActivityException("Screenshots must be at most 4 MiB");
        String mime;
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) mime = "image/png";
        else if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) mime = "image/jpeg";
        else throw new InvalidActivityException("Screenshot must be PNG or JPEG");
        var api = properties.groq();
        if (api.apiKey() == null || api.apiKey().isBlank()) throw new UpstreamException("Groq is not configured");
        try {
            JsonNode response = http.post().uri(api.baseUrl().resolve("/openai/v1/chat/completions"))
                .headers(h -> h.setBearerAuth(api.apiKey())).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", api.model(), "response_format", Map.of("type", "json_object"),
                    "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", ExtractionMapper.PROMPT),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes))))))))
                .retrieve().body(JsonNode.class);
            if (response == null || !"stop".equals(response.path("choices").path(0).path("finish_reason").asText()))
                throw new UpstreamException("Groq returned an incomplete extraction");
            return new ExtractionResult(mapper.map(json.readTree(response.path("choices").path(0).path("message").path("content").asText())), response);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new UpstreamException("Groq extraction failed; retry with a clearer screenshot");
        }
    }
}
