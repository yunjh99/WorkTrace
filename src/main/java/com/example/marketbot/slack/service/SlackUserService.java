package com.example.marketbot.slack.service;

import com.example.marketbot.slack.client.SlackClient;
import com.example.marketbot.slack.domain.SlackUser;
import com.example.marketbot.slack.repository.SlackUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slack 사용자 정보를 조회하고 일정 시간 동안 로컬 DB에 캐시합니다.
 * 사용자 이름과 멘션 변환 과정에서 Slack API 호출이 반복되는 것을 줄입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlackUserService {

    private final SlackUserRepository slackUserRepository;
    private final SlackClient slackClient;

    private static final Duration USER_TTL = Duration.ofDays(7);
    private static final Pattern MENTION = Pattern.compile("<@([A-Z0-9]+)>");

    /**
     * ✅ message.text의 "<@U...>" 를 "@displayName"으로 치환
     * - 내부에서 userId별로 resolveDisplayName 호출
     */
    public String replaceMentionsWithDisplayName(String text) {
        if (text == null || text.isBlank()) return text;

        Matcher matcher = MENTION.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String userId = matcher.group(1);
            String displayName = resolveDisplayName(userId);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("@" + displayName));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * ✅ userId -> displayName
     * - TTL 이내면 DB 값 사용
     * - 없거나 TTL 지났으면 users.info 호출 -> DB 업서트
     */
    public String resolveDisplayName(String userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime freshAfter = now.minus(USER_TTL);

        return slackUserRepository.findBySlackUserId(userId)
                .map(u -> {
                    if (u.getUpdatedAt() != null && u.getUpdatedAt().isAfter(freshAfter)) {
                        return u.getDisplayName();
                    }
                    return refreshUserFromSlack(userId, u);
                })
                .orElseGet(() -> refreshUserFromSlack(userId, null));
    }

    private String refreshUserFromSlack(String userId, SlackUser existing) {
        try {
            JsonNode res = slackClient.usersInfo(userId); // users:read scope 필요
            JsonNode user = res.path("user");
            JsonNode profile = user.path("profile");

            String display = profile.path("display_name").asText("");
            String real = profile.path("real_name").asText("");

            String name = (display != null && !display.isBlank()) ? display : real;
            if (name == null || name.isBlank()) name = userId;

            SlackUser entity = (existing != null) ? existing : new SlackUser();
            entity.setSlackUserId(userId);
            entity.setDisplayName(name);
            entity.setRealName(real);
            entity.setDeleted(user.path("deleted").asBoolean(false));
            entity.setBot(user.path("is_bot").asBoolean(false));
            // updatedAt은 엔티티 @PrePersist/@PreUpdate로 자동 갱신 추천

            slackUserRepository.save(entity);

            return name;

        } catch (Exception e) {
            log.warn("Failed to refresh Slack user. userId={}, using cached value when available", userId, e);
            if (existing != null && existing.getDisplayName() != null && !existing.getDisplayName().isBlank()) {
                return existing.getDisplayName();
            }
            return userId;
        }
    }
}
