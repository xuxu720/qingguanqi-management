package com.qingguanqi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingguanqi.dto.TrackingRecordVO;
import com.qingguanqi.entity.TrackingRecord;

import java.util.List;

public interface TrackingRecordService extends IService<TrackingRecord> {

    /** 获取作业的所有跟踪记录（含站点名称） */
    List<TrackingRecordVO> getByOperation(Long operationId);
}
