package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingguanqi.dto.TrackingRecordVO;
import com.qingguanqi.entity.Station;
import com.qingguanqi.entity.TrackingRecord;
import com.qingguanqi.mapper.StationMapper;
import com.qingguanqi.mapper.TrackingRecordMapper;
import com.qingguanqi.service.TrackingRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrackingRecordServiceImpl extends ServiceImpl<TrackingRecordMapper, TrackingRecord> implements TrackingRecordService {

    private final StationMapper stationMapper;

    @Override
    public List<TrackingRecordVO> getByOperation(Long operationId) {
        List<TrackingRecord> records = list(
            new LambdaQueryWrapper<TrackingRecord>()
                .eq(TrackingRecord::getOperationId, operationId)
                .orderByAsc(TrackingRecord::getPredictedArrivalTime));

        if (records.isEmpty()) return List.of();

        List<Long> stationIds = records.stream()
            .map(TrackingRecord::getStationId)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> nameMap = stationMapper.selectBatchIds(stationIds).stream()
            .collect(Collectors.toMap(Station::getId, Station::getName));

        return records.stream().map(r -> {
            TrackingRecordVO vo = new TrackingRecordVO();
            vo.setId(r.getId());
            vo.setOperationId(r.getOperationId());
            vo.setStationId(r.getStationId());
            vo.setStationName(nameMap.getOrDefault(r.getStationId(), ""));
            vo.setSegmentDistance(r.getSegmentDistance());
            vo.setPredictedArrivalTime(r.getPredictedArrivalTime());
            vo.setActualArrivalTime(r.getActualArrivalTime());
            vo.setPigSpeed(r.getPigSpeed());
            vo.setIsRevised(r.getIsRevised());
            vo.setParentRecordId(r.getParentRecordId());
            vo.setRevisionCount(r.getRevisionCount());
            vo.setRemark(r.getRemark());
            vo.setIsKeyStation(r.getIsKeyStation() != null && r.getIsKeyStation() == 1);
            vo.setCreateTime(r.getCreateTime());
            vo.setUpdateTime(r.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
