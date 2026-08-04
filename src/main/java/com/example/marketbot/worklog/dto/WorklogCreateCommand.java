package com.example.marketbot.worklog.dto;

import com.example.marketbot.worklog.domain.Team;

import java.time.LocalDate;
import java.util.List;

/**
 * Slack 모달에서 받은 값을 업무 생성 서비스로 전달하는 불변 명령 객체입니다.
 * Slack payload 구조가 내부 업무 생성 로직으로 직접 전파되지 않게 합니다.
 */
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
