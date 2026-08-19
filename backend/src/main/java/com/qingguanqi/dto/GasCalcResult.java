package com.qingguanqi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GasCalcResult {
    private BigDecimal distance;
    private BigDecimal avgPressure;
    private BigDecimal compressFactor;
    private BigDecimal crossSectionArea;
    private BigDecimal theoreticalSpeed;
    private BigDecimal runningTime;
    private LocalDateTime estimatedArrivalTime;
}
