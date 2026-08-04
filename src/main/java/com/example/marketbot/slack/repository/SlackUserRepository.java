package com.example.marketbot.slack.repository;

import com.example.marketbot.slack.domain.SlackUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Slack 사용자를 내부 식별자, Slack 사용자 ID 또는 표시 이름으로 조회하고 저장합니다.
 */
public interface SlackUserRepository extends JpaRepository<SlackUser, Long> {

    Optional<SlackUser> findBySlackUserId(String slackUserId);
    Optional<SlackUser> findByDisplayName(String displayName);

}
