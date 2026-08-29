package com.example.worktrace.notion;

import com.example.worktrace.notion.client.NotionClient;
import com.example.worktrace.notion.service.NotionTaskService;
import com.example.worktrace.slack.client.SlackClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotionTaskServiceTest {

    private final SlackClient slackClient = mock(SlackClient.class);
    private final NotionClient notionClient = mock(NotionClient.class);
    private final NotionTaskService notionTaskService =
            new NotionTaskService(slackClient, notionClient);

    @Test
    void 같은_Slack_메시지가_이미_등록되어_있으면_다시_생성하지_않는다() {
        String permalink = "https://workspace.slack.com/archives/C123/p123456";
        when(slackClient.getMessagePermalink("C123", "123.456"))
                .thenReturn(permalink);
        when(notionClient.findTaskBySlackMessageUrl(permalink))
                .thenReturn(Optional.of(
                        new NotionClient.NotionTaskReference("existing-page-id", "UASSIGNEE")
                ));

        notionTaskService.createTask(
                "업무명",
                "업무 내용",
                "UASSIGNEE",
                List.of("UOBSERVER"),
                "C123",
                "123.456"
        );

        verify(slackClient, never()).getUserDisplayName("UASSIGNEE");
        verify(slackClient).addReaction("C123", "123.456", "inbox_tray");
        verify(notionClient, never()).createTask(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void Notion_업무_생성_후_원본_메시지에_접수_이모지를_추가한다() {
        String permalink = "https://workspace.slack.com/archives/C123/p123456";
        when(slackClient.getMessagePermalink("C123", "123.456"))
                .thenReturn(permalink);
        when(notionClient.findTaskBySlackMessageUrl(permalink))
                .thenReturn(Optional.empty());
        when(slackClient.getUserDisplayName("UASSIGNEE"))
                .thenReturn("담당자");
        when(slackClient.getUserDisplayName("UOBSERVER"))
                .thenReturn("참조자");

        notionTaskService.createTask(
                "업무명",
                "업무 내용",
                "UASSIGNEE",
                List.of("UOBSERVER"),
                "C123",
                "123.456"
        );

        verify(notionClient).createTask(
                "업무명",
                "업무 내용",
                "담당자",
                "UASSIGNEE",
                List.of("참조자"),
                permalink
        );
        verify(slackClient).addReaction("C123", "123.456", "inbox_tray");
    }

    @Test
    void 기존_업무에_담당자_Slack_ID가_없으면_중복_생성_대신_보완한다() {
        String permalink = "https://workspace.slack.com/archives/C123/p123456";
        when(slackClient.getMessagePermalink("C123", "123.456"))
                .thenReturn(permalink);
        when(notionClient.findTaskBySlackMessageUrl(permalink))
                .thenReturn(Optional.of(
                        new NotionClient.NotionTaskReference("existing-page-id", null)
                ));

        notionTaskService.createTask(
                "업무명",
                "업무 내용",
                "UASSIGNEE",
                List.of(),
                "C123",
                "123.456"
        );

        verify(notionClient).updateAssigneeSlackId("existing-page-id", "UASSIGNEE");
        verify(slackClient).addReaction("C123", "123.456", "inbox_tray");
    }
}
