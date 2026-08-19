package com.qingguanqi.enums;

import lombok.Getter;

@Getter
public enum WarningLevel {

    高("高"),
    中("中"),
    低("低");

    private final String label;

    WarningLevel(String label) {
        this.label = label;
    }
}
