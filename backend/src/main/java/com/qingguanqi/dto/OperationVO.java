package com.qingguanqi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OperationVO {
    private Long id;
    private Long pipelineId;
    private String pipelineName;
    private Long pigId;
    private String pigName;
    private String pigType;
    private String pigSpec;
    private String operationType;
    private Long fromStationId;
    private String fromStationName;
    private Long toStationId;
    private String toStationName;
    private LocalDateTime dispatchTime;
    private BigDecimal displacement;
    private BigDecimal gasFlowRate;
    private BigDecimal outletPressure;
    private BigDecimal inletPressure;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
