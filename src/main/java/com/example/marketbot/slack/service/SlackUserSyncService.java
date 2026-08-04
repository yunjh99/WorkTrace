package com.example.marketbot.slack.service;

import com.example.marketbot.slack.client.SlackClient;
import com.example.marketbot.slack.domain.SlackUser;
import com.example.marketbot.slack.repository.SlackUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 워크스페이스의 활성 사용자 목록을 Slack API에서 읽어 로컬 DB와 동기화합니다.
 * 담당자 선택과 팀 매핑에 사용할 사용자 기준 데이터를 최신 상태로 유지합니다.
 */
@Service
@RequiredArgsConstructor
public class SlackUserSyncService {

    private final SlackClient slackClient;
    private final SlackUserRepository slackUserRepository;

    @Transactional
    public int syncAllUsers() {
        int upserted = 0;

        String cursor = null;
        while (true) {
            JsonNode res = slackClient.usersList(200, cursor);

            JsonNode members = res.path("members");
            if (members.isArray()) {
                for (JsonNode m : members) {

                    boolean deleted = m.path("deleted").asBoolean(false);
                    boolean bot = m.path("is_bot").asBoolean(false);

                    // ✅ 비활성(deleted)만 스킵. 봇은 저장한다.
                    if (deleted) continue;

                    String userId = m.path("id").asText("");
                    if (userId.isBlank()) continue;

                    JsonNode profile = m.path("profile");

                    // 사람/봇 모두에서 최대한 이름을 채우기
                    String displayName = profile.path("display_name_normalized").asText("");
                    String realName = profile.path("real_name_normalized").asText("");

                    if (displayName.isBlank()) displayName = profile.path("display_name").asText("");
                    if (displayName.isBlank()) displayName = realName;
                    if (displayName.isBlank()) displayName = m.path("name").asText("");
                    if (displayName.isBlank()) displayName = userId;

                    SlackUser user = slackUserRepository.findBySlackUserId(userId)
                            .orElseGet(SlackUser::new);

                    user.setSlackUserId(userId);
                    user.setDisplayName(displayName);
                    user.setRealName(realName.isBlank() ? null : realName);

                    user.setDeleted(false); // deleted는 위에서 걸러서 false 확정
                    user.setBot(bot);       // ✅ 봇 여부 저장
                    user.setUpdatedAt(LocalDateTime.now());

                    slackUserRepository.save(user);
                    upserted++;
                }
            }

            cursor = res.path("response_metadata").path("next_cursor").asText("");
            if (cursor.isBlank()) break;
        }

        return upserted;
    }
}
