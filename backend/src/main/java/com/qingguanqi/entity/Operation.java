package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("operation")
public class Operation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pipelineId;
    private Long pigId;
    private String operationType;
    private Long fromStationId;
    private Long toStationId;
    private LocalDateTime dispatchTime;
    private BigDecimal displacement;
    private BigDecimal gasFlowRate;
    private BigDecimal outletPressure;
    private BigDecimal inletPressure;
    private String status;
    private String remark;

    /** 关键站 ID 列表（仅创建时使用，不持久化到 operation 表） */
    @TableField(exist = false)
    private List<Long> keyStationIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
