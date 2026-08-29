package com.example.worktrace.slack.controller;

import com.example.worktrace.slack.service.SlackShortcutService;
import com.example.worktrace.slack.service.SlackCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Slack이 보내는 HTTP 요청을 처음 받는 진입점이다.
 *
 * <p>Slack Interactivity 요청은 JSON 본문이 아니라
 * {@code application/x-www-form-urlencoded} 형식의 {@code payload} 필드로 전달된다.
 * 이 클래스는 payload를 꺼내 서비스에 전달하고, Slack에 빈 200 OK를 응답하는
 * HTTP 계층의 책임만 가진다.</p>
 */
@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackController {

    private final SlackShortcutService slackShortcutService;
    private final SlackCommandService slackCommandService;

    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleInteraction(@RequestParam("payload") String payload) {
        slackShortcutService.handleInteraction(payload);

        // Slack은 상호작용 요청을 정상적으로 수신했다는 빠른 응답을 기대한다.
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> handleCommand(
            @RequestParam("command") String command,
            @RequestParam("user_id") String userId
    ) {
        return ResponseEntity.ok(slackCommandService.handle(command, userId));
    }
}
