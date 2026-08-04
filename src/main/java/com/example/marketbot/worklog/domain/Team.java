package com.example.marketbot.worklog.domain;

/**
 * 업무를 담당할 수 있는 조직 단위를 정의합니다.
 * 화면에 표시되는 한글 이름과 내부에서 사용하는 안정적인 enum 값을 연결합니다.
 */
public enum Team {
    MARKET("마켓팀"),
    ERP("ERP팀"),
    DATA("DATA팀");

    private final String label;

    Team(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Team fromLabel(String label) {
        for (Team t : values()) {
            if (t.label.equals(label)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown team label: " + label);
    }
}
