package com.example.marketbot.slack_notion_link.repository;

import com.example.marketbot.slack_notion_link.domain.SlackNotionLinkAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface SlackNotionLinkAssigneeRepository extends JpaRepository<SlackNotionLinkAssignee, Long> {

    @Query("select a.slackUserId from SlackNotionLinkAssignee a where a.link.id = :linkId")

    Set<String> findSlackUserIdsByLinkId(@Param("linkId") Long linkId);
    boolean existsByLink_IdAndSlackUserId(Long linkId, String slackUserId);


    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from SlackNotionLinkAssignee a where a.link.id = :linkId")
    int deleteAllByLinkId(@Param("linkId") Long linkId);


}
