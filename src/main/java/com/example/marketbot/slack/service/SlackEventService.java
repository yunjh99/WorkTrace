package com.example.marketbot.slack.service;

import com.example.marketbot.notion.service.NotionWorklogService;
import com.example.marketbot.slack.client.SlackClient;
import com.example.marketbot.slack_notion_link.domain.SlackNotionLink;
import com.example.marketbot.slack_notion_link.repository.SlackNotionLinkAssigneeRepository;
import com.example.marketbot.slack_notion_link.repository.SlackNotionLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class SlackEventService {

    private static final String EMOJI_EYES = "eyes";
    private static final String EMOJI_CHECK = "white_check_mark";

    private final SlackNotionLinkRepository slackNotionLinkRepository;
    private final SlackNotionLinkAssigneeRepository slackNotionLinkAssigneeRepository;
    private final NotionWorklogService notionWorklogService;
    private final SlackClient slackClient;

    /**
     * Slack Events API 진입점
     */
    public void handle(JsonNode payload) {

        // Slack event_callback만 처리
        if (!"event_callback".equals(payload.path("type").asText())) {
            return;
        }

        JsonNode event = payload.path("event");
        String eventType = event.path("type").asText();

        if ("reaction_added".equals(eventType)) {
            handleReactionChanged(event, true);
        } else if ("reaction_removed".equals(eventType)) {
            handleReactionChanged(event, false);
        }
    }

    /**
     * ✅ A안(우선순위) 기준 공통 처리
     * - 관심 이모지: eyes, white_check_mark
     * - 비담당자: 눌렀으면 제거(added만), 상태 계산에는 반영 X
     * - 담당자: added/removed 모두 "재계산"으로 Notion status 업데이트
     *
     * 우선순위: ✅(완료) > 👀(진행) > 접수
     */
    private void handleReactionChanged(JsonNode event, boolean isAdded) {

        String channelId = event.path("item").path("channel").asText();
        String itemTs = event.path("item").path("ts").asText(); // ✅ 이모지가 달린 '그 메시지' ts
        String reactorUserId = event.path("user").asText();
        String reaction = event.path("reaction").asText();

        // 관심 없는 이모지면 무시
        if (!EMOJI_EYES.equals(reaction) && !EMOJI_CHECK.equals(reaction)) {
            return;
        }

        // ✅ 1) bot message ts 기준으로 SlackNotionLink 조회
        SlackNotionLink link = slackNotionLinkRepository
                .findBySlackChannelIdAndSlackBotMessageTs(channelId, itemTs)
                .orElse(null);

        if (link == null) {
            return; // ✅ 원본 메시지/일반 메시지에 달린 리액션은 전부 무시
        }

        // 2) 담당자 권한 체크
        boolean isAssignee = slackNotionLinkAssigneeRepository
                .existsByLink_IdAndSlackUserId(link.getId(), reactorUserId);

        // 비담당자 처리
        if (!isAssignee) {

            if (isAdded) {
                String permalink = slackClient.chatGetPermalink(channelId, itemTs);

                slackClient.chatPostEphemeral(
                        channelId,
                        reactorUserId,
                        "👀/✅ 상태 이모지는 담당자만 사용할 수 있어요.\n" +
                                "현재 상태에는 반영되지 않습니다.\n\n" +
                                "👉 <" + permalink + "|해당 메시지로 이동해서 제거하기>"
                );
            }
            return;
        }

        // ✅ 3) 담당자라면: bot 컨트롤 메시지의 reactions 기준으로 상태 재계산
        String status = recomputeStatusByAssigneeReactions(channelId, itemTs, link.getId());

        // 4) Notion 상태 변경
        notionWorklogService.updateStatus(link.getNotionPageId(), status);
    }

    /**
     * 메시지의 현재 reactions를 Slack에서 재조회하고,
     * "담당자가 남긴 eyes/check"만 기준으로 상태를 결정한다.
     *
     * 우선순위: ✅(완료) > 👀(진행) > 접수
     */
    private String recomputeStatusByAssigneeReactions(String channelId, String messageTs, Long linkId) {

        // 담당자 목록 Set
        Set<String> assigneeIds = slackNotionLinkAssigneeRepository.findSlackUserIdsByLinkId(linkId);

        JsonNode res = slackClient.reactionsGet(channelId, messageTs);
        JsonNode reactions = res.path("message").path("reactions");

        // ✅가 있으면 무조건 완료
        if (hasAssigneeReaction(reactions, EMOJI_CHECK, assigneeIds)) {
            return "완료";
        }

        // ✅ 없고 👀 있으면 진행
        if (hasAssigneeReaction(reactions, EMOJI_EYES, assigneeIds)) {
            return "진행";
        }

        // 둘 다 없으면 접수
        return "접수";
    }

    private boolean hasAssigneeReaction(JsonNode reactions, String emoji, Set<String> assigneeIds) {
        if (reactions == null || !reactions.isArray()) return false;

        for (JsonNode r : reactions) {
            if (!emoji.equals(r.path("name").asText())) continue;

            JsonNode users = r.path("users");
            if (users == null || !users.isArray()) continue;

            for (JsonNode u : users) {
                if (assigneeIds.contains(u.asText())) return true;
            }
        }
        return false;
    }
}
