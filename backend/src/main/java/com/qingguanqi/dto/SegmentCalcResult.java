package com.qingguanqi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SegmentCalcResult {
    private String fromStationName;
    private String toStationName;
    private BigDecimal distance;
    private BigDecimal unitCapacity;
    private BigDecimal pipeCapacity;
    private BigDecimal runningTime;
    private BigDecimal pigSpeed;
    private LocalDateTime estimatedArrivalTime;
}
