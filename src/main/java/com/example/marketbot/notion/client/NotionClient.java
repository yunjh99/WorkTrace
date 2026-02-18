package com.example.marketbot.notion.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NotionClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper om;

    @Value("${notion.token}")
    private String token;

    @Value("${notion.version}")
    private String version;

    public JsonNode get(String url) {
        HttpHeaders headers = baseHeaders();
        ResponseEntity<String> res = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        return parseResponse(res);
    }

    /** ✅ 추가: Notion API POST */
    public JsonNode post(String url, Object body) {
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        return parseResponse(res);
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Notion-Version", version);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private JsonNode parseResponse(ResponseEntity<String> res) {
        if (!res.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Notion API error: " + res.getStatusCode() + " body=" + res.getBody());
        }
        try {
            return om.readTree(res.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Notion JSON parse error", e);
        }
    }

    /** ✅ Notion API PATCH */
    public JsonNode patch(String url, Object body) {
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> res = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                String.class
        );

        return parseResponse(res);
    }

}
