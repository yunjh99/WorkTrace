package com.example.marketbot.slack_notion_link.repository;

import com.example.marketbot.slack_notion_link.domain.SlackNotionLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlackNotionLinkRepository extends JpaRepository<SlackNotionLink, Long> {

    Optional<SlackNotionLink> findBySlackChannelIdAndSlackMessageTs(String slackChannelId, String slackMessageTs);

    // ✅ 추가
    Optional<SlackNotionLink> findBySlackChannelIdAndSlackBotMessageTs(String slackChannelId, String slackBotMessageTs);
    List<SlackNotionLink> findAllBySlackChannelIdAndSlackThreadTs(String channelId, String threadTs);
}

