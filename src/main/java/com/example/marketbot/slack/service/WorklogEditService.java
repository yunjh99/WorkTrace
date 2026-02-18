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
