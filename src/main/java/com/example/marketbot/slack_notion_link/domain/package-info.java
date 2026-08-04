/**
 * Slack 메시지와 기존 Notion 업무 데이터의 연결 정보를 표현합니다.
 *
 * <p>현재 클래스들은 담당자와 Slack 상태 추적에도 사용되고 있으므로,
 * Slack 전용 구조로 전환할 때 단순 삭제보다 Worklog 모델로 이름과 책임을
 * 변경할 필요가 있습니다.</p>
 */
package com.example.marketbot.slack_notion_link.domain;
