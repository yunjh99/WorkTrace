package com.example.marketbot.slack.controller;

import com.example.marketbot.slack.service.SlackUserSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SlackAdminController {

    private final SlackUserSyncService slackUserSyncService;

    @PostMapping("/admin/slack/users/sync")
    public ResponseEntity<String> syncSlackUsers() {
        int n = slackUserSyncService.syncAllUsers();
        return ResponseEntity.ok("ok. upserted=" + n);
    }
}

/*
UPDATE slack_user SET team = 'MARKET' WHERE id IN (2, 12, 15, 17);
UPDATE slack_user SET team = 'ERP' WHERE id IN (6, 11);
UPDATE slack_user SET team = 'DATA' WHERE id IN (5, 13, 14, 20);
*/