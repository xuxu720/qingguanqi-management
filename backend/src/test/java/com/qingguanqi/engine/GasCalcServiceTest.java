package com.qingguanqi.engine;

import com.qingguanqi.dto.GasCalcRequest;
import com.qingguanqi.dto.GasCalcResult;
import com.qingguanqi.entity.GasCompressFactor;
import com.qingguanqi.mapper.GasCompressFactorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GasCalcServiceTest {

    @Mock
    private GasCompressFactorMapper factorMapper;

    @Mock
    private com.qingguanqi.mapper.StationMapper stationMapper;

    @Mock
    private com.qingguanqi.mapper.PipelineSegmentMapper segmentMapper;

    @InjectMocks
    private GasCalcService gasCalcService;

    @BeforeEach
    void setUp() {
        // 真实 Excel 压缩因子对照表数据
        var factors = List.of(
            factor(0, 1.0000),
            factor(2.0, 0.9532),
            factor(2.5, 0.9423),
            factor(3.0, 0.9315),
            factor(3.5, 0.9209),
            factor(4.0, 0.9105),
            factor(4.5, 0.9003),
            factor(5.0, 0.8903),
            factor(5.5, 0.8806),
            factor(6.0, 0.8713),
            factor(6.5, 0.8623),
            factor(7.0, 0.8537),
            factor(7.5, 0.8455),
            factor(8.0, 0.8377),
            factor(8.5, 0.8305),
            factor(9.0, 0.8228),
            factor(9.5, 0.8177),
            factor(9.9, 0.8132)
        );
        when(factorMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(factors);
    }

    // ==================== Excel 参照值校验 ====================

    @Test
    void testExcelRow1() {
        // 西一线: 山丹→古浪, Q=3200, Z=0.8294, P=8.57, d=986.8, dist=233.76 → v=16.88
        var req = new GasCalcRequest();
        req.setFromStationMileage(bd("1609.4"));
        req.setToStationMileage(bd("1843.1"));
        req.setOutletPressure(bd("8.94"));
        req.setInletPressure(bd("8.19"));
        req.setInnerDiameter(bd("986.8"));
        req.setGasFlowRate(bd("3200"));
        req.setDispatchTime(LocalDateTime.of(2026, 5, 12, 8, 0, 0));

        GasCalcResult r = gasCalcService.calcSegment(req);

        assertEquals(0, bd("233.70").compareTo(r.getDistance().setScale(2, java.math.RoundingMode.HALF_UP)));
        assertEquals(0, bd("8.5650").compareTo(r.getAvgPressure()));
        // Z ≈ 0.8294 (interpolated: 8.5→0.8305, 8.565→~0.8293)
        assertTrue(r.getCompressFactor().compareTo(bd("0.828")) > 0
            && r.getCompressFactor().compareTo(bd("0.831")) < 0,
            "Z should be ~0.8294, got " + r.getCompressFactor());
        // v ≈ 16.88 km/h
        assertTrue(r.getTheoreticalSpeed().compareTo(bd("16.5")) > 0
            && r.getTheoreticalSpeed().compareTo(bd("17.2")) < 0,
            "speed should be ~16.88, got " + r.getTheoreticalSpeed());
    }

    @Test
    void testExcelRow5() {
        // 涩宁兰一线: Q=500, Z=0.8854, P=5.25, d=645.8 → v=10.73
        var req = new GasCalcRequest();
        req.setFromStationMileage(bd("613.8"));
        req.setToStationMileage(bd("707.7"));
        req.setOutletPressure(bd("5.30"));
        req.setInletPressure(bd("5.20"));
        req.setInnerDiameter(bd("645.8"));
        req.setGasFlowRate(bd("500"));
        req.setDispatchTime(LocalDateTime.of(2026, 5, 12, 8, 0, 0));

        GasCalcResult r = gasCalcService.calcSegment(req);

        assertEquals(0, bd("5.2500").compareTo(r.getAvgPressure()));
        assertTrue(r.getTheoreticalSpeed().compareTo(bd("10.2")) > 0
            && r.getTheoreticalSpeed().compareTo(bd("11.2")) < 0,
            "speed should be ~10.73, got " + r.getTheoreticalSpeed());
    }

    // ==================== 公式验证 ====================

    @Test
    void testDistanceAbsoluteValue() {
        var req = new GasCalcRequest();
        req.setFromStationMileage(bd("50.0"));
        req.setToStationMileage(bd("30.0"));
        req.setInnerDiameter(bd("500"));
        req.setOutletPressure(bd("3.0"));
        req.setInletPressure(bd("2.5"));
        req.setGasFlowRate(bd("1000"));
        req.setDispatchTime(LocalDateTime.now());

        GasCalcResult r = gasCalcService.calcSegment(req);
        assertEquals(0, bd("20.0").compareTo(r.getDistance()));
    }

    @Test
    void testArrivalTimeAfterDispatch() {
        var req = new GasCalcRequest();
        req.setFromStationMileage(bd("10.0"));
        req.setToStationMileage(bd("10.001"));
        req.setInnerDiameter(bd("500"));
        req.setOutletPressure(bd("3.0"));
        req.setInletPressure(bd("3.0"));
        req.setGasFlowRate(bd("1000"));
        req.setDispatchTime(LocalDateTime.of(2026, 5, 12, 8, 0, 0));

        GasCalcResult r = gasCalcService.calcSegment(req);
        assertTrue(r.getEstimatedArrivalTime().isAfter(req.getDispatchTime())
            || r.getEstimatedArrivalTime().isEqual(req.getDispatchTime()));
    }

    // ==================== 压缩因子查表/插值 ====================

    @Test
    void testExactMatchCompressFactor() {
        BigDecimal z = gasCalcService.lookupCompressFactor(bd("5.0"));
        assertEquals(0, bd("0.8903").compareTo(z));
    }

    @Test
    void testInterpolatedCompressFactor() {
        // 8.5→0.8305, 9.0→0.8228, 8.565→linear interpolation
        BigDecimal z = gasCalcService.lookupCompressFactor(bd("8.565"));
        // 0.8305 - (0.065/0.5 × 0.0077) ≈ 0.8305 - 0.001 = 0.8295
        assertTrue(z.compareTo(bd("0.8290")) > 0 && z.compareTo(bd("0.8305")) < 0,
            "Z at 8.565 should be ~0.8295, got " + z);
    }

    @Test
    void testBelowRangeCompressFactor() {
        BigDecimal z = gasCalcService.lookupCompressFactor(bd("0.5"));
        // 0→1.0, 2.0→0.9532, 插值 ≈ 0.9883
        assertTrue(z.compareTo(bd("0.98")) > 0 && z.compareTo(bd("1.0")) <= 0,
            "Z at 0.5 should be ~0.9883, got " + z);
    }

    @Test
    void testAboveRangeCompressFactor() {
        BigDecimal z = gasCalcService.lookupCompressFactor(bd("12.0"));
        assertEquals(0, bd("0.8132").compareTo(z));
    }

    @Test
    void testEmptyFactorTableThrowsException() {
        when(factorMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        assertThrows(IllegalStateException.class,
            () -> gasCalcService.lookupCompressFactor(bd("3.0")));
    }

    // ==================== 辅助 ====================

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static GasCompressFactor factor(double pressure, double z) {
        var f = new GasCompressFactor();
        f.setAvgPressure(bd(String.valueOf(pressure)));
        f.setCompressFactor(bd(String.valueOf(z)));
        return f;
    }
}
