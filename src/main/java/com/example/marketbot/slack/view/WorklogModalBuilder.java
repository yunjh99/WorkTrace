package com.example.marketbot.slack.view;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ Worklog 모달(JSON) 생성 전용 Builder
 *
 * 요구사항
 * - message.text에 "@멘션 cc @멘션" 패턴이 있으면 담당자/참조자 자동 선택(initial_users)
 * - message.text 원문을 content 입력칸에 자동으로 채움(initial_value)
 */
@Component
public class WorklogModalBuilder {

    /**
     * ✅ 모달 생성
     *
     * @param privateMetadata submit 시 복구할 컨텍스트(예: channelId|messageTs)
     * @param owningTeamOptions 담당팀 옵션
     * @param initialAssignees 담당자 프리필(userId 리스트)
     * @param initialWatchers 참조자 프리필(userId 리스트)
     * @param messageText 원문 메시지 텍스트(message.text) - content에 initial_value로 주입
     */
    public Map<String, Object> build(
            String privateMetadata,
            boolean showTeamSelect,
            List<String> owningTeamOptions,
            List<String> initialAssignees,
            List<String> initialWatchers,
            String messageText
    ) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        /* =========================
         * 팀/카테고리
         * ========================= */

        // ✅ team이 null일 때만 팀 선택 블록 노출
        if (showTeamSelect) {
            blocks.add(inputSelect(
                    "team_block", "team",
                    "Team",
                    owningTeamOptions,
                    "담당팀을 선택해주세요.",
                    null
            ));
            blocks.add(sectionHint("처음 사용자는 담당팀을 1회 설정해주세요. 이후 자동으로 적용됩니다."));
        }
        /* =========================
         * 담당자/참조자
         * ========================= */

        blocks.add(inputMultiUsersSelect(
                "assignee_block", "assignees",
                "Assignee",
                "담당자를 선택하세요.",   // ✅
                false,
                initialAssignees
        ));

        blocks.add(inputMultiUsersSelect(
                "watcher_block", "watchers",
                "Watcher",
                "참조자를 선택하세요.",   // ✅
                true,
                initialWatchers
        ));
        /* =========================
         * 기본 입력
         * ========================= */

        // type
        blocks.add(inputSelect(
                "type_block", "type",
                "Type",
                List.of("업체문의", "영양사문의", "개인업무"),
                "업무유형을 선택해주세요.",
                null
        ));

        blocks.add(inputText(
                "title_block", "title",
                "Title", "업무 제목을 입력하세요",
                false, false,
                null
        ));

        blocks.add(inputText(
                "content_block", "content",
                "Content", "업무 내용을 입력하세요",
                true, false,
                messageText
        ));

        /* =========================
         * 상태/유형/마감일
         * ========================= */

        blocks.add(inputDate("due_block", "due_date", "Due Date", true));

        //blocks.add(sectionHint("요청자(Requester)와 Slack 링크는 원문 메시지 기준으로 자동 지정됩니다."));

