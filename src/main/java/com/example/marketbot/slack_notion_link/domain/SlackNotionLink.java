package com.example.marketbot.slack_notion_link.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "slack_notion_link",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_slack_message",
                        columnNames = {"slack_channel_id", "slack_message_ts"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SlackNotionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slack_channel_id", length = 32, nullable = false)
    private String slackChannelId;

    @Column(name = "slack_message_ts", length = 32, nullable = false)
    private String slackMessageTs;

    @Column(name = "slack_thread_ts", length = 32)
    private String slackThreadTs;

    // ✅ 봇이 thread에 남긴 “진행/완료 컨트롤 메시지”의 ts
    @Column(name = "slack_bot_message_ts", length = 32)
    private String slackBotMessageTs;

    @Column(name = "notion_page_id", length = 64, nullable = false)
    private String notionPageId;

    @Column(name = "receiver_slack_user_id", length = 32)
    private String receiverSlackUserId;

    @Column(name = "worklog_title", length = 255)
    private String worklogTitle;

    @Column(name = "worklog_status", length = 16)
    private String worklogStatus;   // "접수" / "진행" / "완료" (선택이지만 추천)

    @OneToMany(mappedBy = "link", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlackNotionLinkAssignee> assignees = new ArrayList<>();
}
