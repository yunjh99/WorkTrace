package com.example.marketbot.slack.view;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.*;

/**
 * 개인별 미완료 업무와 완료 업무 현황을 Slack 메시지 형태로 구성합니다.
 * 조회 결과를 Block Kit 표현으로 변환하는 책임만 담당합니다.
 */
@Component
@RequiredArgsConstructor
public class WorklogStatusMessageBuilder {

    private static final String PROP_TITLE = "Title";
    private static final String PROP_STATUS = "Status";
    private static final String PROP_SLACK_LINK = "Slack Link";
    private static final String PROP_DUE_DATE = "Due Date";

    public Map<String, Object> build(String displayName, JsonNode todoRes, JsonNode doneRes) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        blocks.add(header("📊 업무현황 - " + displayName));
        blocks.add(divider());

        List<String> todoLines = toLines(todoRes);
        blocks.add(section("*[금일 미완료 업무]*  (" + todoLines.size() + "건)\n" +
                (todoLines.isEmpty() ? "없음" : String.join("\n", todoLines))));

        blocks.add(divider());

        List<String> doneLines = toLines(doneRes);
        blocks.add(section("*[금일 완료 업무]*  (" + doneLines.size() + "건)\n" +
                (doneLines.isEmpty() ? "없음" : String.join("\n", doneLines))));

        return Map.of(
                "response_type", "ephemeral",
                "blocks", blocks
        );
    }

    private List<String> toLines(JsonNode res) {
        JsonNode results = res.path("results");
        if (!results.isArray() || results.isEmpty()) return List.of();

        List<String> lines = new ArrayList<>();
        for (JsonNode page : results) {
            JsonNode props = page.path("properties");

            String title = readTitle(props, PROP_TITLE);
            String status = readStatus(props, PROP_STATUS);
            String due = readDateStart(props, PROP_DUE_DATE);

            String slackLink = readUrl(props, PROP_SLACK_LINK);

            // ✅ 제목만 링크로
            String titlePart = (slackLink != null)
                    ? "<" + slackLink + "|" + safe(title) + ">"
                    : safe(title);

            String line = "• " + titlePart;

            if (!status.isBlank()) {
                line += " (" + status + ")";
            }

            if (due != null && !due.isBlank()) {
                line += " (기한 " + due + ")";
            }

            lines.add(line);
        }
        return lines;
    }

    private String readTitle(JsonNode props, String propName) {
        JsonNode arr = props.path(propName).path("title");
        if (!arr.isArray() || arr.isEmpty()) return "";
        return arr.path(0).path("text").path("content").asText("");
    }

    private String readStatus(JsonNode props, String propName) {
        return props.path(propName).path("status").path("name").asText("");
    }

    private String readDateStart(JsonNode props, String propName) {
        JsonNode date = props.path(propName).path("date");
        if (date.isMissingNode() || date.isNull()) return null;
        return date.path("start").asText(null);
    }

    private Map<String, Object> header(String text) {
        return Map.of("type", "header", "text", Map.of("type", "plain_text", "text", text));
    }

    private Map<String, Object> section(String text) {
        return Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text));
    }

    private Map<String, Object> divider() {
        return Map.of("type", "divider");
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private String readUrl(JsonNode props, String propName) {
        JsonNode url = props.path(propName).path("url");
        if (url.isMissingNode() || url.isNull()) return null;
        String v = url.asText("");
        return v.isBlank() ? null : v;
    }
}
