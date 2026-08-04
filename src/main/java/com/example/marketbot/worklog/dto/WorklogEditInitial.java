package com.example.marketbot.worklog.dto;

import java.util.List;

/**
 * 기존 업무 수정 모달을 열 때 필요한 초기값을 묶어 전달합니다.
 * 여러 개의 개별 반환값 대신 하나의 명시적인 데이터 계약을 제공합니다.
 */
public record WorklogEditInitial(
        String title,
        String content,
        String type,
        String dueDate, // "YYYY-MM-DD" or null
        List<String> initialAssigneeSlackUserIds,
        List<String> initialWatcherSlackUserIds
) {}
