package com.qingguanqi.enums;

import lombok.Getter;

@Getter
public enum WarningType {

    延迟("延迟"),
    速度异常("速度异常"),
    卡阻("卡阻");

    private final String label;

    WarningType(String label) {
        this.label = label;
    }
}
