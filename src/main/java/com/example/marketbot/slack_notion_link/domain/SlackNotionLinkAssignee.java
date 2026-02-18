package com.example.marketbot.slack_notion_link.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
