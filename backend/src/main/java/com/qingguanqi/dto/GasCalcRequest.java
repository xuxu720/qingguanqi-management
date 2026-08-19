package com.qingguanqi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GasCalcRequest {
    @NotNull
    private BigDecimal fromStationMileage;
    @NotNull
    private BigDecimal toStationMileage;
    @NotNull @DecimalMin("0.01")
    private BigDecimal innerDiameter;
    @NotNull @DecimalMin("0.01")
    private BigDecimal outletPressure;
    @NotNull @DecimalMin("0.01")
    private BigDecimal inletPressure;
    @NotNull @DecimalMin("0.01")
    private BigDecimal gasFlowRate;
    @NotNull
    private LocalDateTime dispatchTime;
}
