/**
 * 계층 사이에서 업무 등록·수정 데이터를 전달하는 불변 객체를 관리합니다.
 *
 * <p>Slack payload 전체를 서비스 내부로 전달하지 않고 업무 처리에 필요한 값만
 * 묶어 전달함으로써 외부 요청 형식과 업무 로직의 결합을 줄입니다.</p>
 */
package com.example.marketbot.worklog.dto;
