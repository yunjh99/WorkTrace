package com.example.worktrace.slack;

import com.example.worktrace.slack.dto.SlackViewSubmissionPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlackViewSubmissionPayloadTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 모달에서_담당자_참조자_업무내용을_추출한다() {
        String json = """
                {
                  "type": "view_submission",
                  "view": {
                    "callback_id": "worktrace_task_submit",
                    "private_metadata": "{}",
                    "state": {
                      "values": {
                        "task_title_block": {
                          "task_title": {"value": "상품 정보 수정"}
                        },
                        "assignee_block": {
                          "assignee": {"selected_user": "UASSIGNEE"}
                        },
                        "observers_block": {
                          "observers": {"selected_users": ["UOBSERVER1", "UOBSERVER2"]}
                        },
                        "task_content_block": {
                          "task_content": {"value": "상품 정보를 삭제해주세요."}
                        }
                      }
                    }
                  }
                }
                """;

        SlackViewSubmissionPayload payload = objectMapper.readValue(
                json,
                SlackViewSubmissionPayload.class
        );

        assertThat(payload.view().selectedUser("assignee_block", "assignee"))
                .isEqualTo("UASSIGNEE");
        assertThat(payload.view().value("task_title_block", "task_title"))
                .isEqualTo("상품 정보 수정");
        assertThat(payload.view().selectedUsers("observers_block", "observers"))
                .isEqualTo(List.of("UOBSERVER1", "UOBSERVER2"));
        assertThat(payload.view().value("task_content_block", "task_content"))
                .isEqualTo("상품 정보를 삭제해주세요.");
    }
}
