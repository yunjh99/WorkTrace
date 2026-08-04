package com.example.marketbot.worklog.domain;

import java.util.List;

/**
 * 접수할 업무의 유형과 Slack 선택 화면의 표시 순서를 정의합니다.
 * 허용 가능한 분류를 코드로 제한하여 임의 문자열로 인한 데이터 불일치를 방지합니다.
 */
public enum WorkCategory {

    단가("tk"),
    상품("상품"),
    정산("정산"),
    운영("운영");

    private final String label;

    WorkCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<String> labels() {
        return java.util.Arrays.stream(values())
                .map(WorkCategory::label)
                .toList();
    }
}
