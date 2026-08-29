package com.example.worktrace.slack.controller;

import com.example.worktrace.slack.dto.SlackEventPayload;
import com.example.worktrace.slack.service.SlackReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Slack Events API의 URL 검증과 실제 이벤트 요청을 받는다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/slack")
public class SlackEventController {

    private static final String URL_VERIFICATION = "url_verification";

    private final SlackReactionService slackReactionService;

    @PostMapping(
            value = "/events",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> handleEvent(@RequestBody SlackEventPayload payload) {
        if (URL_VERIFICATION.equals(payload.type())) {
            return ResponseEntity.ok(payload.challenge());
        }

        slackReactionService.handle(payload);
        return ResponseEntity.ok("");
    }
}
