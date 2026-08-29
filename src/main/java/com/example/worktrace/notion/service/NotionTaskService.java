package com.example.worktrace.notion.service;

import com.example.worktrace.notion.client.NotionClient;
import com.example.worktrace.slack.client.SlackClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Slack 모달 입력값을 Notion에 저장할 업무 데이터로 변환한다. */
@Service
@RequiredArgsConstructor
public class NotionTaskService {

    private static final String RECEIVED_REACTION = "inbox_tray";

    private final SlackClient slackClient;
    private final NotionClient notionClient;

    public synchronized void createTask(
            String taskTitle,
            String taskContent,
            String assigneeId,
            List<String> observerIds,
            String channelId,
            String messageTs
    ) {
        if (taskTitle == null || taskTitle.isBlank()) {
            throw new IllegalArgumentException("업무명은 비어 있을 수 없습니다.");
        }
        if (taskContent == null || taskContent.isBlank()) {
            throw new IllegalArgumentException("업무 내용은 비어 있을 수 없습니다.");
        }
        if (assigneeId == null || assigneeId.isBlank()) {
            throw new IllegalArgumentException("담당자를 선택해야 합니다.");
        }

        String slackMessageUrl = slackClient.getMessagePermalink(channelId, messageTs);

        // Slack 재시도나 사용자의 반복 클릭으로 같은 메시지가 중복 등록되는 것을 막는다.
        Optional<NotionClient.NotionTaskReference> existingTask =
                notionClient.findTaskBySlackMessageUrl(slackMessageUrl);
        if (existingTask.isPresent()) {
            NotionClient.NotionTaskReference task = existingTask.get();
            if (task.assigneeSlackId() == null || task.assigneeSlackId().isBlank()) {
                notionClient.updateAssigneeSlackId(task.pageId(), assigneeId);
            }
            // 기존 업무에 접수 표시가 빠져 있다면 Bot 반응을 다시 보장한다.
            slackClient.addReaction(channelId, messageTs, RECEIVED_REACTION);
            return;
        }

        String assigneeName = slackClient.getUserDisplayName(assigneeId);
        List<String> observerNames = observerIds.stream()
                .map(slackClient::getUserDisplayName)
                .toList();

        notionClient.createTask(
                taskTitle,
                taskContent,
                assigneeName,
                assigneeId,
                observerNames,
                slackMessageUrl
        );

        // Notion 생성이 성공한 뒤 원본 Slack 메시지에 접수 상태를 표시한다.
        slackClient.addReaction(channelId, messageTs, RECEIVED_REACTION);
    }
}
