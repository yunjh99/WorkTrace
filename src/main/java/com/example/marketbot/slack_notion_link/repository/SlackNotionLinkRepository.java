package com.example.marketbot.slack_notion_link.repository;

import com.example.marketbot.slack_notion_link.domain.SlackNotionLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 접수 업무를 Slack 메시지 위치와 내부 ID로 조회하고 저장합니다.
 * Slack 이벤트가 어느 업무에 대한 것인지 찾기 위한 조회 경로를 제공합니다.
 */
public interface SlackNotionLinkRepository extends JpaRepository<SlackNotionLink, Long> {

    Optional<SlackNotionLink> findBySlackChannelIdAndSlackMessageTs(String slackChannelId, String slackMessageTs);

    // ✅ 추가
    Optional<SlackNotionLink> findBySlackChannelIdAndSlackBotMessageTs(String slackChannelId, String slackBotMessageTs);
    List<SlackNotionLink> findAllBySlackChannelIdAndSlackThreadTs(String channelId, String threadTs);
}

