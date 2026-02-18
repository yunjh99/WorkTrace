package com.example.marketbot.slack.view;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WorklogReceiptMessageBuilder {

    private static final String ACTION_PROGRESS = "worklog_progress";
    private static final String ACTION_COMPLETE = "worklog_complete";
    private static final String ACTION_RESET    = "worklog_reset";
    private static final String ACTION_EDIT = "worklog_edit";

    public List<Map<String, Object>> buildReceiptBlocks(
            String receiverUserId,
            String title,
            List<String> assigneeUserIds,
            List<String> watcherUserIds,   // ✅ 추가
            Long linkId,
            String status,
            String dateLine
    ) {

        String receiverMention = "<@" + receiverUserId + ">";

        String assigneeMentions = (assigneeUserIds == null || assigneeUserIds.isEmpty())
                ? "(없음)"
                : assigneeUserIds.stream()
                .distinct()
                .map(id -> "<@" + id + ">")
                .reduce((a, b) -> a + ", " + b)
                .orElse("(없음)");

        String watcherMentions = (watcherUserIds == null || watcherUserIds.isEmpty())
                ? "(없음)"
                : watcherUserIds.stream()
                .distinct()
                .map(id -> "<@" + id + ">")
                .reduce((a, b) -> a + ", " + b)
                .orElse("(없음)");

        String watcherLine = "완료".equals(status)
                ? "\n참조자: " + watcherMentions
                : "";


        // ✅ 상태별 이모지 + 텍스트
        String statusLine;
        if ("진행".equals(status)) {
            statusLine = "🟡 *현재 상태: 진행*";
        } else if ("완료".equals(status)) {
            statusLine = "🟢 *현재 상태: 완료*";
        } else {
            statusLine = "📥 *현재 상태: 접수*";
        }

        String headerText =
                statusLine + "\n" +
                        receiverMention + "님이 *[" + title + "]* 업무를 등록했습니다.\n" +
                        "담당자: " + assigneeMentions +
                        watcherLine +
                        (dateLine != null ? "\n" + dateLine : "");

        List<Map<String, Object>> blocks = new ArrayList<>();

        // ✅ 상단 정보 블록
        blocks.add(
                Map.of(
                        "type", "section",
                        "text", Map.of("type", "mrkdwn", "text", headerText)
                )
        );

        // ✅ 상태별 버튼 구성
        List<Map<String, Object>> buttons = new ArrayList<>();

        if ("접수".equals(status)) {

            buttons.add(buildButton("✏️ 수정하기", null, ACTION_EDIT, linkId)); // ✅ 추가
            buttons.add(buildButton("👀 진행하기", "primary", ACTION_PROGRESS, linkId));
            buttons.add(buildButton("✅ 완료하기", "danger", ACTION_COMPLETE, linkId));

        } else if ("진행".equals(status)) {

            buttons.add(buildButton("✅ 완료하기", "danger", ACTION_COMPLETE, linkId));
            buttons.add(buildButton("↩ 접수로 되돌리기", null, ACTION_RESET, linkId));

        } else if ("완료".equals(status)) {

            buttons.add(buildButton("↩ 접수로 되돌리기", null, ACTION_RESET, linkId));
        }

        if (!buttons.isEmpty()) {
            blocks.add(
                    Map.of(
                            "type", "actions",
                            "elements", buttons
                    )
            );
        }

        return blocks;
    }

    private Map<String, Object> buildButton(String text,
                                            String style,
                                            String actionId,
                                            Long linkId) {

        Map<String, Object> btn = Map.of(
                "type", "button",
                "text", Map.of("type", "plain_text", "text", text),
                "action_id", actionId,
                "value", String.valueOf(linkId)
        );

        if (style != null) {
            return Map.of(
                    "type", "button",
                    "text", Map.of("type", "plain_text", "text", text),
                    "action_id", actionId,
                    "value", String.valueOf(linkId),
                    "style", style
            );
        }

        return btn;
    }
}
