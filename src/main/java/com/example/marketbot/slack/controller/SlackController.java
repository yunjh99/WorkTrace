package com.example.marketbot.slack.controller;

import com.example.marketbot.slack.service.SlackCommandService;
import com.example.marketbot.slack.service.SlackInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/**
 * ✅ 왜 필요한가?
 * - Slack은 message shortcut 클릭, modal submit 등 인터랙션이 발생하면
 *   Interactivity 설정된 URL로 HTTP POST를 보냄.
 * - 우리는 그 진입점을 받아서 service로 넘겨야 함.
 *
 * ✅ Slack 요청 형태
 * - Content-Type: application/x-www-form-urlencoded
 * - payload=<JSON 문자열>
 */

@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
@Slf4j
public class SlackController {

    private final SlackInteractionService slackInteractionService;
    private final SlackCommandService slackCommandService;

    @PostMapping("/interaction")
    public ResponseEntity<String> interaction(@RequestParam("payload") String payload) {
        log.debug("Slack interaction request received");

        // ✅ payload는 JSON 문자열. service에서 파싱/분기 처리한다.
        slackInteractionService.handle(payload);

        // ✅ Slack은 3초 내 응답을 권장.
        // 일단 성공 처리로 빈 문자열 반환.
        return ResponseEntity.ok("");
    }

    @PostMapping(value="/command", consumes="application/x-www-form-urlencoded")
    public ResponseEntity<String> command(@RequestParam MultiValueMap<String,String> form) {
        String command = form.getFirst("command");     // "/업무현황"
        String userId = form.getFirst("user_id");      // 실행자
        String responseUrl = form.getFirst("response_url");

        log.debug("Slack command request received. command={}, userId={}", command, userId);
        slackCommandService.handleCommand(command, userId, responseUrl);

        return ResponseEntity.ok("조회 중...");
    }

    private String first(org.springframework.util.MultiValueMap<String, String> form, String key) {
        String v = form.getFirst(key);
        return v == null ? "" : v;
    }
}
