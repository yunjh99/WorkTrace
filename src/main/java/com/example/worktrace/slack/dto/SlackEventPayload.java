package com.example.worktrace.slack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Slack Events API의 URL 검증 및 이모지 추가 이벤트를 받는 DTO다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackEventPayload(
        String type,
        String challenge,
        @JsonProperty("event_id") String eventId,
        Event event
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String type, String user, String reaction, Item item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String type, String channel, String ts) {
    }
}
