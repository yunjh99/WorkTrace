package com.example.marketbot.worklog.domain;

import java.util.List;

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
