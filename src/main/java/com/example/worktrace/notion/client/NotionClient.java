package com.example.worktrace.notion.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

/** Notion API를 호출해 업무 데이터 소스에 새 페이지를 생성한다. */
@Component
public class NotionClient {

    private static final String NOTION_VERSION = "2026-03-11";
    private static final String DEFAULT_STATUS = "접수";

    private final RestClient restClient;
    private final String dataSourceId;

    public NotionClient(
            @Value("${notion.api.token}") String apiToken,
            @Value("${notion.data-source-id}") String dataSourceId
    ) {
        this.dataSourceId = dataSourceId;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void createTask(
            String taskTitle,
            String taskContent,
            String assigneeName,
            String assigneeSlackId,
            List<String> observerNames,
            String slackMessageUrl
    ) {
        if (dataSourceId == null || dataSourceId.isBlank()) {
            throw new IllegalStateException("NOTION_DATA_SOURCE_ID가 설정되지 않았습니다.");
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("업무명", title(taskTitle));
        properties.put("담당자", richText(assigneeName));
        properties.put("담당자 Slack ID", richText(assigneeSlackId));
        properties.put("참조자", richText(String.join(", ", observerNames)));
        properties.put("진행상태", status(DEFAULT_STATUS));
        properties.put("Slack 메시지", url(slackMessageUrl));

        Map<String, Object> request = Map.of(
                "parent", Map.of("data_source_id", dataSourceId),
                "properties", properties,
                "children", paragraphBlocks(taskContent)
        );

        restClient.post()
                .uri("/pages")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public Optional<String> findPageIdBySlackMessageUrl(String slackMessageUrl) {
        return findTaskBySlackMessageUrl(slackMessageUrl)
                .map(NotionTaskReference::pageId);
    }

    public Optional<NotionTaskReference> findTaskBySlackMessageUrl(String slackMessageUrl) {
        Map<String, Object> request = Map.of(
                "filter", Map.of(
                        "property", "Slack 메시지",
                        "url", Map.of("equals", slackMessageUrl)
                ),
                "page_size", 1
        );

        NotionQueryResponse response = restClient.post()
                .uri("/data_sources/{dataSourceId}/query", dataSourceId)
                .body(request)
                .retrieve()
                .body(NotionQueryResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }

        NotionPage page = response.results().getFirst();
        String assigneeSlackId = page.richText("담당자 Slack ID");
        return Optional.of(new NotionTaskReference(page.id(), assigneeSlackId));
    }

    public List<WorkTask> findAllTasks() {
        List<WorkTask> tasks = new ArrayList<>();
        String cursor = null;

        do {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("page_size", 100);
            if (cursor != null) {
                request.put("start_cursor", cursor);
            }

            NotionQueryResponse response = restClient.post()
                    .uri("/data_sources/{dataSourceId}/query", dataSourceId)
                    .body(request)
                    .retrieve()
                    .body(NotionQueryResponse.class);

            if (response == null || response.results() == null) {
                break;
            }

            response.results().stream()
                    .map(page -> new WorkTask(
                            page.title("업무명"),
                            page.richText("담당자"),
                            page.richText("담당자 Slack ID"),
                            page.status("진행상태"),
                            page.url("Slack 메시지"),
                            page.lastEditedTime()
                    ))
                    .forEach(tasks::add);

            cursor = response.hasMore() ? response.nextCursor() : null;
        } while (cursor != null && !cursor.isBlank());

        return List.copyOf(tasks);
    }

    public void updateStatus(String pageId, String statusName) {
        Map<String, Object> request = Map.of(
                "properties", Map.of(
                        "진행상태", status(statusName)
                )
        );

        restClient.patch()
                .uri("/pages/{pageId}", pageId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void updateAssigneeSlackId(String pageId, String assigneeSlackId) {
        Map<String, Object> request = Map.of(
                "properties", Map.of(
                        "담당자 Slack ID", richText(assigneeSlackId)
                )
        );

        restClient.patch()
                .uri("/pages/{pageId}", pageId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> title(String content) {
        return Map.of(
                "title", List.of(text(content))
        );
    }

    private List<Map<String, Object>> paragraphBlocks(String content) {
        return content.lines()
                .map(line -> Map.<String, Object>of(
                        "object", "block",
                        "type", "paragraph",
                        "paragraph", Map.of(
                                "rich_text", line.isBlank() ? List.of() : List.of(text(line))
                        )
                ))
                .toList();
    }

    private Map<String, Object> richText(String content) {
        if (content == null || content.isBlank()) {
            return Map.of("rich_text", List.of());
        }
        return Map.of(
                "rich_text", List.of(text(content))
        );
    }

    private Map<String, Object> status(String name) {
        return Map.of(
                "status", Map.of("name", name)
        );
    }

    private Map<String, Object> url(String value) {
        return Map.of("url", value);
    }

    private Map<String, Object> text(String content) {
        return Map.of(
                "type", "text",
                "text", Map.of("content", content)
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NotionQueryResponse(
            List<NotionPage> results,
            @JsonProperty("has_more") boolean hasMore,
            @JsonProperty("next_cursor") String nextCursor
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NotionPage(
            String id,
            @JsonProperty("last_edited_time") String lastEditedTime,
            Map<String, NotionProperty> properties
    ) {
        private String title(String propertyName) {
            if (properties == null || properties.get(propertyName) == null) {
                return "";
            }
            List<NotionRichText> values = properties.get(propertyName).title();
            return plainText(values);
        }

        private String richText(String propertyName) {
            if (properties == null || properties.get(propertyName) == null) {
                return null;
            }
            return plainText(properties.get(propertyName).richText());
        }

        private String status(String propertyName) {
            if (properties == null || properties.get(propertyName) == null
                    || properties.get(propertyName).status() == null) {
                return null;
            }
            return properties.get(propertyName).status().name();
        }

        private String url(String propertyName) {
            if (properties == null || properties.get(propertyName) == null) {
                return null;
            }
            return properties.get(propertyName).url();
        }

        private String plainText(List<NotionRichText> values) {
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.stream()
                    .map(NotionRichText::plainText)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce("", String::concat);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NotionProperty(
            List<NotionRichText> title,
            @JsonProperty("rich_text") List<NotionRichText> richText,
            NotionStatus status,
            String url
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NotionStatus(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NotionRichText(
            @JsonProperty("plain_text") String plainText
    ) {
    }

    public record NotionTaskReference(
            String pageId,
            String assigneeSlackId
    ) {
    }

    public record WorkTask(
            String title,
            String assigneeName,
            String assigneeSlackId,
            String status,
            String slackMessageUrl,
            String lastEditedTime
    ) {
    }
}
