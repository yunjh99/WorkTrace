package com.example.worktrace.slack;

import com.example.worktrace.slack.dto.SlackShortcutPayload;
import com.example.worktrace.slack.dto.SlackViewOpenRequest;
import com.example.worktrace.slack.modal.SlackModalFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlackModalFactoryTest {

    private final SlackModalFactory slackModalFactory =
            new SlackModalFactory(JsonMapper.builder().build());

    @Test
    void cc_앞뒤_멘션과_선택한_메시지를_모달_초기값으로_설정한다() {
        SlackShortcutPayload payload = new SlackShortcutPayload(
                "message_action",
                "worktrace_create_task",
                "trigger-123",
                new SlackShortcutPayload.User("U-REGISTER", "tester"),
                new SlackShortcutPayload.Channel("C-WORK", "업무요청"),
                new SlackShortcutPayload.Message(
                        "U-REQUESTER",
                        "<@UASSIGNEE> cc <@UOBSERVER1> <@UOBSERVER2>\n EE업체\n <tel:010-2304-2401|010-2304-2401>\n 상품 정보를 삭제해도 삭제되지 않음.",
                        "1234567890.123456"
                )
        );

        SlackViewOpenRequest request = slackModalFactory.createTaskModal(payload);

        assertThat(request.triggerId()).isEqualTo("trigger-123");

        List<Map<String, Object>> blocks = blocksOf(request.view());
        Map<String, Object> titleElement = elementOf(blocks.get(0));
        Map<String, Object> contentElement = elementOf(blocks.get(1));
        Map<String, Object> assigneeElement = elementOf(blocks.get(2));
        Map<String, Object> observersElement = elementOf(blocks.get(3));

        assertThat(titleElement).doesNotContainKey("initial_value");
        assertThat(contentElement.get("initial_value"))
                .isEqualTo("EE업체\n010-2304-2401\n상품 정보를 삭제해도 삭제되지 않음.");
        assertThat(assigneeElement.get("initial_user"))
                .isEqualTo("UASSIGNEE");
        assertThat(observersElement.get("initial_users"))
                .isEqualTo(List.of("UOBSERVER1", "UOBSERVER2"));
    }

    @Test
    void cc_멘션이_없으면_담당자와_참조자_초기값을_생략한다() {
        SlackShortcutPayload payload = new SlackShortcutPayload(
                "message_action",
                "worktrace_create_task",
                "trigger-123",
                new SlackShortcutPayload.User("U-REGISTER", "tester"),
                new SlackShortcutPayload.Channel("C-WORK", "업무요청"),
                new SlackShortcutPayload.Message(
                        "U-REQUESTER",
                        "상품 정보를 수정해주세요.",
                        "1234567890.123456"
                )
        );

        SlackViewOpenRequest request = slackModalFactory.createTaskModal(payload);
        List<Map<String, Object>> blocks = blocksOf(request.view());

        assertThat(elementOf(blocks.get(2))).doesNotContainKey("initial_user");
        assertThat(elementOf(blocks.get(3))).doesNotContainKey("initial_users");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> blocksOf(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("blocks");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> elementOf(Map<String, Object> block) {
        return (Map<String, Object>) block.get("element");
    }
}
