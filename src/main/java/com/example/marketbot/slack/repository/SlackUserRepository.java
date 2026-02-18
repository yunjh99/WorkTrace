package com.example.marketbot.slack.repository;

import com.example.marketbot.slack.domain.SlackUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlackUserRepository extends JpaRepository<SlackUser, Long> {

    Optional<SlackUser> findBySlackUserId(String slackUserId);
    Optional<SlackUser> findByDisplayName(String displayName);

}