        return Map.of(
                "type", "modal",
                "callback_id", "worklog_create",
                "title", Map.of("type", "plain_text", "text", "업무기록"),
                "submit", Map.of("type", "plain_text", "text", "저장"),
                "close", Map.of("type", "plain_text", "text", "취소"),
                "private_metadata", privateMetadata,
                "blocks", blocks
        );
    }

    /* =========================
     *  block builder helpers
     * ========================= */

    /**
     * ✅ 단일 텍스트 입력 (plain_text_input)
     *
     * - initialValue를 주면 모달이 열릴 때 기본 값으로 자동 채워짐
     *
     * view_submission에서 읽는 경로:
     * values[blockId][actionId].value
     */
    private Map<String, Object> inputText(
            String blockId,
            String actionId,
            String label,
            String placeholder,
            boolean multiline,
            boolean optional,
            String initialValue
    ) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "plain_text_input");
        element.put("action_id", actionId);
        element.put("multiline", multiline);
        element.put("placeholder", Map.of("type", "plain_text", "text", placeholder));

        // ✅ message.text 같은 원문을 initial_value로 미리 주입
        // (Slack Block Kit: plain_text_input supports initial_value)
        if (initialValue != null && !initialValue.isBlank()) {
            element.put("initial_value", initialValue);
        }

        return Map.of(
                "type", "input",
                "block_id", blockId,
                "optional", optional,
                "label", Map.of("type", "plain_text", "text", label),
                "element", element
        );
    }

    // ---- 이하 helper들은 기존 그대로 ----

    private Map<String, Object> inputSelect(
            String blockId,
            String actionId,
            String label,
            List<String> options,
            String placeholderText,   // ✅ 추가
            String initial
    ) {
        List<Map<String, Object>> slackOptions = options.stream()
                .map(v -> Map.of(
                        "text", Map.of("type", "plain_text", "text", v),
                        "value", v
                ))
                .toList();

        Map<String, Object> element = new HashMap<>();
        element.put("type", "static_select");
        element.put("action_id", actionId);
        element.put("options", slackOptions);

        // ✅ placeholder 추가
        element.put("placeholder",
                Map.of("type", "plain_text", "text", placeholderText));

        // initial 값이 있을 경우 기본 선택값 지정
        if (initial != null) {
            element.put("initial_option", Map.of(
                    "text", Map.of("type", "plain_text", "text", initial),
                    "value", initial
            ));
        }

        return Map.of(
                "type", "input",
                "block_id", blockId,
                "label", Map.of("type", "plain_text", "text", label),
                "element", element
        );
    }


    private Map<String, Object> inputMultiSelect(
            String blockId,
            String actionId,
            String label,
            List<String> options,
            boolean optional
    ) {
        List<Map<String, Object>> slackOptions = options.stream()
                .map(v -> Map.of("text", Map.of("type", "plain_text", "text", v), "value", v))
                .toList();

        return Map.of(
                "type", "input",
                "block_id", blockId,
                "optional", optional,
                "label", Map.of("type", "plain_text", "text", label),
                "element", Map.of(
                        "type", "multi_static_select",
                        "action_id", actionId,
                        "options", slackOptions
                )
        );
    }

    private Map<String, Object> inputDate(String blockId, String actionId, String label, boolean optional) {
        return Map.of(
                "type", "input",
                "block_id", blockId,
                "optional", optional,
                "label", Map.of("type", "plain_text", "text", label),
                "element", Map.of(
                        "type", "datepicker",
                        "action_id", actionId,
                        "placeholder", Map.of("type", "plain_text", "text", "날짜 선택")
                )
        );
    }

    private Map<String, Object> inputMultiUsersSelect(
            String blockId,
            String actionId,
            String label,
            String placeholderText,   // ✅ 추가
            boolean optional,
            List<String> initialUsers
    ) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "multi_users_select");
        element.put("action_id", actionId);
        element.put("placeholder", Map.of("type", "plain_text", "text", placeholderText)); // ✅ 변경

        if (initialUsers != null && !initialUsers.isEmpty()) {
            element.put("initial_users", initialUsers);
        }

        return Map.of(
                "type", "input",
                "block_id", blockId,
                "optional", optional,
                "label", Map.of("type", "plain_text", "text", label),
                "element", element
        );
    }


    private Map<String, Object> sectionHint(String text) {
        return Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", "ℹ️ " + text)
        );
    }

    public Map<String, Object> buildForEdit(
            String privateMetadata,
            boolean showTeamSelect,
            List<String> owningTeamOptions,
            List<String> initialAssignees,
            List<String> initialWatchers,
            String initialContent,
            String initialType,
            String initialTitle,
            String initialDueDate // "YYYY-MM-DD" or null
    ) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        if (showTeamSelect) {
            blocks.add(inputSelect(
                    "team_block", "team",
                    "Team",
                    owningTeamOptions,
                    "담당팀을 선택해주세요.",
                    null
            ));
            blocks.add(sectionHint("처음 사용자는 담당팀을 1회 설정해주세요. 이후 자동으로 적용됩니다."));
        }

        blocks.add(inputMultiUsersSelect(
                "assignee_block", "assignees",
                "Assignee",
                "담당자를 선택하세요.",
                false,
                initialAssignees
        ));

        blocks.add(inputMultiUsersSelect(
                "watcher_block", "watchers",
                "Watcher",
                "참조자를 선택하세요.",
                true,
                initialWatchers
        ));

        blocks.add(inputSelect(
                "type_block", "type",
                "Type",
                List.of("업체문의", "영양사문의", "개인업무"),
                "업무유형을 선택해주세요.",
                (initialType != null && !initialType.isBlank()) ? initialType : null
        ));

        blocks.add(inputText(
                "title_block", "title",
                "Title", "업무 제목을 입력하세요",
                false, false,
                initialTitle
        ));

        blocks.add(inputText(
                "content_block", "content",
                "Content", "업무 내용을 입력하세요",
                true, false,
                initialContent
        ));

        blocks.add(inputDateWithInitial(
                "due_block", "due_date",
                "Due Date",
                true,
                initialDueDate
        ));

        return Map.of(
                "type", "modal",
                "callback_id", "worklog_edit", // ✅ 수정용 callback_id
                "title", Map.of("type", "plain_text", "text", "업무수정"),
                "submit", Map.of("type", "plain_text", "text", "저장"),
                "close", Map.of("type", "plain_text", "text", "취소"),
                "private_metadata", privateMetadata,
                "blocks", blocks
        );
    }
    private Map<String, Object> inputDateWithInitial(
            String blockId,
            String actionId,
            String label,
            boolean optional,
            String initialDate
    ) {
        Map<String, Object> element = new HashMap<>();
        element.put("type", "datepicker");
        element.put("action_id", actionId);
        element.put("placeholder", Map.of("type", "plain_text", "text", "날짜 선택"));

        if (initialDate != null && !initialDate.isBlank()) {
            element.put("initial_date", initialDate); // ✅ 핵심
        }

        return Map.of(
                "type", "input",
                "block_id", blockId,
                "optional", optional,
                "label", Map.of("type", "plain_text", "text", label),
                "element", element
        );
    }

}
