package com.qingguanqi.enums;

import lombok.Getter;

@Getter
public enum WarningStatus {

    未处理("未处理"),
    已确认("已确认"),
    已关闭("已关闭");

    private final String label;

    WarningStatus(String label) {
        this.label = label;
    }

    public boolean canTransitionTo(WarningStatus target) {
        if (this == 已关闭) return false;
        if (this == 未处理) return target == 已确认 || target == 已关闭;
        if (this == 已确认) return target == 已关闭;
        return true;
    }
}
