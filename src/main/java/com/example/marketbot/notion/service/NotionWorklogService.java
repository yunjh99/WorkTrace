package com.example.marketbot.notion.service;

import com.example.marketbot.notion.client.NotionClient;
import com.example.marketbot.slack.domain.SlackUser;
import com.example.marketbot.slack.repository.SlackUserRepository;
import com.example.marketbot.worklog.dto.WorklogCreateCommand;
import com.example.marketbot.worklog.dto.WorklogEditInitial;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotionWorklogService {

    private final NotionClient notionClient;
    private final ObjectMapper om;
    private final SlackUserRepository slackUserRepository; // ✅ displayName -> slackUserId 변환용

    @Value("${notion.database.id}")
    private String databaseId;

    private static final String PAGES_URL = "https://api.notion.com/v1/pages";
    private static final String PAGE_URL  = "https://api.notion.com/v1/pages/"; // ✅ 단건 조회/수정용

    // ✅ Notion DB property names (DB에 있는 이름과 정확히 일치해야 함)
    private static final String PROP_TITLE       = "Title";
    private static final String PROP_CONTENT     = "content";
    private static final String PROP_STATUS      = "Status";
    private static final String PROP_TYPE        = "Type";
    private static final String PROP_OWNING_TEAM = "Owning Team";
    private static final String PROP_ASSIGNEE    = "Assignee";
    private static final String PROP_WATCHER     = "Watcher";
    private static final String PROP_SLACK_LINK  = "Slack Link";
    private static final String PROP_DUE_DATE    = "Due Date";
    private static final String PROP_RECEIVER    = "Receiver";

    /**
     * WorklogCreateCommand → Notion DB에 페이지 생성(저장)
     */
    public JsonNode create(WorklogCreateCommand cmd) {
        Map<String, Object> payload = Map.of(
                "parent", Map.of("database_id", databaseId),
                "properties", buildProperties(cmd)
        );

        // ✅ payload 로그(선택)
        try {
            System.out.println("========== [NOTION PAYLOAD] ==========");
            System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            System.out.println("======================================");
        } catch (Exception ignored) {}

        JsonNode res = notionClient.post(PAGES_URL, payload);

        // ✅ 응답 로그(선택)
        try {
            System.out.println("========== [NOTION RESPONSE] ==========");
            System.out.println(res.toPrettyString());
            System.out.println("=======================================");
        } catch (Exception ignored) {}

        return res;
    }

    private Map<String, Object> buildProperties(WorklogCreateCommand cmd) {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put(PROP_TITLE, Map.of(
                "title", List.of(text(cmd.title()))
        ));

        props.put(PROP_CONTENT, Map.of(
                "rich_text", List.of(text(cmd.content()))
        ));

        props.put(PROP_STATUS, Map.of(
                "status", Map.of("name", cmd.status())
        ));

        props.put(PROP_TYPE, Map.of(
                "select", Map.of("name", cmd.type())
        ));

        props.put(PROP_OWNING_TEAM, Map.of(
                "multi_select", List.of(Map.of("name", cmd.owningTeam().name()))
        ));

        props.put(PROP_ASSIGNEE, Map.of(
                "multi_select", toMultiSelect(cmd.assigneeNames())
        ));

        props.put(PROP_WATCHER, Map.of(
                "multi_select", toMultiSelect(cmd.watcherNames())
        ));

        props.put(PROP_SLACK_LINK, Map.of(
                "url", cmd.slackLink()
        ));

        if (cmd.dueDate() != null) {
            props.put(PROP_DUE_DATE, Map.of(
                    "date", Map.of("start", cmd.dueDate().toString())
            ));
        }

        if (cmd.receiverName() != null && !cmd.receiverName().isBlank()) {
            props.put(PROP_RECEIVER, Map.of(
                    "select", Map.of("name", cmd.receiverName())
            ));
        }

        return props;
    }

    /** ✅ Notion 페이지 단건 조회 */
    public JsonNode getPage(String pageId) {
        return notionClient.get(PAGE_URL + pageId);
    }

    /** ✅ Notion 페이지 → 수정 모달 초기값(WorklogEditInitial) */
    public WorklogEditInitial parseInitialForEdit(JsonNode page) {
        JsonNode props = page.path("properties");

        String title   = readTitle(props, PROP_TITLE);
        String content = readRichText(props, PROP_CONTENT);
        String type    = readSelectName(props, PROP_TYPE);
        String dueDate = readDateStart(props, PROP_DUE_DATE);

        List<String> assigneeNames = readMultiSelectNames(props, PROP_ASSIGNEE);
        List<String> watcherNames  = readMultiSelectNames(props, PROP_WATCHER);

        // ✅ displayName -> slackUserId (multi_users_select initial_users)
        List<String> assigneeIds = toSlackUserIdsByDisplayName(assigneeNames);
        List<String> watcherIds  = toSlackUserIdsByDisplayName(watcherNames);

        return new WorklogEditInitial(
                title,
                content,
                type,
                dueDate,
                assigneeIds,
                watcherIds
        );
    }

    private String readTitle(JsonNode props, String propName) {
        JsonNode arr = props.path(propName).path("title");
        if (!arr.isArray() || arr.isEmpty()) return "";
        return arr.path(0).path("text").path("content").asText("");
    }

    private String readRichText(JsonNode props, String propName) {
        JsonNode arr = props.path(propName).path("rich_text");
        if (!arr.isArray() || arr.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (JsonNode n : arr) {
            String t = n.path("text").path("content").asText("");
            if (!t.isBlank()) sb.append(t);
        }
        return sb.toString();
    }

    private String readSelectName(JsonNode props, String propName) {
        return props.path(propName).path("select").path("name").asText("");
    }

    private String readDateStart(JsonNode props, String propName) {
        JsonNode date = props.path(propName).path("date");
        if (date.isMissingNode() || date.isNull()) return null;

        String start = date.path("start").asText(null);
        return (start == null || start.isBlank()) ? null : start;
    }

    private List<String> readMultiSelectNames(JsonNode props, String propName) {
        JsonNode arr = props.path(propName).path("multi_select");
        if (!arr.isArray() || arr.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            String name = n.path("name").asText("");
            if (!name.isBlank()) out.add(name);
        }
        return out.stream().distinct().toList();
    }

    private List<String> toSlackUserIdsByDisplayName(List<String> displayNames) {
        if (displayNames == null || displayNames.isEmpty()) return List.of();

        List<String> ids = new ArrayList<>();
        for (String dn : displayNames) {
            if (dn == null || dn.isBlank()) continue;

            slackUserRepository.findByDisplayName(dn)
                    .map(SlackUser::getSlackUserId)
                    .ifPresent(ids::add);
        }
        return ids.stream().distinct().toList();
    }

    private static Map<String, Object> text(String s) {
        String safe = (s == null) ? "" : s;
        return Map.of("text", Map.of("content", safe));
    }

    private static List<Map<String, Object>> toMultiSelect(List<String> names) {
        if (names == null) return List.of();
        return names.stream()
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .map(n -> Map.<String, Object>of("name", n))
                .toList();
    }

    /** ✅ Status만 업데이트 */
    public void updateStatus(String pageId, String status) {
        Map<String, Object> payload = Map.of(
                "properties", Map.of(
                        PROP_STATUS, Map.of(
                                "status", Map.of("name", status)
                        )
                )
        );

        notionClient.patch(PAGE_URL + pageId, payload);
    }

    public void updateWorklog(
            String pageId,
            String title,
            String content,
            String type,
            List<String> assigneeNames,
            List<String> watcherNames,
            java.time.LocalDate dueDate
    ) {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put(PROP_TITLE, Map.of("title", List.of(text(title))));
        props.put(PROP_CONTENT, Map.of("rich_text", List.of(text(content))));
        props.put(PROP_TYPE, Map.of("select", Map.of("name", type)));

        props.put(PROP_ASSIGNEE, Map.of("multi_select", toMultiSelect(assigneeNames)));
        props.put(PROP_WATCHER, Map.of("multi_select", toMultiSelect(watcherNames)));

        // ✅ Due Date (null 제거 처리: Map.of 쓰면 NPE 나니까 Map으로)
        Map<String, Object> dueDateProp = new LinkedHashMap<>();
        if (dueDate != null) {
            dueDateProp.put("date", Map.of("start", dueDate.toString()));
        } else {
            dueDateProp.put("date", null); // ✅ Notion date 제거
        }
        props.put(PROP_DUE_DATE, dueDateProp);

        Map<String, Object> payload = Map.of("properties", props);

        notionClient.patch(PAGE_URL + pageId, payload);
    }
    private static final String DB_QUERY_URL = "https://api.notion.com/v1/databases/";
    private static final String PROP_LAST_UPDATED_AT = "Last Updated At"; // Notion 속성명 그대로!

    public JsonNode queryTodoByAssignee(String displayName) {
        String url = DB_QUERY_URL + databaseId + "/query";

        Map<String, Object> payload = Map.of(
                "filter", Map.of(
                        "and", List.of(
                                Map.of("property", PROP_ASSIGNEE,
                                        "multi_select", Map.of("contains", displayName)),
                                Map.of("or", List.of(
                                        Map.of("property", PROP_STATUS, "status", Map.of("equals", "접수")),
                                        Map.of("property", PROP_STATUS, "status", Map.of("equals", "진행"))
                                ))
                        )
                ),
                "sorts", List.of(
                        Map.of("property", PROP_LAST_UPDATED_AT, "direction", "descending")
                )
        );

        return notionClient.post(url, payload);
    }

    public JsonNode queryDoneTodayByAssignee(String displayName, java.time.LocalDate today) {
        String url = DB_QUERY_URL + databaseId + "/query";

        String start = today.toString();
        String end = today.plusDays(1).toString();

        Map<String, Object> payload = Map.of(
                "filter", Map.of(
                        "and", List.of(
                                Map.of("property", PROP_ASSIGNEE,
                                        "multi_select", Map.of("contains", displayName)),
                                Map.of("property", PROP_STATUS,
                                        "status", Map.of("equals", "완료")),
                                Map.of("property", PROP_LAST_UPDATED_AT,
                                        "date", Map.of("on_or_after", start)),
                                Map.of("property", PROP_LAST_UPDATED_AT,
                                        "date", Map.of("before", end))
                        )
                ),
                "sorts", List.of(
                        Map.of("property", PROP_LAST_UPDATED_AT, "direction", "descending")
                )
        );

        return notionClient.post(url, payload);
    }


}
