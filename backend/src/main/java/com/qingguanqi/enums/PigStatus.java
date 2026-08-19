package com.qingguanqi.enums;

import lombok.Getter;

@Getter
public enum PigStatus {

    可用("可用"),
    使用中("使用中"),
    报废("报废");

    private final String label;

    PigStatus(String label) {
        this.label = label;
    }

    public boolean canTransitionTo(PigStatus target) {
        if (this == 报废) {
            return false;
        }
        return true;
    }
}
