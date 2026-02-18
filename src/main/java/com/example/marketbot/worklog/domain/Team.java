package com.example.marketbot.worklog.domain;

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
