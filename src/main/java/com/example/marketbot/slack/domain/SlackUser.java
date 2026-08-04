package com.example.marketbot.slack.domain;

import com.example.marketbot.worklog.domain.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Slack 사용자의 식별자, 표시 이름, 소속 팀을 저장하는 엔티티입니다.
 * 담당자 배정과 멘션 표시 시 매번 Slack API를 조회하지 않도록 로컬 기준 정보를 제공합니다.
 */
@Entity
@Table(
        name = "slack_user",
        indexes = {
                @Index(name = "idx_slack_user_slack_user_id", columnList = "slackUserId", unique = true),
                @Index(name = "idx_slack_user_updated_at", columnList = "updatedAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SlackUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String slackUserId;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 100)
    private String realName;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 30)
    private Team team;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(nullable = false)
    private Boolean bot = false;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
