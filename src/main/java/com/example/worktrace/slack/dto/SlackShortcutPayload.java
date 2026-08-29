package com.example.worktrace.slack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Slack Message Shortcut 요청의 JSON 구조를 Java 객체로 옮기는 DTO다.
 *
 * <p>현재 기능에 필요한 이벤트 종류, callback ID, trigger ID, 사용자, 채널,
 * 원본 메시지만 선언한다. DTO는 데이터 전달만 담당하며 업무 로직은 포함하지 않는다.</p>
 */
// Slack payload에는 team, token, response_url 등 사용하지 않는 필드도 많다.
// ignoreUnknown=true를 사용하면 DTO에 선언하지 않은 필드는 오류 없이 무시된다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackShortcutPayload(
        String type,

        // JSON의 snake_case 필드 callback_id를 Java의 callbackId에 연결한다.
        @JsonProperty("callback_id")
        String callbackId,

        // JSON의 snake_case 필드 trigger_id를 Java의 triggerId에 연결한다.
        @JsonProperty("trigger_id")
        String triggerId,

        User user,
        Channel channel,
        Message message
) {

    /** Shortcut을 실행한 Slack 사용자 정보다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            String id,
            String username
    ) {
    }

    /** Shortcut이 실행된 원본 메시지의 채널 정보다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(
            String id,
            String name
    ) {
    }

    /** 업무로 등록할 원본 Slack 메시지 정보다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String user,
            String text,
            String ts
    ) {
    }
}
