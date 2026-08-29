package com.example.worktrace.slack.service;

import com.example.worktrace.slack.client.SlackClient;
import com.example.worktrace.slack.dto.SlackModalMetadata;
import com.example.worktrace.slack.dto.SlackShortcutPayload;
import com.example.worktrace.slack.dto.SlackViewSubmissionPayload;
import com.example.worktrace.slack.dto.SlackViewOpenRequest;
import com.example.worktrace.slack.modal.SlackModalFactory;
import com.example.worktrace.notion.service.NotionTaskService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Slack Interaction 중 WorkTrace의 업무등록 Message Shortcut을 처리한다.
 *
 * <p>Controller가 전달한 JSON 문자열을 DTO로 변환하고, 이벤트 종류와 callback ID가
 * 업무등록 Shortcut에 해당하는지 확인한다. 조건이 맞으면 모달 요청을 생성하고
 * Slack API 호출을 위임한다.</p>
 */
@Service
@RequiredArgsConstructor
public class SlackShortcutService {

    private static final String MESSAGE_ACTION = "message_action";
    private static final String CREATE_TASK_CALLBACK =
            "worktrace_create_task";
    private static final String VIEW_SUBMISSION = "view_submission";
    private static final String TASK_SUBMIT_CALLBACK = "worktrace_task_submit";

    private final ObjectMapper objectMapper;
    private final SlackModalFactory slackModalFactory;
    private final SlackClient slackClient;
    private final NotionTaskService notionTaskService;

    public void handleInteraction(String payloadJson) {
        String type = objectMapper.readValue(payloadJson, InteractionType.class).type();

        if (MESSAGE_ACTION.equals(type)) {
            openTaskModal(payloadJson);
            return;
        }

        if (VIEW_SUBMISSION.equals(type)) {
            createNotionTask(payloadJson);
        }
    }

    private void openTaskModal(String payloadJson) {
        // application/x-www-form-urlencoded 안에 들어 있던 JSON 문자열을 DTO로 변환한다.
        SlackShortcutPayload payload =
                objectMapper.readValue(
                        payloadJson,
                        SlackShortcutPayload.class
                );

        // 같은 URL로 view_submission, block_actions 등 다른 이벤트도 들어올 수 있다.
        if (!MESSAGE_ACTION.equals(payload.type())) {
            return;
        }

        // 여러 Shortcut 중 WorkTrace 업무등록 Shortcut만 처리한다.
        if (!CREATE_TASK_CALLBACK.equals(payload.callbackId())) {
            return;
        }

        // 모달 JSON 생성과 외부 API 호출은 각각 전용 객체에 위임한다.
        SlackViewOpenRequest request = slackModalFactory.createTaskModal(payload);

        slackClient.openModal(request);
    }

    private void createNotionTask(String payloadJson) {
        SlackViewSubmissionPayload payload = objectMapper.readValue(
                payloadJson,
                SlackViewSubmissionPayload.class
        );

        if (!TASK_SUBMIT_CALLBACK.equals(payload.view().callbackId())) {
            return;
        }

        String taskTitle = payload.view().value("task_title_block", "task_title");
        String taskContent = payload.view().value("task_content_block", "task_content");
        String assigneeId = payload.view().selectedUser("assignee_block", "assignee");
        List<String> observerIds = payload.view()
                .selectedUsers("observers_block", "observers");
        SlackModalMetadata metadata = objectMapper.readValue(
                payload.view().privateMetadata(),
                SlackModalMetadata.class
        );

        notionTaskService.createTask(
                taskTitle,
                taskContent,
                assigneeId,
                observerIds,
                metadata.channelId(),
                metadata.messageTs()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InteractionType(String type) {
    }
}
