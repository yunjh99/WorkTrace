package com.example.worktrace.slack.client;

import com.example.worktrace.slack.dto.SlackViewOpenRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Slack Web API와 HTTP 통신하는 외부 연동 클라이언트다.
 *
 * <p>Bot Token 인증 헤더를 설정하고 {@code views.open} API를 호출한다.
 * Slack은 HTTP 200 안에서도 {@code ok=false}로 실패를 알릴 수 있으므로
 * HTTP 상태뿐 아니라 응답 본문의 ok 값도 검사한다.</p>
 */
@Component
public class SlackClient {

    private final RestClient restClient;

    public SlackClient(@Value("${slack.bot.token}") String botToken) {
        this.restClient = RestClient.builder()
                .baseUrl("https://slack.com/api")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                .build();
    }

    public String getUserDisplayName(String userId) {
        SlackUserResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users.info")
                        .queryParam("user", userId)
                        .build())
                .retrieve()
                .body(SlackUserResponse.class);

        if (response == null || !response.ok() || response.user() == null) {
            String error = response == null ? "empty_response" : response.error();
            throw new IllegalStateException("Slack 사용자 조회에 실패했습니다: " + error);
        }

        SlackUser user = response.user();
        if (user.profile() != null && !isBlank(user.profile().displayName())) {
            return user.profile().displayName();
        }
        if (user.profile() != null && !isBlank(user.profile().realName())) {
            return user.profile().realName();
        }
        return user.name();
    }

    public String getMessagePermalink(String channelId, String messageTs) {
        SlackPermalinkResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/chat.getPermalink")
                        .queryParam("channel", channelId)
                        .queryParam("message_ts", messageTs)
                        .build())
                .retrieve()
                .body(SlackPermalinkResponse.class);

        if (response == null || !response.ok() || isBlank(response.permalink())) {
            String error = response == null ? "empty_response" : response.error();
            throw new IllegalStateException("Slack 메시지 링크 조회에 실패했습니다: " + error);
        }

        return response.permalink();
    }

    public void addReaction(String channelId, String messageTs, String reaction) {
        Map<String, String> request = Map.of(
                "channel", channelId,
                "timestamp", messageTs,
                "name", reaction
        );

        SlackApiResponse response = restClient.post()
                .uri("/reactions.add")
                .body(request)
                .retrieve()
                .body(SlackApiResponse.class);

        // 이미 Bot이 같은 이모지를 추가했다면 원하는 결과가 충족된 것으로 본다.
        if (response != null && "already_reacted".equals(response.error())) {
            return;
        }
        if (response == null || !response.ok()) {
            String error = response == null ? "empty_response" : response.error();
            throw new IllegalStateException("Slack 이모지 추가에 실패했습니다: " + error);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public void openModal(SlackViewOpenRequest request) {
        SlackApiResponse response = restClient.post()
                .uri("/views.open")
                .body(request)
                .retrieve()
                .body(SlackApiResponse.class);

        // Slack API의 논리적 실패까지 확인한다. 예: trigger_expired, invalid_auth.
        if (response == null || !response.ok()) {
            String error = response == null ? "empty_response" : response.error();
            throw new IllegalStateException("Slack 모달 열기에 실패했습니다: " + error);
        }
    }

    /** Slack Web API의 공통 성공 여부와 오류 코드를 받는 내부 응답 DTO다. */
    private record SlackApiResponse(
            boolean ok,
            String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SlackUserResponse(
            boolean ok,
            String error,
            SlackUser user
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SlackUser(
            String name,
            SlackProfile profile
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SlackProfile(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("real_name") String realName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SlackPermalinkResponse(
            boolean ok,
            String error,
            String permalink
    ) {
    }
}
