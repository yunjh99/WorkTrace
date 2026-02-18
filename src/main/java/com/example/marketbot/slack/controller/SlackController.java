package com.example.marketbot.slack.controller;

import com.example.marketbot.slack.service.SlackCommandService;
import com.example.marketbot.slack.service.SlackEventService;
import com.example.marketbot.slack.service.SlackInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
public class SlackController {

    private final SlackInteractionService slackInteractionService;
    private final SlackEventService slackEventService;
    private final ObjectMapper om;   // ✅ 추가
    private final SlackCommandService slackCommandService;

    @PostMapping("/interaction")
    public ResponseEntity<String> interaction(@RequestParam("payload") String payload) {
        System.out.println("========== [SLACK RAW payload param] ==========");
        System.out.println(payload);
        System.out.println("==============================================");

        // ✅ payload는 JSON 문자열. service에서 파싱/분기 처리한다.
        slackInteractionService.handle(payload);

        // ✅ Slack은 3초 내 응답을 권장.
        // 일단 성공 처리로 빈 문자열 반환.
        return ResponseEntity.ok("");
    }

    // ✅ Slack Events API (json body)
    @PostMapping("/events")
    public ResponseEntity<String> events(@RequestBody String body) throws Exception {
        JsonNode payload = om.readTree(body);

        // url_verification 처리
        if ("url_verification".equals(payload.path("type").asText())) {
            String challenge = payload.path("challenge").asText();
            return ResponseEntity.ok(challenge);
        }

        // event_callback 처리
        slackEventService.handle(payload);
        return ResponseEntity.ok("ok");
    }

    @PostMapping(value="/command", consumes="application/x-www-form-urlencoded")
    public ResponseEntity<String> command(@RequestParam MultiValueMap<String,String> form) {
        String command = form.getFirst("command");     // "/업무현황"
        String userId = form.getFirst("user_id");      // 실행자
        String responseUrl = form.getFirst("response_url");

        slackCommandService.handleCommand(command, userId, responseUrl);

        return ResponseEntity.ok("조회 중...");
    }

    private String first(org.springframework.util.MultiValueMap<String, String> form, String key) {
        String v = form.getFirst(key);
        return v == null ? "" : v;
    }
}