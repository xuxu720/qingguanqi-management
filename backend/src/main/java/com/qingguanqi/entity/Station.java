package com.qingguanqi.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("station")
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "所属管线不能为空")
    private Long pipelineId;

    @NotBlank(message = "站点名称不能为空")
    private String name;

    @NotBlank(message = "站点类型不能为空")
    private String stationType;

    @NotNull(message = "累计里程不能为空")
    private BigDecimal mileage;

    private BigDecimal elevation;

    @NotNull(message = "排序号不能为空")
    private Integer sortOrder;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
