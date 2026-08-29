package com.example.worktrace.slack.dto;

/** 모달 제출 후 원본 Slack 메시지를 다시 찾기 위해 숨겨서 전달하는 정보다. */
public record SlackModalMetadata(
        String channelId,
        String messageTs,
        String messageUserId,
        String registeredBy
) {
}
