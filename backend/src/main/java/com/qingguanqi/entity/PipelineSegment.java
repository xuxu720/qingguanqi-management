package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pipeline_segment")
public class PipelineSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "所属管线不能为空")
    private Long pipelineId;

    @NotNull(message = "起始站不能为空")
    private Long fromStationId;

    @NotNull(message = "到达站不能为空")
    private Long toStationId;

    @NotNull(message = "站间距不能为空")
    private BigDecimal distance;
    private BigDecimal unitCapacity;
    private BigDecimal innerDiameter;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
