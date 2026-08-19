package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingguanqi.engine.GasCalcService;
import com.qingguanqi.engine.LiquidCalcService;
import com.qingguanqi.entity.*;
import com.qingguanqi.enums.OperationStatus;
import com.qingguanqi.enums.PigStatus;
import com.qingguanqi.mapper.*;
import com.qingguanqi.service.OperationService;
import com.qingguanqi.service.PigService;
import com.qingguanqi.service.TrackingRecordService;
import com.qingguanqi.service.WarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperationServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<OperationMapper, Operation> implements OperationService {

    private final TrackingRecordService trackingRecordService;
    private final GasCalcService gasCalcService;
    private final LiquidCalcService liquidCalcService;
    private final StationMapper stationMapper;
    private final PipelineMapper pipelineMapper;
    private final PigMapper pigMapper;
    private final PigService pigService;
    private final WarningService warningService;

    private static final long DELAY_THRESHOLD_MINUTES = 30;

    @Override
    @Transactional
    public List<TrackingRecord> createWithTracking(Operation operation) {
        Pipeline pipeline = pipelineMapper.selectById(operation.getPipelineId());
        if (pipeline == null) throw new IllegalArgumentException("管线不存在");

        Pig pig = pigMapper.selectById(operation.getPigId());
        if (pig == null) throw new IllegalArgumentException("清管器不存在");
        if (!pig.getMediumType().equals(pipeline.getMediumType()) && !"通用".equals(pig.getMediumType())) {
            throw new IllegalArgumentException("清管器介质类型与管线不匹配");
        }

        operation.setStatus(OperationStatus.准备.getLabel());
        save(operation);

        List<TrackingRecord> records;
        if ("气体".equals(pipeline.getMediumType())) {
            records = initGasTracking(operation);
        } else {
            records = initLiquidTracking(operation);
        }

        trackingRecordService.saveBatch(records);

        if ("可用".equals(pig.getStatus())) {
            pigService.updateStatus(pig.getId(), PigStatus.使用中.getLabel());
        }

        operation.setStatus(OperationStatus.运行中.getLabel());
        updateById(operation);

        return records;
    }

    @Override
    @Transactional
    public void nodeArrival(Long operationId, Long stationId, LocalDateTime actualArrivalTime) {
        Operation operation = getById(operationId);
        if (operation == null) throw new IllegalArgumentException("作业不存在");
        if (!OperationStatus.运行中.getLabel().equals(operation.getStatus())) {
            throw new IllegalStateException("只有运行中的作业才能反馈节点到达");
        }

        TrackingRecord record = trackingRecordService.getOne(
            new LambdaQueryWrapper<TrackingRecord>()
                .eq(TrackingRecord::getOperationId, operationId)
                .eq(TrackingRecord::getStationId, stationId));
        if (record == null) throw new IllegalArgumentException("该站点不在本作业路线上");
        if (record.getActualArrivalTime() != null) {
            throw new IllegalStateException("该站点已反馈过到达时间");
        }

        record.setActualArrivalTime(actualArrivalTime);
        long delayMinutes = Duration.between(record.getPredictedArrivalTime(), actualArrivalTime).toMinutes();

        boolean isLastStation = stationId.equals(operation.getToStationId());
        if (isLastStation) {
            operation.setStatus(OperationStatus.已完成.getLabel());
            updateById(operation);
            // 清管器恢复可用
            try {
                pigService.updateStatus(operation.getPigId(), PigStatus.可用.getLabel());
            } catch (Exception ignored) { /* 状态可能已变更 */ }
        } else {
            rollForward(operation, stationId, actualArrivalTime);
        }

        trackingRecordService.updateById(record);

        if (Math.abs(delayMinutes) >= DELAY_THRESHOLD_MINUTES) {
            warningService.createDelayWarning(operationId, delayMinutes);
        }
        warningService.checkSpeedAnomaly(operationId, operation.getPipelineId(),
                stationId, record.getPigSpeed());
    }

    @Override
    public void updateStatus(Long operationId, String newStatus) {
        Operation operation = getById(operationId);
        if (operation == null) throw new IllegalArgumentException("作业不存在");

        OperationStatus current = OperationStatus.valueOf(operation.getStatus());
        OperationStatus target = OperationStatus.valueOf(newStatus);
        if (OperationStatus.已完成.equals(target)) {
            throw new IllegalStateException("「已完成」状态由系统在所有节点到达后自动设置，不可手动变更");
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("不允许从「%s」变更为「%s」", current.getLabel(), target.getLabel()));
        }
        operation.setStatus(newStatus);
        updateById(operation);
    }

    private List<TrackingRecord> initLiquidTracking(Operation op) {
        List<com.qingguanqi.dto.SegmentCalcResult> calcResults = liquidCalcService.calcPipeline(
            op.getPipelineId(), op.getFromStationId(), op.getToStationId(),
            op.getDisplacement(), op.getDispatchTime());

        List<Station> stations = getOrderedStations(op.getPipelineId());
        int startIdx = findStationIndex(stations, op.getFromStationId());

        Set<Long> keySet = buildKeyStationSet(op, stations, startIdx, calcResults.size());

        List<TrackingRecord> records = new ArrayList<>();
        for (int i = 0; i < calcResults.size(); i++) {
            com.qingguanqi.dto.SegmentCalcResult cr = calcResults.get(i);
            Station toStation = stations.get(startIdx + i + 1);
            TrackingRecord tr = new TrackingRecord();
            tr.setOperationId(op.getId());
            tr.setStationId(toStation.getId());
            tr.setSegmentDistance(cr.getDistance());
            tr.setPredictedArrivalTime(cr.getEstimatedArrivalTime());
            tr.setPigSpeed(cr.getPigSpeed());
            tr.setIsRevised(0);
            tr.setRevisionCount(0);
            tr.setIsKeyStation(keySet.contains(toStation.getId()) ? 1 : 0);
            records.add(tr);
        }
        return records;
    }

    private List<TrackingRecord> initGasTracking(Operation op) {
        List<com.qingguanqi.dto.GasCalcResult> calcResults = gasCalcService.calcPipeline(
            op.getPipelineId(), op.getFromStationId(), op.getToStationId(),
            op.getOutletPressure(), op.getInletPressure(),
            op.getGasFlowRate(), op.getDispatchTime());

        List<Station> stations = getOrderedStations(op.getPipelineId());
        int startIdx = findStationIndex(stations, op.getFromStationId());

        Set<Long> keySet = buildKeyStationSet(op, stations, startIdx, calcResults.size());

        List<TrackingRecord> records = new ArrayList<>();
        for (int i = 0; i < calcResults.size(); i++) {
            com.qingguanqi.dto.GasCalcResult cr = calcResults.get(i);
            Station toStation = stations.get(startIdx + i + 1);
            TrackingRecord tr = new TrackingRecord();
            tr.setOperationId(op.getId());
            tr.setStationId(toStation.getId());
            tr.setSegmentDistance(cr.getDistance());
            tr.setPredictedArrivalTime(cr.getEstimatedArrivalTime());
            tr.setPigSpeed(cr.getTheoreticalSpeed());
            tr.setIsRevised(0);
            tr.setRevisionCount(0);
            tr.setIsKeyStation(keySet.contains(toStation.getId()) ? 1 : 0);
            records.add(tr);
        }
        return records;
    }

    private void rollForward(Operation op, Long currentStationId, LocalDateTime actualTime) {
        // 按站点顺序查出全部跟踪记录，用位置而非时间判断下游（清管器延误时
        // actualTime 可能晚于下游预测时间，.gt() 会查不到任何记录）
        List<TrackingRecord> allRecords = trackingRecordService.list(
            new LambdaQueryWrapper<TrackingRecord>()
                .eq(TrackingRecord::getOperationId, op.getId())
                .orderByAsc(TrackingRecord::getPredictedArrivalTime));

        int currentIdx = -1;
        for (int i = 0; i < allRecords.size(); i++) {
            if (allRecords.get(i).getStationId().equals(currentStationId)) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx < 0 || currentIdx >= allRecords.size() - 1) return;

        List<TrackingRecord> downstream = allRecords.subList(currentIdx + 1, allRecords.size());
        Pipeline pipeline = pipelineMapper.selectById(op.getPipelineId());
        Long triggerRecordId = allRecords.get(currentIdx).getId();

        if ("气体".equals(pipeline.getMediumType())) {
            BigDecimal currentPressure = computePressureAtStation(op, currentStationId);
            List<com.qingguanqi.dto.GasCalcResult> newResults = gasCalcService.calcPipeline(
                op.getPipelineId(), currentStationId, op.getToStationId(),
                currentPressure, op.getInletPressure(),
                op.getGasFlowRate(), actualTime);
            applyGasCorrection(downstream, newResults, triggerRecordId);
        } else {
            List<com.qingguanqi.dto.SegmentCalcResult> newResults = liquidCalcService.calcPipeline(
                op.getPipelineId(), currentStationId, op.getToStationId(),
                op.getDisplacement(), actualTime);
            applyLiquidCorrection(downstream, newResults, triggerRecordId);
        }
    }

    private BigDecimal computePressureAtStation(Operation op, Long stationId) {
        List<Station> stations = getOrderedStations(op.getPipelineId());
        Station fromStation = stations.stream().filter(s -> s.getId().equals(op.getFromStationId())).findFirst().orElseThrow();
        Station currentStation = stations.stream().filter(s -> s.getId().equals(stationId)).findFirst().orElseThrow();
        Station toStation = stations.stream().filter(s -> s.getId().equals(op.getToStationId())).findFirst().orElseThrow();

        BigDecimal totalDistance = toStation.getMileage().subtract(fromStation.getMileage());
        BigDecimal traveledDistance = currentStation.getMileage().subtract(fromStation.getMileage());
        BigDecimal totalPressureDrop = op.getOutletPressure().subtract(op.getInletPressure());

        return op.getOutletPressure()
            .subtract(totalPressureDrop.multiply(traveledDistance).divide(totalDistance, 10, RoundingMode.HALF_UP));
    }

    private void applyLiquidCorrection(List<TrackingRecord> downstream,
                                        List<com.qingguanqi.dto.SegmentCalcResult> newResults,
                                        Long triggerRecordId) {
        for (int i = 0; i < downstream.size() && i < newResults.size(); i++) {
            TrackingRecord tr = downstream.get(i);
            com.qingguanqi.dto.SegmentCalcResult cr = newResults.get(i);
            tr.setPredictedArrivalTime(cr.getEstimatedArrivalTime());
            tr.setPigSpeed(cr.getPigSpeed());
            tr.setSegmentDistance(cr.getDistance());
            tr.setIsRevised(1);
            tr.setParentRecordId(triggerRecordId);
            tr.setRevisionCount(tr.getRevisionCount() + 1);
            trackingRecordService.updateById(tr);
        }
    }

    private void applyGasCorrection(List<TrackingRecord> downstream,
                                     List<com.qingguanqi.dto.GasCalcResult> newResults,
                                     Long triggerRecordId) {
        for (int i = 0; i < downstream.size() && i < newResults.size(); i++) {
            TrackingRecord tr = downstream.get(i);
            com.qingguanqi.dto.GasCalcResult cr = newResults.get(i);
            tr.setPredictedArrivalTime(cr.getEstimatedArrivalTime());
            tr.setPigSpeed(cr.getTheoreticalSpeed());
            tr.setSegmentDistance(cr.getDistance());
            tr.setIsRevised(1);
            tr.setParentRecordId(triggerRecordId);
            tr.setRevisionCount(tr.getRevisionCount() + 1);
            trackingRecordService.updateById(tr);
        }
    }

    private List<Station> getOrderedStations(Long pipelineId) {
        return stationMapper.selectList(
            new LambdaQueryWrapper<Station>()
                .eq(Station::getPipelineId, pipelineId)
                .orderByAsc(Station::getSortOrder));
    }

    private int findStationIndex(List<Station> stations, Long stationId) {
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).getId().equals(stationId)) return i;
        }
        throw new IllegalArgumentException("站点不属于该管线");
    }

    private Set<Long> buildKeyStationSet(Operation op, List<Station> stations, int startIdx, int segmentCount) {
        if (op.getKeyStationIds() != null && !op.getKeyStationIds().isEmpty()) {
            return new HashSet<>(op.getKeyStationIds());
        }
        // 默认：发球后首站 + 收球站为关键站
        Set<Long> set = new HashSet<>();
        set.add(stations.get(startIdx + 1).getId());
        set.add(stations.get(startIdx + segmentCount).getId());
        return set;
    }
}
