package com.example.worktrace.slack;

import com.example.worktrace.notion.client.NotionClient;
import com.example.worktrace.slack.client.SlackClient;
import com.example.worktrace.slack.dto.SlackEventPayload;
import com.example.worktrace.slack.service.SlackReactionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackReactionServiceTest {

    private final SlackClient slackClient = mock(SlackClient.class);
    private final NotionClient notionClient = mock(NotionClient.class);
    private final SlackReactionService slackReactionService =
            new SlackReactionService(slackClient, notionClient);

    @ParameterizedTest
    @CsvSource({
            "eyes,진행중",
            "white_check_mark,완료"
    })
    void 상태_이모지에_맞춰_Notion_진행상태를_변경한다(
            String reaction,
            String expectedStatus
    ) {
        SlackEventPayload payload = new SlackEventPayload(
                "event_callback",
                null,
                "EV123",
                new SlackEventPayload.Event(
                        "reaction_added",
                        "U123",
                        reaction,
                        new SlackEventPayload.Item("message", "C123", "123.456")
                )
        );

        when(slackClient.getMessagePermalink("C123", "123.456"))
                .thenReturn("https://workspace.slack.com/archives/C123/p123456");
        when(notionClient.findTaskBySlackMessageUrl(
                "https://workspace.slack.com/archives/C123/p123456"
        )).thenReturn(Optional.of(
                new NotionClient.NotionTaskReference("notion-page-id", "U123")
        ));

        slackReactionService.handle(payload);

        verify(notionClient).updateStatus("notion-page-id", expectedStatus);
    }

    @org.junit.jupiter.api.Test
    void 담당자가_아닌_사용자의_상태_이모지는_무시한다() {
        SlackEventPayload payload = new SlackEventPayload(
                "event_callback",
                null,
                "EV123",
                new SlackEventPayload.Event(
                        "reaction_added",
                        "UOTHER",
                        "eyes",
                        new SlackEventPayload.Item("message", "C123", "123.456")
                )
        );

        String permalink = "https://workspace.slack.com/archives/C123/p123456";
        when(slackClient.getMessagePermalink("C123", "123.456"))
                .thenReturn(permalink);
        when(notionClient.findTaskBySlackMessageUrl(permalink))
                .thenReturn(Optional.of(
                        new NotionClient.NotionTaskReference("notion-page-id", "UASSIGNEE")
                ));

        slackReactionService.handle(payload);

        org.mockito.Mockito.verify(
                notionClient,
                org.mockito.Mockito.never()
        ).updateStatus(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest
    @CsvSource({
            "white_check_mark,진행중",
            "eyes,접수"
    })
    void 담당자가_상태_이모지를_제거하면_이전_상태로_되돌린다(
            String reaction,
            String expectedStatus
    ) {
        SlackEventPayload payload = new SlackEventPayload(
                "event_callback",
                null,
                "EV124",
                new SlackEventPayload.Event(
                        "reaction_removed",
                        "UASSIGNEE",
                        reaction,
                        new SlackEventPayload.Item("message", "C123", "123.456")
                )
        );

        String permalink = "https://workspace.slack.com/archives/C123/p123456";
        when(slackClient.getMessagePermalink("C123", "123.456"))
                .thenReturn(permalink);
        when(notionClient.findTaskBySlackMessageUrl(permalink))
                .thenReturn(Optional.of(
                        new NotionClient.NotionTaskReference("notion-page-id", "UASSIGNEE")
                ));

        slackReactionService.handle(payload);

        verify(notionClient).updateStatus("notion-page-id", expectedStatus);
    }
}
