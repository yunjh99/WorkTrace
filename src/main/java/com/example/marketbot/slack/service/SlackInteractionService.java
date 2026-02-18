package com.example.marketbot.slack.service;

import com.example.marketbot.notion.service.NotionWorklogService;
import com.example.marketbot.slack.client.SlackClient;
import com.example.marketbot.slack.domain.SlackUser;
import com.example.marketbot.slack.repository.SlackUserRepository;
import com.example.marketbot.slack.view.WorklogModalBuilder;
import com.example.marketbot.slack.view.WorklogReceiptMessageBuilder;
import com.example.marketbot.slack_notion_link.domain.SlackNotionLink;
import com.example.marketbot.slack_notion_link.domain.SlackNotionLinkAssignee;
import com.example.marketbot.slack_notion_link.repository.SlackNotionLinkAssigneeRepository;
import com.example.marketbot.slack_notion_link.repository.SlackNotionLinkRepository;
import com.example.marketbot.worklog.domain.Team;
import com.example.marketbot.worklog.dto.WorklogCreateCommand;
import com.example.marketbot.worklog.dto.WorklogEditInitial;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SlackInteractionService {

    private final ObjectMapper om;
    private final SlackClient slackClient;
    private final WorklogModalBuilder worklogModalBuilder;
    private final SlackUserRepository slackUserRepository;
    private final SlackUserService slackUserService;
    private final NotionWorklogService notionWorklogService;
    private final SlackNotionLinkRepository slackNotionLinkRepository;
    private final SlackNotionLinkAssigneeRepository slackNotionLinkAssigneeRepository;
    private final WorklogReceiptMessageBuilder worklogReceiptMessageBuilder;
    private final WorklogEditService worklogEditService;

    // ✅ 원문(타팀 문의 메시지)에 표시할 이모지
    private static final String EMOJI_EYES  = "eyes";
    private static final String EMOJI_CHECK = "white_check_mark";

    // ✅ 버튼 action_id
    private static final String ACTION_PROGRESS = "worklog_progress";
    private static final String ACTION_COMPLETE = "worklog_complete";
    private static final String ACTION_RESET    = "worklog_reset";
    private static final String ACTION_EDIT     = "worklog_edit";

    /**
     * ✅ Slack Interactivity 진입점
     * - message_action  : 메시지 shortcut 클릭 → 모달 오픈
     * - view_submission : 모달 submit → 생성/수정 처리
     * - block_actions   : receipt 버튼 클릭(수정/진행/완료/되돌리기)
     */
    public void handle(String payloadJson) {
        try {
            JsonNode payload = om.readTree(payloadJson);
            String type = payload.path("type").asText();

            if ("block_actions".equals(type)) {
                handleBlockActions(payload);
                return;
            }

            if ("message_action".equals(type)) {
                handleMessageAction(payload);
                return;
            }

            if ("view_submission".equals(type)) {
                handleViewSubmission(payload);
                return;
            }

            System.out.println("[SLACK] Unsupported interaction type: " + type);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ✅ 메시지 shortcut 클릭 처리 (worklog_create)
     * - private_metadata: channelId|messageTs|threadTs|assigneeUserId
     * - content 프리필: mention -> displayName 치환
     * - assignee/watcher 프리필: cc 기준 분리
     */
    private void handleMessageAction(JsonNode payload) {
        String callbackId = payload.path("callback_id").asText();
        if (!"worklog_create".equals(callbackId)) return;

        String triggerId = payload.path("trigger_id").asText();

        String channelId = payload.path("channel").path("id").asText();
        String messageTs = payload.path("message").path("ts").asText();
        String threadTs  = payload.path("message").path("thread_ts").asText("");

        // threadTs 없으면 루트 = messageTs
        if (threadTs.isBlank()) threadTs = messageTs;

        // shortcut 누른 사람(담당자)
        String assigneeUserId = payload.path("user").path("id").asText();

        // submit에서 복구할 컨텍스트
        String privateMetadata = String.join("|",
                channelId,
                messageTs,
                threadTs,
                assigneeUserId
        );

        String rawText = payload.path("message").path("text").asText(null);

        // content 프리필(멘션 -> displayName)
        String contentText = slackUserService.replaceMentionsWithDisplayName(rawText);

        // cc 기준 담당자/참조자 프리필
        AssigneeWatcher aw = parseAssigneeWatcherFromText(rawText);

        // 담당자 팀 설정 여부에 따라 팀 선택 UI 노출 여부 결정
        SlackUser assignee = slackUserRepository.findBySlackUserId(assigneeUserId)
                .orElseThrow(() -> new RuntimeException("slack_user에 없는 담당자: " + assigneeUserId));

        boolean showTeamSelect = (assignee.getTeam() == null);
        List<String> owningTeamOptions = List.of("마켓팀", "ERP팀", "DATA팀");

        slackClient.viewsOpen(
                triggerId,
                worklogModalBuilder.build(
                        privateMetadata,
                        showTeamSelect,
                        owningTeamOptions,
                        aw.assignees,
                        aw.watchers,
                        contentText
                )
        );
    }

    /**
     * ✅ 모달 submit 라우팅
     * - worklog_create : 신규 저장
     * - worklog_edit   : 수정 저장
     */
    private void handleViewSubmission(JsonNode payload) {
        String callbackId = payload.path("view").path("callback_id").asText();

        if ("worklog_create".equals(callbackId)) {
            handleCreateSubmission(payload);
            return;
        }

        if ("worklog_edit".equals(callbackId)) {
            handleEditSubmission(payload);
            return;
        }
    }

    /**
     * ✅ 신규 저장(worklog_create)
     * 1) 팀 최초 설정(필요 시)
     * 2) Notion 페이지 생성
     * 3) SlackNotionLink 저장(접수자/제목/상태 포함)
     * 4) assignee 권한 테이블 저장
     * 5) receipt 메시지 전송 + bot ts 저장
     */
    private void handleCreateSubmission(JsonNode payload) {

        JsonNode values = payload.path("view").path("state").path("values");

        // channelId | messageTs | threadTs | shortcut누른사람
        String privateMetadata = payload.path("view").path("private_metadata").asText();
        String[] meta = privateMetadata.split("\\|", 4);

        String channelId      = meta.length > 0 ? meta[0] : "";
        String messageTs      = meta.length > 1 ? meta[1] : "";
        String threadTs       = meta.length > 2 ? meta[2] : messageTs;
        String assigneeUserId = meta.length > 3 ? meta[3] : "";

        // 접수자(모달 submit한 사람)
        String receiverUserId = payload.path("user").path("id").asText();
        String receiverName   = slackUserService.resolveDisplayName(receiverUserId);

        // 담당자(= shortcut 누른 사람)
        SlackUser assignee = slackUserRepository.findBySlackUserId(assigneeUserId)
                .orElseThrow(() -> new RuntimeException("slack_user에 없는 담당자: " + assigneeUserId));

        // ✅ 담당팀 최초 설정(1회)
        if (assignee.getTeam() == null) {
            String teamLabel = values.path("team_block")
                    .path("team")                 // ✅ WorklogModalBuilder actionId="team"
                    .path("selected_option")
                    .path("value")
                    .asText("");

            if (teamLabel.isBlank()) {
                throw new RuntimeException("담당팀이 설정되지 않았습니다. 팀을 선택해주세요.");
            }

            assignee.setTeam(Team.fromLabel(teamLabel));
            slackUserRepository.save(assignee);
        }

        Team owningTeam = assignee.getTeam();

        // Slack permalink (루트 message 기준)
        String slackLink = slackClient.chatGetPermalink(channelId, messageTs);

        // 입력값
        String title   = values.path("title_block").path("title").path("value").asText("");
        String content = values.path("content_block").path("content").path("value").asText("");
        String type    = values.path("type_block").path("type").path("selected_option").path("value").asText("");

        // 담당자/참조자 (Slack userId)
        List<String> assignees = extractSelectedUsers(values, "assignee_block", "assignees");
        List<String> watchers  = extractSelectedUsers(values, "watcher_block", "watchers");

        // Notion에는 displayName으로 저장
        List<String> assigneeNames = toDisplayNames(assignees);
        List<String> watcherNames  = toDisplayNames(watchers);

        // 마감일
        String dueDateStr = values.path("due_block").path("due_date").path("selected_date").asText(null);
        LocalDate dueDate = (dueDateStr != null && !dueDateStr.isBlank()) ? LocalDate.parse(dueDateStr) : null;

        String status = "접수";

        WorklogCreateCommand cmd = new WorklogCreateCommand(
                owningTeam,
                status,
                title,
                content,
                type,
                assigneeNames,
                watcherNames,
                channelId,
                messageTs,
                threadTs,
                receiverName,
                slackLink,
                dueDate
        );

        // ✅ 중복 저장 방지 (channelId + messageTs)
        var existingOpt = slackNotionLinkRepository.findBySlackChannelIdAndSlackMessageTs(channelId, messageTs);
        if (existingOpt.isPresent()) {
            SlackNotionLink existing = existingOpt.get();
            String notionUrl = "https://www.notion.so/" + existing.getNotionPageId().replace("-", "");
            slackClient.chatPostEphemeral(channelId, receiverUserId, "이미 저장된 업무입니다. Notion 페이지: " + notionUrl);
            return;
        }

        // ✅ Notion 생성
        JsonNode notionRes = notionWorklogService.create(cmd);
        String notionPageId = notionRes.path("id").asText("");

        // ✅ Slack ↔ Notion 연결 저장 (접수자/제목/상태 포함)
        SlackNotionLink link = new SlackNotionLink();
        link.setSlackChannelId(channelId);
        link.setSlackMessageTs(messageTs);
        link.setSlackThreadTs(threadTs);
        link.setNotionPageId(notionPageId);
        link.setReceiverSlackUserId(receiverUserId);
        link.setWorklogTitle(title);
        link.setWorklogStatus(status);
        slackNotionLinkRepository.save(link);

        // ✅ 담당자 권한 체크용 저장
        assignees.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .forEach(uid -> {
                    SlackNotionLinkAssignee a = new SlackNotionLinkAssignee();
                    a.setLink(link);
                    a.setSlackUserId(uid);
                    slackNotionLinkAssigneeRepository.save(a);
                });

        // ✅ receipt(버튼 메시지) 전송
        // - 정책: watcher 라인은 "완료"일 때만 표시 → 생성 시에는 빈 리스트로
        List<Map<String, Object>> blocks =
                worklogReceiptMessageBuilder.buildReceiptBlocks(
                        receiverUserId,
                        title,
                        assignees,
                        List.of(),          // ✅ watcherUserIds (완료 때만 사용)
                        link.getId(),
                        "접수",
                        null
                );

        String botTs = slackClient.chatPostMessageWithBlocks(channelId, blocks, threadTs);

        // receipt 메시지 ts 저장
        link.setSlackBotMessageTs(botTs);
        slackNotionLinkRepository.save(link);
    }

    /**
     * ✅ 수정 저장(worklog_edit)
     * - 실제 DB/Notion 업데이트는 WorklogEditService(트랜잭션)에서 처리
     * - 여기서는 receipt 갱신만 처리
     *
     * private_metadata: EDIT|linkId|channelId|receiptTs
     */
    private void handleEditSubmission(JsonNode payload) {

        JsonNode values = payload.path("view").path("state").path("values");

        String privateMetadata = payload.path("view").path("private_metadata").asText("");
        String[] meta = privateMetadata.split("\\|", 4);

        Long linkId = Long.valueOf(meta[1]);
        String channelId = meta[2];
        String receiptMessageTs = meta[3];

        String newTitle   = values.path("title_block").path("title").path("value").asText("");
        String newContent = values.path("content_block").path("content").path("value").asText("");
        String newType    = values.path("type_block").path("type").path("selected_option").path("value").asText("");

        List<String> newAssignees = extractSelectedUsers(values, "assignee_block", "assignees");
        List<String> newWatchers  = extractSelectedUsers(values, "watcher_block", "watchers");

        List<String> newAssigneeNames = toDisplayNames(newAssignees);
        List<String> newWatcherNames  = toDisplayNames(newWatchers);

        String dueDateStr = values.path("due_block").path("due_date").path("selected_date").asText(null);
        LocalDate newDueDate = (dueDateStr != null && !dueDateStr.isBlank()) ? LocalDate.parse(dueDateStr) : null;

        // ✅ DB/Notion 업데이트는 트랜잭션 서비스로
        worklogEditService.editWorklog(
                linkId,
                newTitle,
                newContent,
                newType,
                newAssigneeNames,
                newWatcherNames,
                newDueDate,
                newAssignees
        );

        // ✅ 최신 link 조회(상태/접수자 등)
        SlackNotionLink link = slackNotionLinkRepository.findById(linkId).orElseThrow();

        // ✅ receipt 갱신
        // - 정책: watcher 라인은 "완료" 버튼 눌렀을 때만 Notion에서 조회하여 표시
        List<Map<String, Object>> newBlocks =
                worklogReceiptMessageBuilder.buildReceiptBlocks(
                        link.getReceiverSlackUserId(),
                        newTitle,
                        newAssignees,
                        List.of(),              // ✅ watcherUserIds (여기서는 비움)
                        linkId,
                        link.getWorklogStatus(),
                        null
                );

        slackClient.chatUpdateWithBlocks(channelId, receiptMessageTs, newBlocks);
    }

    /**
     * ✅ receipt 버튼 클릭 처리
     *
     * 정책
     * - 수정하기: "접수자만" 허용
     * - 진행/완료/되돌리기: "담당자만" 허용
     *
     * 성능/체감 속도 순서(완료 케이스만 watcher Notion 조회 추가)
     * 1) 권한 체크
     * 2) 상태 계산 + (완료면 watcher 조회 best-effort)
     * 3) 원문 이모지 토글(best-effort)
     * 4) receipt chat.update
     * 5) 로컬 DB 상태 저장
     * 6) Notion 상태 업데이트(실패해도 Slack은 반영)
     */
    private void handleBlockActions(JsonNode payload) {

        JsonNode action = (payload.path("actions").isArray() && payload.path("actions").size() > 0)
                ? payload.path("actions").get(0)
                : null;
        if (action == null) return;

        String actionId = action.path("action_id").asText();
        Long linkId = action.path("value").asLong();

        String clickUserId = payload.path("user").path("id").asText();
        String responseChannelId = payload.path("channel").path("id").asText();

        // ✅ receipt 메시지 ts (chat.update 대상)
        String receiptMessageTs = payload.path("message").path("ts").asText();

        SlackNotionLink link = slackNotionLinkRepository.findById(linkId).orElse(null);
        if (link == null) return;

        // =========================
        // ✅ ✏️ 수정하기: 접수자만
        // =========================
        if (ACTION_EDIT.equals(actionId)) {

            boolean isReceiver = clickUserId.equals(link.getReceiverSlackUserId());
            if (!isReceiver) {
                slackClient.chatPostEphemeral(responseChannelId, clickUserId, "수정은 *접수자만* 할 수 있어요.");
                return;
            }

            String triggerId = payload.path("trigger_id").asText("");
            if (triggerId.isBlank()) return;

            openEditModal(triggerId, link, responseChannelId, receiptMessageTs);
            return;
        }

        // =========================
        // ✅ 진행/완료/되돌리기: 담당자만
        // =========================
        boolean isAssignee = slackNotionLinkAssigneeRepository.existsByLink_IdAndSlackUserId(linkId, clickUserId);
        if (!isAssignee) {
            slackClient.chatPostEphemeral(responseChannelId, clickUserId, "이 버튼은 *담당자만* 사용할 수 있어요.");
            return;
        }

        // =========================
        // ✅ 상태 계산
        // =========================
        String newStatus = switch (actionId) {
            case ACTION_PROGRESS -> "진행";
            case ACTION_COMPLETE -> "완료";
            case ACTION_RESET    -> "접수";
            default -> null;
        };
        if (newStatus == null) return;

        // 상태 중복 클릭이면 중단(외부 호출 안 함)
        String currentStatus = link.getWorklogStatus();
        if (currentStatus != null && currentStatus.equals(newStatus)) {
            slackClient.chatPostEphemeral(responseChannelId, clickUserId, "이미 *" + newStatus + "* 상태입니다.");
            return;
        }

        // =========================
        // ✅ 완료일/참조자(완료일 때만 Notion에서 watcher 조회)
        // =========================
        String dateLine = null;
        List<String> watcherIds = List.of();

        if ("완료".equals(newStatus)) {
            dateLine = "완료일: " + LocalDate.now();

            // ✅ watcher는 Notion에만 있으니 "완료"에서만 best-effort로 조회
            try {
                JsonNode page = notionWorklogService.getPage(link.getNotionPageId());
                WorklogEditInitial init = notionWorklogService.parseInitialForEdit(page);
                watcherIds = (init != null) ? init.initialWatcherSlackUserIds() : List.of();
            } catch (Exception ignore) {
                // Notion이 느리거나 실패해도, 완료 처리는 진행(참조자 라인만 비게 됨)
                watcherIds = List.of();
            }
        }

        // =========================
        // ✅ receipt 블록 재구성
        // =========================
        String receiverUserId = link.getReceiverSlackUserId();
        String title = link.getWorklogTitle();

        var assigneeIdSet = slackNotionLinkAssigneeRepository.findSlackUserIdsByLinkId(linkId);
        List<String> assigneeIdList = new ArrayList<>(assigneeIdSet);

        List<Map<String, Object>> newBlocks =
                worklogReceiptMessageBuilder.buildReceiptBlocks(
                        receiverUserId,
                        title,
                        assigneeIdList,
                        watcherIds,   // ✅ 완료일 때만 채워짐
                        linkId,
                        newStatus,
                        dateLine
                );

        // =========================
        // ✅ 원문 이모지 토글(best-effort)
        // =========================
        try {
            toggleOriginalMessageReactions(link, newStatus);
        } catch (Exception e) {
            slackClient.chatPostEphemeral(responseChannelId, clickUserId,
                    "원문 메시지 이모지 반영에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        // =========================
        // ✅ receipt 업데이트
        // =========================
        try {
            slackClient.chatUpdateWithBlocks(responseChannelId, receiptMessageTs, newBlocks);
        } catch (Exception e) {
            slackClient.chatPostEphemeral(responseChannelId, clickUserId,
                    "receipt 메시지 업데이트에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return;
        }

        // =========================
        // ✅ 로컬 DB 상태 저장
        // =========================
        link.setWorklogStatus(newStatus);
        slackNotionLinkRepository.save(link);

        // =========================
        // ✅ Notion 상태 업데이트(가장 느림, 실패해도 Slack은 반영)
        // =========================
        try {
            notionWorklogService.updateStatus(link.getNotionPageId(), newStatus);
        } catch (Exception e) {
            slackClient.chatPostEphemeral(responseChannelId, clickUserId,
                    "Slack 상태는 반영됐지만 Notion 상태 업데이트에 실패했습니다. (네트워크/Notion 지연)\n" +
                            "잠시 후 다시 눌러 동기화해주세요.");
        }
    }

    /**
     * ✅ 원문 메시지에 이모지 토글
     * - 진행: ✅ 제거 + 👀 추가
     * - 완료: 👀 제거 + ✅ 추가
     * - 접수: 👀/✅ 둘 다 제거
     */
    private void toggleOriginalMessageReactions(SlackNotionLink link, String status) {
        String channelId = link.getSlackChannelId();
        String messageTs = link.getSlackMessageTs();

        if ("진행".equals(status)) {
            safeReactionsRemove(channelId, messageTs, EMOJI_CHECK);
            slackClient.reactionsAdd(channelId, messageTs, EMOJI_EYES);

        } else if ("완료".equals(status)) {
            safeReactionsRemove(channelId, messageTs, EMOJI_EYES);
            slackClient.reactionsAdd(channelId, messageTs, EMOJI_CHECK);

        } else { // 접수
            safeReactionsRemove(channelId, messageTs, EMOJI_EYES);
            safeReactionsRemove(channelId, messageTs, EMOJI_CHECK);
        }
    }

    /** remove는 이미 없으면 에러 날 수 있어 흡수 */
    private void safeReactionsRemove(String channelId, String messageTs, String emoji) {
        try {
            slackClient.reactionsRemove(channelId, messageTs, emoji);
        } catch (Exception ignore) {}
    }

    /* =========================
     * selected_users 추출
     * ========================= */
    private static List<String> extractSelectedUsers(JsonNode values, String blockId, String actionId) {
        JsonNode arr = values.path(blockId).path(actionId).path("selected_users");
        if (!arr.isArray()) return List.of();

        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            String id = n.asText();
            if (id != null && !id.isBlank()) out.add(id);
        }
        return out;
    }

    /* =========================
     * cc 기준 담당자/참조자 분리
     * ========================= */
    private static final Pattern MENTION = Pattern.compile("<@([A-Z0-9]+)>");

    private static List<String> extractMentionedUserIds(String s) {
        if (s == null) return List.of();

        Matcher m = MENTION.matcher(s);
        List<String> ids = new ArrayList<>();
        while (m.find()) ids.add(m.group(1));
        return ids;
    }

    private static class AssigneeWatcher {
        final List<String> assignees;
        final List<String> watchers;

        AssigneeWatcher(List<String> assignees, List<String> watchers) {
            this.assignees = assignees;
            this.watchers = watchers;
        }
    }

    /**
     * 메시지 텍스트에서 "cc" 기준 분리
     * - cc 앞: assignees
     * - cc 뒤: watchers
     */
    private static AssigneeWatcher parseAssigneeWatcherFromText(String text) {
        if (text == null) return new AssigneeWatcher(List.of(), List.of());

        String[] parts = text.split("(?i)(?:\\s|^)cc(?:\\s|$)", 2);
        List<String> assignees = extractMentionedUserIds(parts[0]);
        List<String> watchers  = (parts.length > 1) ? extractMentionedUserIds(parts[1]) : List.of();

        return new AssigneeWatcher(assignees, watchers);
    }

    /** Slack userId → displayName 변환 */
    private List<String> toDisplayNames(List<String> slackUserIds) {
        return slackUserIds.stream()
                .map(slackUserService::resolveDisplayName)
                .distinct()
                .toList();
    }

    /**
     * ✅ 수정 모달 오픈
     * - Notion에서 기존 값 조회 → 초기값 채워서 모달 생성
     * - private_metadata: EDIT|linkId|responseChannelId|receiptMessageTs
     */
    private void openEditModal(String triggerId, SlackNotionLink link, String responseChannelId, String receiptMessageTs) {

        JsonNode page = notionWorklogService.getPage(link.getNotionPageId());
        WorklogEditInitial init = notionWorklogService.parseInitialForEdit(page);

        String privateMetadata = "EDIT|" + link.getId() + "|" + responseChannelId + "|" + receiptMessageTs;

        Map<String, Object> modal = worklogModalBuilder.buildForEdit(
                privateMetadata,
                false,
                List.of(),
                init.initialAssigneeSlackUserIds(),
                init.initialWatcherSlackUserIds(),
                init.content(),
                init.type(),
                init.title(),
                init.dueDate()
        );

        slackClient.viewsOpen(triggerId, modal);
    }
}
