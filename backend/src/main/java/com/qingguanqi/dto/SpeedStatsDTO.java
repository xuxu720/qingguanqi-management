package com.qingguanqi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeedStatsDTO {
    private BigDecimal avgSpeed;
    private BigDecimal stddevSpeed;
    private Long sampleCount;
}
