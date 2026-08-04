package com.example.marketbot.slack_notion_link.repository;

import com.example.marketbot.slack_notion_link.domain.SlackNotionLinkAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

/**
 * 업무별 담당자 관계를 저장하고 담당자의 업무 목록을 조회합니다.
 * 개인 업무 현황과 상태 변경 권한 판정에 필요한 관계 조회를 담당합니다.
 */
public interface SlackNotionLinkAssigneeRepository extends JpaRepository<SlackNotionLinkAssignee, Long> {

    @Query("select a.slackUserId from SlackNotionLinkAssignee a where a.link.id = :linkId")

    Set<String> findSlackUserIdsByLinkId(@Param("linkId") Long linkId);
    boolean existsByLink_IdAndSlackUserId(Long linkId, String slackUserId);


    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from SlackNotionLinkAssignee a where a.link.id = :linkId")
    int deleteAllByLinkId(@Param("linkId") Long linkId);


}
