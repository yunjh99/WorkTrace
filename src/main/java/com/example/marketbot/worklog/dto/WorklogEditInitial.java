package com.example.marketbot.worklog.dto;

import java.util.List;

public record WorklogEditInitial(
        String title,
        String content,
        String type,
        String dueDate, // "YYYY-MM-DD" or null
        List<String> initialAssigneeSlackUserIds,
        List<String> initialWatcherSlackUserIds
) {}