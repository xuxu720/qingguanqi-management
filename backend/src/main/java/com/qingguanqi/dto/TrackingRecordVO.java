package com.qingguanqi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrackingRecordVO {
    private Long id;
    private Long operationId;
    private Long stationId;
    private String stationName;
    private BigDecimal segmentDistance;
    private LocalDateTime predictedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private BigDecimal pigSpeed;
    private Integer isRevised;
    private Long parentRecordId;
    private Integer revisionCount;
    private String remark;
    private Boolean isKeyStation;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
