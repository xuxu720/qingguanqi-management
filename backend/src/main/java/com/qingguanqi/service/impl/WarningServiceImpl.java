package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingguanqi.dto.SpeedStatsDTO;
import com.qingguanqi.entity.Operation;
import com.qingguanqi.entity.TrackingRecord;
import com.qingguanqi.entity.Warning;
import com.qingguanqi.enums.WarningLevel;
import com.qingguanqi.enums.WarningStatus;
import com.qingguanqi.enums.WarningType;
import com.qingguanqi.mapper.OperationMapper;
import com.qingguanqi.mapper.TrackingRecordMapper;
import com.qingguanqi.mapper.WarningMapper;
import com.qingguanqi.service.WarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarningServiceImpl extends ServiceImpl<WarningMapper, Warning> implements WarningService {

    private final TrackingRecordMapper trackingRecordMapper;
    private final OperationMapper operationMapper;

    private static final int STUCK_THRESHOLD_HOURS = 2;
    private static final int MIN_SAMPLE_COUNT = 3;

    @Override
    @Transactional
    public Warning createDelayWarning(Long operationId, long delayMinutes) {
        Warning warning = new Warning();
        warning.setOperationId(operationId);
        warning.setWarningType(WarningType.延迟.getLabel());
        warning.setLevel(Math.abs(delayMinutes) > 120 ? WarningLevel.高.getLabel() : WarningLevel.中.getLabel());
        warning.setContent(String.format("实际到达时间与预测偏差 %d 分钟（%s）",
                Math.abs(delayMinutes), delayMinutes > 0 ? "延迟" : "提前"));
        warning.setSuggestion(buildDelaySuggestion(delayMinutes));
        warning.setStatus(WarningStatus.未处理.getLabel());
        save(warning);
        return warning;
    }

    @Override
    @Transactional
    public Warning checkSpeedAnomaly(Long operationId, Long pipelineId, Long stationId, BigDecimal currentSpeed) {
        if (currentSpeed == null || currentSpeed.compareTo(BigDecimal.ZERO) <= 0) return null;

        SpeedStatsDTO stats = trackingRecordMapper.getHistoricalSpeedStats(pipelineId, stationId);
        if (stats == null || stats.getSampleCount() < MIN_SAMPLE_COUNT) {
            stats = trackingRecordMapper.getOverallSpeedStats(pipelineId);
        }
        if (stats == null || stats.getSampleCount() < MIN_SAMPLE_COUNT) return null;

        BigDecimal avgSpeed = stats.getAvgSpeed();
        BigDecimal stddev = stats.getStddevSpeed();
        if (avgSpeed == null || stddev == null || stddev.compareTo(BigDecimal.ZERO) <= 0) return null;

        BigDecimal deviation = currentSpeed.subtract(avgSpeed).abs();
        BigDecimal sigma = deviation.divide(stddev, 2, RoundingMode.HALF_UP);

        if (sigma.compareTo(new BigDecimal("2")) < 0) return null;

        String level = sigma.compareTo(new BigDecimal("3")) >= 0 ?
                WarningLevel.高.getLabel() : WarningLevel.中.getLabel();
        String direction = currentSpeed.compareTo(avgSpeed) > 0 ? "偏快" : "偏慢";

        Warning warning = new Warning();
        warning.setOperationId(operationId);
        warning.setWarningType(WarningType.速度异常.getLabel());
        warning.setLevel(level);
        warning.setContent(String.format("清管器速度异常：当前 %.2f km/h，历史均值 %.2f km/h（σ=%.2f），偏差 %.1fσ（%s）",
                currentSpeed, avgSpeed, stddev, sigma, direction));
        warning.setSuggestion(buildSpeedSuggestion(direction, sigma));
        warning.setStatus(WarningStatus.未处理.getLabel());
        save(warning);
        return warning;
    }

    @Override
    @Transactional
    public List<Warning> checkStuckConditions() {
        List<Warning> newWarnings = new ArrayList<>();

        List<Operation> runningOps = operationMapper.selectList(
                new LambdaQueryWrapper<Operation>().eq(Operation::getStatus, "运行中"));
        if (runningOps.isEmpty()) return newWarnings;

        for (Operation op : runningOps) {
            List<TrackingRecord> records = trackingRecordMapper.selectList(
                    new LambdaQueryWrapper<TrackingRecord>()
                            .eq(TrackingRecord::getOperationId, op.getId())
                            .orderByAsc(TrackingRecord::getPredictedArrivalTime));

            TrackingRecord firstUnarrivedKey = null;
            boolean anyArrived = false;
            for (TrackingRecord r : records) {
                if (r.getActualArrivalTime() != null) {
                    anyArrived = true;
                    continue;
                }
                if (firstUnarrivedKey == null && r.getIsKeyStation() != null && r.getIsKeyStation() == 1) {
                    firstUnarrivedKey = r;
                }
            }

            if (firstUnarrivedKey == null) continue;
            if (!anyArrived) continue;

            long hoursOverdue = java.time.Duration.between(
                    firstUnarrivedKey.getPredictedArrivalTime(), LocalDateTime.now()).toHours();
            if (hoursOverdue < STUCK_THRESHOLD_HOURS) continue;

            // Dedup: check if there's already an unresolved stuck warning for this operation
            long existingCount = count(new LambdaQueryWrapper<Warning>()
                    .eq(Warning::getOperationId, op.getId())
                    .eq(Warning::getWarningType, WarningType.卡阻.getLabel())
                    .ne(Warning::getStatus, WarningStatus.已关闭.getLabel()));
            if (existingCount > 0) continue;

            Warning warning = new Warning();
            warning.setOperationId(op.getId());
            warning.setWarningType(WarningType.卡阻.getLabel());
            warning.setLevel(hoursOverdue > 6 ? WarningLevel.高.getLabel() : WarningLevel.中.getLabel());
            warning.setContent(String.format("清管器可能卡阻：预计 %s 到达关键站 #%d，已延误 %d 小时未反馈",
                    firstUnarrivedKey.getPredictedArrivalTime().toString().replace("T", " "),
                    firstUnarrivedKey.getStationId(), hoursOverdue));
            warning.setSuggestion(buildStuckSuggestion(hoursOverdue, firstUnarrivedKey));
            warning.setStatus(WarningStatus.未处理.getLabel());
            save(warning);
            newWarnings.add(warning);
        }
        return newWarnings;
    }

    @Override
    @Transactional
    public void confirm(Long id) {
        Warning warning = getById(id);
        if (warning == null) throw new IllegalArgumentException("预警不存在");
        warning.setStatus(WarningStatus.已确认.getLabel());
        updateById(warning);
    }

    @Override
    @Transactional
    public void resolve(Long id, String remark) {
        Warning warning = getById(id);
        if (warning == null) throw new IllegalArgumentException("预警不存在");
        warning.setStatus(WarningStatus.已关闭.getLabel());
        warning.setResolvedTime(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            warning.setRemark(remark);
        }
        updateById(warning);
    }

    // ---- suggestion templates ----

    private String buildDelaySuggestion(long delayMinutes) {
        long absMin = Math.abs(delayMinutes);
        if (delayMinutes > 0) {
            if (absMin > 120) return "延迟严重，建议立即排查：1）检查清管器是否卡阻；2）核实管线压力/排量参数是否正常；3）必要时启动应急清管预案";
            return "请关注后续站点的运行情况，必要时调整排量或压力参数";
        }
        return "清管器提前到达，请核实：1）排量/压力参数设置是否偏高；2）管内介质流速是否异常；3）管段距离数据是否准确";
    }

    private String buildSpeedSuggestion(String direction, BigDecimal sigma) {
        if ("偏慢".equals(direction)) {
            return "清管器速度偏慢，建议：1）检查管内是否存在堵塞或沉积物堆积；2）适当增大排量或压差；3）如持续偏慢考虑应急清管";
        }
        return "清管器速度偏快，建议：1）确认排量/压力参数录入是否准确；2）检查管段是否存在泄漏风险；3）关注下游站点实际到达时间";
    }

    private String buildStuckSuggestion(long hoursOverdue, TrackingRecord record) {
        return String.format("清管器已延误 %d 小时未到达下一站，建议：1）立即核实管线和站点工况；2）尝试调整压力/排量参数推动清管器；3）如确认卡阻，启动应急卡阻处理流程：增压推动 → 反向吹扫 → 机械切割取球", hoursOverdue);
    }
}
