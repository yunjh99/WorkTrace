package com.example.worktrace.slack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Slack {@code views.open} API에 전달하는 요청 DTO다.
 *
 * @param triggerId Slack 상호작용으로 발급된 짧은 수명의 일회성 ID
 * @param view      Slack Block Kit 형식의 모달 내용
 */
public record SlackViewOpenRequest(
        // Slack API가 요구하는 JSON 필드명은 trigger_id다.
        @JsonProperty("trigger_id") String triggerId,
        Map<String, Object> view
) {
}
