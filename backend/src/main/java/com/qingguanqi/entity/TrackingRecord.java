package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tracking_record")
public class TrackingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operationId;
    private Long stationId;
    private BigDecimal segmentDistance;
    private LocalDateTime predictedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private BigDecimal pigSpeed;
    private Integer isRevised;
    private Long parentRecordId;
    private Integer revisionCount;
    private String remark;
    private Integer isKeyStation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
