package com.example.worktrace.slack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Slack 업무등록 모달에서 등록 버튼을 눌렀을 때 전달되는 payload DTO다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackViewSubmissionPayload(
        String type,
        View view
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record View(
            @JsonProperty("callback_id") String callbackId,
            @JsonProperty("private_metadata") String privateMetadata,
            State state
    ) {
        public String value(String blockId, String actionId) {
            return action(blockId, actionId).value();
        }

        public String selectedUser(String blockId, String actionId) {
            return action(blockId, actionId).selectedUser();
        }

        public List<String> selectedUsers(String blockId, String actionId) {
            List<String> users = action(blockId, actionId).selectedUsers();
            return users == null ? List.of() : List.copyOf(users);
        }

        private Action action(String blockId, String actionId) {
            if (state == null || state.values() == null) {
                throw new IllegalArgumentException("Slack 모달 입력값이 없습니다.");
            }

            Map<String, Action> block = state.values().get(blockId);
            if (block == null || block.get(actionId) == null) {
                throw new IllegalArgumentException(
                        "Slack 모달 입력값을 찾을 수 없습니다: " + blockId + "/" + actionId
                );
            }

            return block.get(actionId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record State(
            Map<String, Map<String, Action>> values
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(
            String value,
            @JsonProperty("selected_user") String selectedUser,
            @JsonProperty("selected_users") List<String> selectedUsers
    ) {
    }
}
