package com.example.marketbot.slack.service;

import com.example.marketbot.notion.service.NotionWorklogService;
import com.example.marketbot.slack_notion_link.domain.SlackNotionLink;
import com.example.marketbot.slack_notion_link.domain.SlackNotionLinkAssignee;
import com.example.marketbot.slack_notion_link.repository.SlackNotionLinkAssigneeRepository;
import com.example.marketbot.slack_notion_link.repository.SlackNotionLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 등록된 업무의 제목, 내용, 담당자, 기한을 하나의 트랜잭션으로 수정합니다.
 * 상호작용 해석과 데이터 변경을 분리해 수정 규칙을 독립적으로 관리합니다.
 */
@Service
@RequiredArgsConstructor
public class WorklogEditService {

    private final SlackNotionLinkRepository slackNotionLinkRepository;
    private final SlackNotionLinkAssigneeRepository slackNotionLinkAssigneeRepository;
    private final NotionWorklogService notionWorklogService;

    @Transactional
    public void editWorklog(
            Long linkId,
            String newTitle,
            String newContent,
            String newType,
            List<String> newAssigneeNames,
            List<String> newWatcherNames,
            LocalDate newDueDate,
            List<String> newAssigneeSlackUserIds
    ) {
        SlackNotionLink link = slackNotionLinkRepository.findById(linkId)
                .orElseThrow(() -> new RuntimeException("SlackNotionLink not found: " + linkId));

        // 1) Notion 업데이트
        notionWorklogService.updateWorklog(
                link.getNotionPageId(),
                newTitle,
                newContent,
                newType,
                newAssigneeNames,
                newWatcherNames,
                newDueDate
        );

        // 2) 로컬 link 업데이트(receipt 빠른 재구성용)
        link.setWorklogTitle(newTitle);
        slackNotionLinkRepository.save(link);

        // 3) 담당자 권한 테이블 최신화
        slackNotionLinkAssigneeRepository.deleteAllByLinkId(linkId); // ✅ @Modifying delete 추천

        newAssigneeSlackUserIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .forEach(uid -> {
                    SlackNotionLinkAssignee a = new SlackNotionLinkAssignee();
                    a.setLink(link);
                    a.setSlackUserId(uid);
                    slackNotionLinkAssigneeRepository.save(a);
                });
    }
}
