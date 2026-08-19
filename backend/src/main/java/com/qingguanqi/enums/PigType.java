package com.qingguanqi.enums;

import lombok.Getter;

@Getter
public enum PigType {

    清管球("清管球"),
    泡沫清管器("泡沫清管器"),
    钢刷清管器("钢刷清管器"),
    磁力清管器("磁力清管器"),
    智能清管器("智能清管器"),
    双向清管器("双向清管器");

    private final String label;

    PigType(String label) {
        this.label = label;
    }
}
