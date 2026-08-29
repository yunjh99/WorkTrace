package com.example.worktrace.slack.service;

import com.example.worktrace.notion.client.NotionClient;
import com.example.worktrace.slack.client.SlackClient;
import com.example.worktrace.slack.dto.SlackEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Slack 메시지에 추가된 상태 이모지를 같은 Notion 업무의 진행상태로 반영한다. */
@Service
@RequiredArgsConstructor
public class SlackReactionService {

    private static final String REACTION_ADDED = "reaction_added";
    private static final String REACTION_REMOVED = "reaction_removed";
    private static final String MESSAGE = "message";

    private final SlackClient slackClient;
    private final NotionClient notionClient;

    public void handle(SlackEventPayload payload) {
        SlackEventPayload.Event event = payload.event();
        if (event == null || !isStatusReactionEvent(event.type())) {
            return;
        }
        if (event.item() == null || !MESSAGE.equals(event.item().type())) {
            return;
        }

        Optional<String> status = statusFromReaction(event.type(), event.reaction());
        if (status.isEmpty()) {
            return;
        }

        String permalink = slackClient.getMessagePermalink(
                event.item().channel(),
                event.item().ts()
        );

        notionClient.findTaskBySlackMessageUrl(permalink)
                .filter(task -> event.user().equals(task.assigneeSlackId()))
                .ifPresent(task -> notionClient.updateStatus(task.pageId(), status.get()));
    }

    private boolean isStatusReactionEvent(String eventType) {
        return REACTION_ADDED.equals(eventType) || REACTION_REMOVED.equals(eventType);
    }

    private Optional<String> statusFromReaction(String eventType, String reaction) {
        if (REACTION_ADDED.equals(eventType)) {
            return switch (reaction) {
                case "eyes" -> Optional.of("진행중");
                case "white_check_mark" -> Optional.of("완료");
                default -> Optional.empty();
            };
        }

        return switch (reaction) {
            case "white_check_mark" -> Optional.of("진행중");
            case "eyes" -> Optional.of("접수");
            default -> Optional.empty();
        };
    }
}
