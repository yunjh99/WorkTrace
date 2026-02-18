package com.example.marketbot.worklog.dto;

import com.example.marketbot.worklog.domain.Team;

import java.time.LocalDate;
import java.util.List;

public record WorklogCreateCommand(
        Team owningTeam,
        String status,
        String title,
        String content,
        String type,
        List<String> assigneeNames, // displayName 리스트
        List<String> watcherNames,  // displayName 리스트
        String channelId,
        String messageTs,           // shortcut 누른 메시지 ts
        String threadTs,            // 쓰레드 루트 ts (없으면 messageTs)
        String receiverName,        // ✅ 접수자(displayName) - null 허용 가능
        String slackLink,            // ✅ Slack permalink
        LocalDate dueDate          // ✅ 추가
) {}
