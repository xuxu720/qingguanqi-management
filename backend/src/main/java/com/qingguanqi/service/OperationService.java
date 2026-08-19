package com.qingguanqi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingguanqi.entity.Operation;
import com.qingguanqi.entity.TrackingRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationService extends IService<Operation> {

    /** 创建作业并初始化全部跟踪记录（预测到达时间） */
    List<TrackingRecord> createWithTracking(Operation operation);

    /** 节点到达反馈：记录实际到达时间并触发下游滚动修正 */
    void nodeArrival(Long operationId, Long stationId, LocalDateTime actualArrivalTime);

    /** 状态机流转 */
    void updateStatus(Long operationId, String newStatus);
}
