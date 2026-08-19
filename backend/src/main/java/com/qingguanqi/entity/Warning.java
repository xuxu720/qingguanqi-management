package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("warning")
public class Warning {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operationId;
    private String warningType;
    private String level;
    private String content;
    private String suggestion;
    private String status;
    private LocalDateTime resolvedTime;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
