package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pipeline")
public class Pipeline {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "管线名称不能为空")
    private String name;

    @NotBlank(message = "介质类型不能为空")
    private String mediumType;
    private BigDecimal diameter;
    private BigDecimal designPressureMin;
    private BigDecimal designPressureMax;
    private BigDecimal totalLength;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
