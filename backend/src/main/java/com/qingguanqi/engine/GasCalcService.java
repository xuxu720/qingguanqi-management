package com.qingguanqi.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingguanqi.dto.GasCalcRequest;
import com.qingguanqi.dto.GasCalcResult;
import com.qingguanqi.entity.GasCompressFactor;
import com.qingguanqi.entity.PipelineSegment;
import com.qingguanqi.entity.Station;
import com.qingguanqi.mapper.GasCompressFactorMapper;
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
public class GasCalcService {

    private final GasCompressFactorMapper factorMapper;
    private final StationMapper stationMapper;
    private final PipelineSegmentMapper segmentMapper;

    /** Excel 公式使用 3.14 而非 Math.PI，需保持一致 */
    private static final BigDecimal PI = new BigDecimal("3.14");

    /**
     * 单段气体管道计算
     * 公式来源 Excel: v = Q × Z / (24 × P_avg × A)  其中 A = π × (d/2000)²
     */
    public GasCalcResult calcSegment(GasCalcRequest req) {
        BigDecimal distance = req.getToStationMileage().subtract(req.getFromStationMileage()).abs();

        BigDecimal avgPressure = req.getOutletPressure().add(req.getInletPressure())
            .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

        BigDecimal compressFactor = lookupCompressFactor(avgPressure);

        // 截面积 A = π × (d/2000)²   (内径 mm → m)
        BigDecimal radiusM = req.getInnerDiameter()
            .divide(BigDecimal.valueOf(2000), 10, RoundingMode.HALF_UP);
        BigDecimal area = radiusM.pow(2).multiply(PI).setScale(10, RoundingMode.HALF_UP);

        // 理论速度 = Q × Z / (24 × P_avg × A)  → km/h（Excel 经验公式）
        BigDecimal speedKmh = req.getGasFlowRate()
            .multiply(compressFactor)
            .divide(
                BigDecimal.valueOf(24).multiply(avgPressure).multiply(area),
                10, RoundingMode.HALF_UP);

        BigDecimal runningTime = distance.divide(speedKmh, 10, RoundingMode.HALF_UP);

        long seconds = runningTime.multiply(BigDecimal.valueOf(3600)).longValue();
        LocalDateTime arrivalTime = req.getDispatchTime().plusSeconds(seconds);

        GasCalcResult result = new GasCalcResult();
        result.setDistance(distance);
        result.setAvgPressure(avgPressure);
        result.setCompressFactor(compressFactor);
        result.setCrossSectionArea(area);
        result.setTheoreticalSpeed(speedKmh);
        result.setRunningTime(runningTime);
        result.setEstimatedArrivalTime(arrivalTime);
        return result;
    }

    /**
     * 多站点串联气体管道计算。压缩因子统一、球速匀速。
     */
    public List<GasCalcResult> calcPipeline(Long pipelineId, Long fromStationId, Long toStationId,
                                             BigDecimal firstOutletPressure, BigDecimal lastInletPressure,
                                             BigDecimal gasFlowRate, LocalDateTime dispatchTime) {
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

        int endIdx = stations.size();
        if (toStationId != null) {
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).getId().equals(toStationId)) {
                    endIdx = i + 1;
                    break;
                }
            }
            if (endIdx <= startIdx + 1) {
                throw new IllegalArgumentException("终点站必须在起始站之后");
            }
        }

        List<Station> chainStations = stations.subList(startIdx, endIdx);
        BigDecimal totalDistance = chainStations.get(chainStations.size() - 1).getMileage()
            .subtract(chainStations.get(0).getMileage());

        // 统一压缩因子：使用全线平均压力查表
        BigDecimal overallAvgPressure = firstOutletPressure.add(lastInletPressure)
            .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal uniformZ = lookupCompressFactor(overallAvgPressure);

        // 获取管径（取首段，同管线各段内径一致）
        PipelineSegment firstSeg = segmentMap.get(chainStations.get(0).getId());
        if (firstSeg == null || firstSeg.getInnerDiameter() == null) {
            throw new IllegalStateException("管线管段未配置管道内径");
        }
        BigDecimal radiusM = firstSeg.getInnerDiameter()
            .divide(BigDecimal.valueOf(2000), 10, RoundingMode.HALF_UP);
        BigDecimal area = radiusM.pow(2).multiply(PI).setScale(10, RoundingMode.HALF_UP);

        // 匀速计算：全线统一球速
        BigDecimal uniformSpeed = gasFlowRate.multiply(uniformZ)
            .divide(BigDecimal.valueOf(24).multiply(overallAvgPressure).multiply(area),
                    10, RoundingMode.HALF_UP);

        List<GasCalcResult> results = new ArrayList<>();
        LocalDateTime currentTime = dispatchTime;

        for (int i = 0; i < chainStations.size() - 1; i++) {
            Station from = chainStations.get(i);
            Station to = chainStations.get(i + 1);

            BigDecimal segDistance = to.getMileage().subtract(from.getMileage());
            BigDecimal runningTime = segDistance.divide(uniformSpeed, 10, RoundingMode.HALF_UP);
            long seconds = runningTime.multiply(BigDecimal.valueOf(3600)).longValue();
            LocalDateTime arrivalTime = currentTime.plusSeconds(seconds);

            GasCalcResult result = new GasCalcResult();
            result.setDistance(segDistance);
            result.setAvgPressure(overallAvgPressure);
            result.setCompressFactor(uniformZ);
            result.setCrossSectionArea(area);
            result.setTheoreticalSpeed(uniformSpeed);
            result.setRunningTime(runningTime);
            result.setEstimatedArrivalTime(arrivalTime);
            results.add(result);

            currentTime = arrivalTime;
        }

        return results;
    }

    BigDecimal lookupCompressFactor(BigDecimal avgPressure) {
        List<GasCompressFactor> factors = factorMapper.selectList(
            new LambdaQueryWrapper<GasCompressFactor>()
                .orderByAsc(GasCompressFactor::getAvgPressure)
        );

        if (factors.isEmpty()) {
            throw new IllegalStateException("压缩因子表无数据，请先在 gas_compress_factor 表中录入数据");
        }

        for (GasCompressFactor f : factors) {
            if (f.getAvgPressure().compareTo(avgPressure) == 0) {
                return f.getCompressFactor();
            }
        }

        GasCompressFactor lower = null, upper = null;
        for (GasCompressFactor f : factors) {
            if (f.getAvgPressure().compareTo(avgPressure) < 0) {
                lower = f;
            } else if (f.getAvgPressure().compareTo(avgPressure) > 0) {
                upper = f;
                break;
            }
        }

        if (lower == null) return factors.get(0).getCompressFactor();
        if (upper == null) return factors.get(factors.size() - 1).getCompressFactor();

        BigDecimal pDiff = upper.getAvgPressure().subtract(lower.getAvgPressure());
        BigDecimal zDiff = upper.getCompressFactor().subtract(lower.getCompressFactor());
        BigDecimal ratio = avgPressure.subtract(lower.getAvgPressure())
            .divide(pDiff, 10, RoundingMode.HALF_UP);
        return lower.getCompressFactor().add(zDiff.multiply(ratio)).setScale(6, RoundingMode.HALF_UP);
    }
}
