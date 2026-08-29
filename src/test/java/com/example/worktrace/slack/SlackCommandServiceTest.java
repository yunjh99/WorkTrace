package com.example.worktrace.slack;

import com.example.worktrace.notion.client.NotionClient;
import com.example.worktrace.notion.client.NotionClient.WorkTask;
import com.example.worktrace.slack.service.SlackCommandService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlackCommandServiceTest {

    private final NotionClient notionClient = mock(NotionClient.class);
    private final SlackCommandService service = new SlackCommandService(notionClient);

    @Test
    void 내업무는_요청한_사용자의_미완료와_오늘_완료만_보여준다() {
        String today = Instant.now().toString();
        String old = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(2)
                .atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toString();
        when(notionClient.findAllTasks()).thenReturn(List.of(
                task("내 접수", "김민수", "U1", "접수", today),
                task("내 오늘 완료", "김민수", "U1", "완료", today),
                task("내 과거 완료", "김민수", "U1", "완료", old),
                task("다른 사람 업무", "이지은", "U2", "진행중", today)
        ));

        String text = sectionText(service.handle("/내업무", "U1"));

        assertThat(text).contains("내 접수", "내 오늘 완료")
                .contains("[금일 미완료 업무]", "[금일 완료 업무]", "상태 : 접수")
                .doesNotContain("내 과거 완료", "다른 사람 업무");
    }

    @Test
    void 팀업무는_담당자별로_업무를_묶어서_보여준다() {
        String now = Instant.now().toString();
        when(notionClient.findAllTasks()).thenReturn(List.of(
                task("상품 확인", "김민수", "U1", "접수", now),
                task("고객 회신", "이지은", "U2", "진행중", now)
        ));

        Map<String, Object> response = service.handle("/팀업무", "U1");
        String text = sectionText(response);

        assertThat(response.get("response_type")).isEqualTo("ephemeral");
        assertThat(text).contains("[김민수]", "상품 확인", "상태 : 접수",
                "[이지은]", "고객 회신", "상태 : 진행중");
    }

    private WorkTask task(String title, String assignee, String userId, String status, String edited) {
        return new WorkTask(title, assignee, userId, status,
                "https://workspace.slack.com/archives/C123/p123", edited);
    }

    @SuppressWarnings("unchecked")
    private String sectionText(Map<String, Object> response) {
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) response.get("blocks");
        Map<String, Object> text = (Map<String, Object>) blocks.get(1).get("text");
        return (String) text.get("text");
    }
}
