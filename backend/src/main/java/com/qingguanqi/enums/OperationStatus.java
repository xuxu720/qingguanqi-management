package com.qingguanqi.enums;

import lombok.Getter;

@Getter
public enum OperationStatus {

    准备("准备"),
    运行中("运行中"),
    已完成("已完成"),
    异常("异常");

    private final String label;

    OperationStatus(String label) {
        this.label = label;
    }

    public boolean canTransitionTo(OperationStatus target) {
        if (this == 已完成 || this == 异常) {
            return false;
        }
        if (this == 准备) {
            return target == 运行中 || target == 异常;
        }
        if (this == 运行中) {
            return target == 已完成 || target == 异常;
        }
        return true;
    }
}
