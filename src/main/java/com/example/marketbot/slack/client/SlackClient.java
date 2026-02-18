package com.example.marketbot.slack.client;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ 왜 필요한가?
 * - Slack Web API 호출(views.open 등)을 전담하는 계층.
 * - Service는 "무슨 작업을 할지"만 결정하고,
 *   Client는 "어떻게 Slack API를 호출할지"만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class SlackClient {

    @Value("${slack.bot.token}")
    private String botToken;

    private final RestTemplate restTemplate;

    private static final String API_BASE = "https://slack.com/api";
    private final ObjectMapper om; // ✅ 추가
    /**
     * Slack 모달 열기
     * - endpoint: POST https://slack.com/api/views.open
     *
     * @param triggerId Slack이 준 trigger_id (3초 제한)
     * @param view      Block Kit view JSON(Map)
     */
    public void viewsOpen(String triggerId, Map<String, Object> view) {
        String url = API_BASE + "/views.open";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = Map.of(
                "trigger_id", triggerId,
                "view", view
        );

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack views.open error: " + json);
        }
    }

    /**
     * Slack 메시지 퍼머링크 조회
     * - GET https://slack.com/api/chat.getPermalink?channel=C123&message_ts=1234.5678
     *
     * @param channelId  채널 ID (ex: C012AB3CD)
     * @param messageTs  메시지 ts (ex: 1700000000.123456)
     * @return permalink (https://.../archives/.../p....)
     */
    public String chatGetPermalink(String channelId, String messageTs) {
        String url = API_BASE + "/chat.getPermalink"
                + "?channel=" + URLEncoder.encode(channelId, StandardCharsets.UTF_8)
                + "&message_ts=" + URLEncoder.encode(messageTs, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack chat.getPermalink error: " + json);
        }

        String permalink = json.path("permalink").asText("");
        if (permalink.isBlank()) {
            throw new RuntimeException("Slack chat.getPermalink returned empty permalink: " + json);
        }

        return permalink;
    }


    /**
     * Slack 사용자 정보 조회
     * - GET https://slack.com/api/users.info?user=U123ABC
     *
     * @param userId Slack user id (ex: U012AB3CD)
     * @return Slack API JSON 응답
     */
    public JsonNode usersInfo(String userId) {
        String url = API_BASE + "/users.info?user=" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack users.info error: " + json);
        }

        return json;
    }
    /**
     * Slack 사용자 목록 조회 (전체 동기화용)
     * - GET https://slack.com/api/users.list?limit=200&cursor=...
     *
     * @param limit  1~200 권장
     * @param cursor 다음 페이지 커서(null/blank면 첫 페이지)
     */
    public JsonNode usersList(int limit, String cursor) {
        String url = API_BASE + "/users.list?limit=" + limit;

        if (cursor != null && !cursor.isBlank()) {
            url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack users.list error: " + json);
        }

        return json;
    }

    /**
     * Slack 채널에 사용자에게만 보이는(ephemeral) 메시지 전송
     * - POST https://slack.com/api/chat.postEphemeral
     *
     * @param channelId 채널 ID
     * @param userId    메시지를 볼 사용자 ID
     * @param text      표시할 텍스트
     */
    public void chatPostEphemeral(String channelId, String userId, String text) {
        String url = API_BASE + "/chat.postEphemeral";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "user", userId,
                "text", (text == null ? "" : text)
        );

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack chat.postEphemeral error: " + json);
        }
    }

    public void chatPostMessage(String channelId, String text, String threadTs) {
        String url = API_BASE + "/chat.postMessage";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("channel", channelId);
        body.put("text", (text == null ? "" : text));

        // ✅ thread에 달고 싶으면 thread_ts 포함
        if (threadTs != null && !threadTs.isBlank()) {
            body.put("thread_ts", threadTs);
        }

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack chat.postMessage error: " + json);
        }
    }

    public String chatPostMessageWithBlocks(String channelId,
                                            List<Map<String, Object>> blocks,
                                            String threadTs) {

        String url = API_BASE + "/chat.postMessage";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("blocks", blocks);

        if (threadTs != null && !threadTs.isBlank()) {
            body.put("thread_ts", threadTs);
        }

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack chat.postMessage error: " + json);
        }

        // ✅ bot message ts 추출 (둘 중 하나로 들어올 수 있음)
        String ts = json.path("ts").asText(null);
        if (ts == null || ts.isBlank()) {
            ts = json.path("message").path("ts").asText(null);
        }

        if (ts == null || ts.isBlank()) {
            throw new RuntimeException("Slack chat.postMessage ok but ts missing: " + json);
        }

        return ts;
    }


    /**
     * Slack 메시지 리액션 조회
     * - GET https://slack.com/api/reactions.get?channel=...&timestamp=...
     */
    public JsonNode reactionsGet(String channelId, String messageTs) {
        String url = API_BASE + "/reactions.get"
                + "?channel=" + URLEncoder.encode(channelId, StandardCharsets.UTF_8)
                + "&timestamp=" + URLEncoder.encode(messageTs, StandardCharsets.UTF_8)
                + "&full=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack reactions.get error: " + json);
        }

        return json;
    }

    public void reactionsAdd(String channelId, String messageTs, String emojiName) {
        String url = API_BASE + "/reactions.add";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("timestamp", messageTs);
        body.put("name", emojiName);

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            // 이미 달려있으면 already_reacted 같은 에러가 날 수 있음 → 무시해도 됨
            String err = json == null ? "null" : json.path("error").asText();
            if (!"already_reacted".equals(err)) {
                throw new RuntimeException("Slack reactions.add error: " + json);
            }
        }
    }

    public void reactionsRemove(String channelId, String messageTs, String emojiName) {
        String url = API_BASE + "/reactions.remove";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("timestamp", messageTs);
        body.put("name", emojiName);

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            // 이미 없으면 no_reaction 같은 에러 → 무시해도 됨
            String err = json == null ? "null" : json.path("error").asText();
            if (!"no_reaction".equals(err)) {
                throw new RuntimeException("Slack reactions.remove error: " + json);
            }
        }
    }
    public void chatUpdateWithBlocks(String channelId, String ts, List<Map<String, Object>> blocks) {

        String url = API_BASE + "/chat.update";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("ts", ts);
        body.put("blocks", blocks);
        body.put("text", "worklog updated"); // fallback

        ResponseEntity<JsonNode> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode json = res.getBody();
        if (json == null || !json.path("ok").asBoolean()) {
            throw new RuntimeException("Slack chat.update error: " + json);
        }
    }

    public void respondToCommand(String responseUrl, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String json = om.writeValueAsString(payload); // SlackClient 안에 ObjectMapper가 있으면 그걸 써
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> res = restTemplate.postForEntity(responseUrl, entity, String.class);
            if (!res.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Slack response_url error: " + res.getStatusCode() + " body=" + res.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Slack respondToCommand failed", e);
        }
    }
}
