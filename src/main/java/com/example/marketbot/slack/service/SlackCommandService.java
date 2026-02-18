package com.example.marketbot.slack.service;

import com.example.marketbot.notion.service.NotionWorklogService;
import com.example.marketbot.slack.client.SlackClient;
import com.example.marketbot.slack.domain.SlackUser;
import com.example.marketbot.slack.repository.SlackUserRepository;
import com.example.marketbot.slack.view.WorklogStatusMessageBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlackCommandService {

    private final SlackUserRepository slackUserRepository;
    private final NotionWorklogService notionWorklogService;
    private final SlackClient slackClient;
    private final WorklogStatusMessageBuilder worklogStatusMessageBuilder;

    @Async
    public void handleCommand(String command, String userId, String responseUrl) {
        if (!"/업무현황".equals(command)) return;

        String displayName = slackUserRepository.findBySlackUserId(userId)
                .map(SlackUser::getDisplayName)
                .orElseThrow(() -> new IllegalStateException("SlackUser 없음: " + userId));

        LocalDate today = LocalDate.now(); // KST 서버 기준이면 OK

        JsonNode todo = notionWorklogService.queryTodoByAssignee(displayName);
        JsonNode done = notionWorklogService.queryDoneTodayByAssignee(displayName, today);

        Map<String,Object> blocks = worklogStatusMessageBuilder.build(displayName, todo, done);

        slackClient.respondToCommand(responseUrl, blocks);
    }
}
