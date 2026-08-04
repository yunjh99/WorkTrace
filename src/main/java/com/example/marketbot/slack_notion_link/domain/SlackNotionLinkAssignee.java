package com.example.marketbot.slack_notion_link.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 하나의 접수 업무와 여러 담당자 사이의 다대다 관계를 표현하는 연결 엔티티입니다.
 * 담당자별 상태 계산과 개인 업무 현황 조회를 위해 관계를 별도 행으로 저장합니다.
 */
@Entity
@Table(
        name = "slack_notion_link_assignee",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_link_assignee",
                        columnNames = {"slack_notion_link_id", "slack_user_id"}
                )
        },
        indexes = {
                @Index(name = "ix_assignee_user", columnList = "slack_user_id"),
                @Index(name = "ix_assignee_link", columnList = "slack_notion_link_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SlackNotionLinkAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slack_notion_link_id", nullable = false)
    private SlackNotionLink link;

    @Column(name = "slack_user_id", length = 32, nullable = false)
    private String slackUserId;
}
