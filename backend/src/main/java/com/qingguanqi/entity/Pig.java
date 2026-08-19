package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pig")
public class Pig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "清管器类型不能为空")
    private String type;

    @NotBlank(message = "规格不能为空")
    private String spec;

    @NotNull(message = "过盈量不能为空")
    @DecimalMin(value = "0.01", message = "过盈量不能小于0.01")
    @DecimalMax(value = "50.00", message = "过盈量不能大于50.00")
    private BigDecimal interferenceRate;

    private String applicableScene;

    @NotBlank(message = "适用介质不能为空")
    private String mediumType;

    /** 状态由系统内部维护，不通过接口直接传入 */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
