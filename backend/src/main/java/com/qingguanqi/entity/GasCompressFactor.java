package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("gas_compress_factor")
public class GasCompressFactor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private BigDecimal avgPressure;
    private BigDecimal temperature;
    private BigDecimal compressFactor;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
