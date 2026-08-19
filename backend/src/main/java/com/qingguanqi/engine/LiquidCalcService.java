package com.qingguanqi.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingguanqi.dto.SegmentCalcResult;
import com.qingguanqi.entity.PipelineSegment;
import com.qingguanqi.entity.Station;
import com.qingguanqi.mapper.PipelineSegmentMapper;
import com.qingguanqi.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LiquidCalcService {

    private final StationMapper stationMapper;
    private final PipelineSegmentMapper segmentMapper;

    /**
     * 单段计算（纯函数，不查数据库）
     * @param fromStation  起始站
     * @param toStation    目标站
     * @param unitCapacity 单位管容 (m³/km)
     * @param displacement 排量 (m³/h)
     * @param startTime    起始时间
     */
    public SegmentCalcResult calcSegment(Station fromStation, Station toStation,
                                          BigDecimal unitCapacity, BigDecimal displacement,
                                          LocalDateTime startTime) {
        // 站间距 = |到达站里程 - 起始站里程|
        BigDecimal distance = toStation.getMileage().subtract(fromStation.getMileage()).abs();

        // 管容 = 站间距 × 单位管容
        BigDecimal pipeCapacity = distance.multiply(unitCapacity);

        // 运行时间 = 管容 / 排量
        BigDecimal runningTime = pipeCapacity.divide(displacement, 10, RoundingMode.HALF_UP);

        // 清管器速度 = 站间距 / 运行时间
        BigDecimal pigSpeed = distance.divide(runningTime, 4, RoundingMode.HALF_UP);

        // 预计到达时间 = 发出时间 + 运行时间
        long seconds = runningTime.multiply(BigDecimal.valueOf(3600)).longValue();
        LocalDateTime arrivalTime = startTime.plusSeconds(seconds);

        SegmentCalcResult result = new SegmentCalcResult();
        result.setFromStationName(fromStation.getName());
        result.setToStationName(toStation.getName());
        result.setDistance(distance);
        result.setUnitCapacity(unitCapacity);
        result.setPipeCapacity(pipeCapacity);
        result.setRunningTime(runningTime);
        result.setPigSpeed(pigSpeed);
        result.setEstimatedArrivalTime(arrivalTime);
        return result;
    }

    /**
     * 多站点串联计算：从起始站出发，逐段计算至管线末站
     * @param pipelineId    管线ID
     * @param fromStationId 发球站ID
     * @param displacement  排量 (m³/h)
     * @param dispatchTime  发球时间
     * @return 逐段计算结果列表
     */
    public List<SegmentCalcResult> calcPipeline(Long pipelineId, Long fromStationId, Long toStationId,
                                                 BigDecimal displacement, LocalDateTime dispatchTime) {
        List<Station> stations = stationMapper.selectList(
            new LambdaQueryWrapper<Station>()
                .eq(Station::getPipelineId, pipelineId)
                .orderByAsc(Station::getSortOrder)
        );

        List<PipelineSegment> segments = segmentMapper.selectList(
            new LambdaQueryWrapper<PipelineSegment>()
                .eq(PipelineSegment::getPipelineId, pipelineId)
        );

        Map<Long, PipelineSegment> segmentMap = segments.stream()
            .collect(Collectors.toMap(PipelineSegment::getFromStationId, s -> s));

        int startIdx = -1;
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).getId().equals(fromStationId)) {
                startIdx = i;
                break;
            }
        }
        if (startIdx == -1) {
            throw new IllegalArgumentException("起始站不属于该管线");
        }

        int endIdx = stations.size() - 1;
        if (toStationId != null) {
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).getId().equals(toStationId)) {
                    endIdx = i;
                    break;
                }
            }
            if (endIdx <= startIdx) {
                throw new IllegalArgumentException("终点站必须在起始站之后");
            }
        }

        List<SegmentCalcResult> results = new ArrayList<>();
        LocalDateTime currentTime = dispatchTime;

        for (int i = startIdx; i < endIdx; i++) {
            Station from = stations.get(i);
            Station to = stations.get(i + 1);
            PipelineSegment seg = segmentMap.get(from.getId());
            if (seg == null || seg.getUnitCapacity() == null) {
                throw new IllegalStateException(
                    "站点「" + from.getName() + "」→「" + to.getName() + "」的管段未配置单位管容");
            }

            SegmentCalcResult result = calcSegment(from, to, seg.getUnitCapacity(), displacement, currentTime);
            results.add(result);
            currentTime = result.getEstimatedArrivalTime();
        }

        return results;
    }
}
