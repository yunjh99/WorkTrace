package com.example.worktrace.slack.modal;

import com.example.worktrace.slack.dto.SlackModalMetadata;
import com.example.worktrace.slack.dto.SlackShortcutPayload;
import com.example.worktrace.slack.dto.SlackViewOpenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slack Block Kit 형식의 업무등록 모달을 생성한다.
 *
 * <p>선택한 메시지에서 {@code cc} 바로 앞의 멘션을 담당자로, {@code cc} 뒤의
 * 멘션들을 참조자로 추출하고 원본 메시지를 업무 내용 초기값으로 설정한다.
 * Service에서 복잡한 화면 구성 코드가 섞이지 않도록 모달 생성 책임을
 * 이 클래스로 분리했다.</p>
 */
@Component
@RequiredArgsConstructor
public class SlackModalFactory {

    private static final Pattern ASSIGNEE_PATTERN =
            Pattern.compile("<@([A-Z0-9]+)>\\h*cc\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_MENTION_PATTERN =
            Pattern.compile("<@([A-Z0-9]+)>");
    private static final Pattern TELEPHONE_PATTERN =
            Pattern.compile("<tel:[^|>]+\\|([^>]+)>");

    private final ObjectMapper objectMapper;

    public SlackViewOpenRequest createTaskModal(SlackShortcutPayload payload) {
        MentionedUsers mentionedUsers = extractMentionedUsers(payload.message().text());

        // 모달 제출 시 원본 메시지를 다시 식별할 수 있도록 숨겨서 전달할 정보다.
        SlackModalMetadata metadata = new SlackModalMetadata(
                payload.channel().id(),
                payload.message().ts(),
                payload.message().user(),
                payload.user().id()
        );

        Map<String, Object> view = Map.of(
                "type", "modal",
                "callback_id", "worktrace_task_submit",
                "title", plainText("업무 등록"),
                "submit", plainText("등록"),
                "close", plainText("취소"),
                // private_metadata는 화면에 표시되지 않고 view_submission 때 다시 전달된다.
                "private_metadata", objectMapper.writeValueAsString(metadata),
                "blocks", List.of(
                        taskTitleBlock(),
                        taskContentBlock(extractTaskContent(payload.message().text())),
                        assigneeBlock(mentionedUsers.assigneeId()),
                        observersBlock(mentionedUsers.observerIds())
                )
        );

        return new SlackViewOpenRequest(payload.triggerId(), view);
    }

    private Map<String, Object> taskTitleBlock() {
        return Map.of(
                "type", "input",
                "block_id", "task_title_block",
                "label", plainText("업무명"),
                "element", Map.of(
                        "type", "plain_text_input",
                        "action_id", "task_title",
                        "placeholder", plainText("업무명을 입력해주세요")
                )
        );
    }

    private Map<String, Object> taskContentBlock(String initialValue) {
        // 선택한 Slack 메시지 본문을 수정 가능한 업무 내용 초기값으로 사용한다.
        return Map.of(
                "type", "input",
                "block_id", "task_content_block",
                "label", plainText("업무 내용"),
                "element", Map.of(
                        "type", "plain_text_input",
                        "action_id", "task_content",
                        "multiline", true,
                        "initial_value", initialValue
                )
        );
    }

    private Map<String, Object> assigneeBlock(String initialUserId) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", "users_select");
        element.put("action_id", "assignee");
        element.put("placeholder", plainText("담당자를 선택해주세요"));

        // cc 바로 앞에 사용자 멘션이 있을 때만 담당자 초기값을 설정한다.
        if (initialUserId != null) {
            element.put("initial_user", initialUserId);
        }

        return Map.of(
                "type", "input",
                "block_id", "assignee_block",
                "label", plainText("담당자"),
                "element", element
        );
    }

    private Map<String, Object> observersBlock(List<String> initialUserIds) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", "multi_users_select");
        element.put("action_id", "observers");
        element.put("placeholder", plainText("참조자를 선택해주세요"));

        // cc 뒤에 사용자 멘션이 있을 때만 참조자 초기값을 설정한다.
        if (!initialUserIds.isEmpty()) {
            element.put("initial_users", initialUserIds);
        }

        return Map.of(
                "type", "input",
                "block_id", "observers_block",
                "optional", true,
                "label", plainText("참조자"),
                "element", element
        );
    }

    private MentionedUsers extractMentionedUsers(String messageText) {
        for (String line : messageText.split("\\R")) {
            Matcher assigneeMatcher = ASSIGNEE_PATTERN.matcher(line);
            if (!assigneeMatcher.find()) {
                continue;
            }

            String assigneeId = assigneeMatcher.group(1);
            String textAfterCc = line.substring(assigneeMatcher.end());
            Matcher observerMatcher = USER_MENTION_PATTERN.matcher(textAfterCc);
            List<String> observerIds = new ArrayList<>();

            while (observerMatcher.find()) {
                observerIds.add(observerMatcher.group(1));
            }

            return new MentionedUsers(assigneeId, List.copyOf(observerIds));
        }

        return new MentionedUsers(null, List.of());
    }

    private String extractTaskContent(String messageText) {
        List<String> contentLines = new ArrayList<>();

        for (String line : messageText.split("\\R")) {
            // 담당자와 참조자만 적힌 cc 줄은 업무 내용에서 제외한다.
            if (ASSIGNEE_PATTERN.matcher(line).find()) {
                continue;
            }

            // Slack 전화번호 마크업 <tel:실제값|표시값>에서는 표시값만 남긴다.
            String readableLine = TELEPHONE_PATTERN.matcher(line)
                    .replaceAll("$1")
                    .replaceAll("^[\\s\\u00A0]+|[\\s\\u00A0]+$", "");
            contentLines.add(readableLine);
        }

        return String.join("\n", contentLines)
                .replaceAll("^[\\s\\u00A0]+", "")
                .replaceAll("[\\s\\u00A0]+$", "");
    }

    private Map<String, String> plainText(String text) {
        return Map.of(
                "type", "plain_text",
                "text", text
        );
    }

    private record MentionedUsers(
            String assigneeId,
            List<String> observerIds
    ) {
    }
}
