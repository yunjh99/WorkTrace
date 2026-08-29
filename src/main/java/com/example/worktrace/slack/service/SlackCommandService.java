package com.example.worktrace.slack.service;

import com.example.worktrace.notion.client.NotionClient;
import com.example.worktrace.notion.client.NotionClient.WorkTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlackCommandService {

    private static final String MY_TASKS_COMMAND = "/내업무";
    private static final String TEAM_TASKS_COMMAND = "/팀업무";
    private static final String COMPLETED = "완료";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_SECTION_LENGTH = 2900;

    private final NotionClient notionClient;

    public Map<String, Object> handle(String command, String userId) {
        List<WorkTask> visibleTasks = notionClient.findAllTasks().stream()
                .filter(this::isIncompleteOrCompletedToday)
                .sorted(Comparator.comparingInt(this::statusOrder)
                        .thenComparing(task -> safe(task.title())))
                .toList();

        if (MY_TASKS_COMMAND.equals(command)) {
            return response("내 업무 현황", personalText(visibleTasks, userId));
        }
        if (TEAM_TASKS_COMMAND.equals(command)) {
            return response("팀 업무 현황", teamText(visibleTasks));
        }
        return response("WorkTrace", "지원하지 않는 커맨드입니다. `/내업무` 또는 `/팀업무`를 사용해주세요.");
    }

    private String personalText(List<WorkTask> tasks, String userId) {
        List<WorkTask> mine = tasks.stream()
                .filter(task -> userId != null && userId.equals(task.assigneeSlackId()))
                .toList();

        List<WorkTask> incomplete = mine.stream()
                .filter(task -> !COMPLETED.equals(task.status()))
                .toList();
        List<WorkTask> completed = mine.stream()
                .filter(task -> COMPLETED.equals(task.status()))
                .toList();

        StringBuilder text = new StringBuilder("*[금일 미완료 업무]*");
        appendTasks(text, incomplete, true);
        text.append("\n\n*[금일 완료 업무]*");
        appendTasks(text, completed, false);
        return limit(text.toString());
    }

    private String teamText(List<WorkTask> tasks) {
        StringBuilder text = new StringBuilder();
        Map<String, List<WorkTask>> byAssignee = new LinkedHashMap<>();
        for (WorkTask task : tasks) {
            String assignee = safe(task.assigneeName()).isBlank() ? "담당자 미지정" : task.assigneeName();
            byAssignee.computeIfAbsent(assignee, ignored -> new ArrayList<>()).add(task);
        }

        byAssignee.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
            String assignee = entry.getKey();
            List<WorkTask> assignedTasks = entry.getValue();
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("*[").append(escape(assignee)).append("]*");
            assignedTasks.stream().limit(5).forEach(task -> text.append("\n- ")
                    .append(taskLink(task))
                    .append(" (상태 : ").append(safe(task.status())).append(")"));
            if (assignedTasks.size() > 5) {
                text.append("\n… 외 ").append(assignedTasks.size() - 5).append("건");
            }
        });

        if (tasks.isEmpty()) {
            text.append("\n표시할 업무가 없습니다.");
        }
        return limit(text.toString());
    }

    private void appendTasks(StringBuilder text, List<WorkTask> tasks, boolean showStatus) {
        if (tasks.isEmpty()) {
            text.append("\n- 없음");
            return;
        }
        tasks.forEach(task -> {
            text.append("\n- ").append(taskLink(task));
            if (showStatus) {
                text.append(" (상태 : ").append(safe(task.status())).append(")");
            }
        });
    }

    private boolean isIncompleteOrCompletedToday(WorkTask task) {
        if (!COMPLETED.equals(task.status())) {
            return true;
        }
        try {
            return Instant.parse(task.lastEditedTime()).atZone(KOREA).toLocalDate()
                    .equals(LocalDate.now(KOREA));
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    private int statusOrder(WorkTask task) {
        return switch (safe(task.status())) {
            case "접수" -> 0;
            case "진행중" -> 1;
            case COMPLETED -> 2;
            default -> 3;
        };
    }

    private String taskLink(WorkTask task) {
        String title = escape(safe(task.title()).isBlank() ? "제목 없음" : task.title());
        if (task.slackMessageUrl() == null || task.slackMessageUrl().isBlank()) {
            return title;
        }
        return "<" + task.slackMessageUrl() + "|" + title + ">";
    }

    private Map<String, Object> response(String title, String sectionText) {
        return Map.of(
                "response_type", "ephemeral",
                "text", title,
                "blocks", List.of(
                        Map.of("type", "header", "text", Map.of("type", "plain_text", "text", title)),
                        Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", sectionText))
                )
        );
    }

    private String limit(String text) {
        return text.length() <= MAX_SECTION_LENGTH
                ? text
                : text.substring(0, MAX_SECTION_LENGTH - 20) + "\n… 일부 생략됨";
    }

    private String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
