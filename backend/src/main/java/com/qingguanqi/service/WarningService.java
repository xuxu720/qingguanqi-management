package com.qingguanqi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingguanqi.entity.Warning;

import java.math.BigDecimal;
import java.util.List;

public interface WarningService extends IService<Warning> {

    Warning createDelayWarning(Long operationId, long delayMinutes);

    Warning checkSpeedAnomaly(Long operationId, Long pipelineId, Long stationId, BigDecimal currentSpeed);

    List<Warning> checkStuckConditions();

    void confirm(Long id);

    void resolve(Long id, String remark);
}
