package com.qingguanqi.controller;

import com.qingguanqi.dto.GasCalcRequest;
import com.qingguanqi.dto.GasCalcResult;
import com.qingguanqi.dto.Result;
import com.qingguanqi.dto.SegmentCalcResult;
import com.qingguanqi.engine.GasCalcService;
import com.qingguanqi.engine.LiquidCalcService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/calc")
@RequiredArgsConstructor
public class CalcController {

    private final GasCalcService gasCalcService;
    private final LiquidCalcService liquidCalcService;

    @PostMapping("/gas-segment")
    public Result<GasCalcResult> calcGasSegment(@Valid @RequestBody GasCalcRequest request) {
        return Result.ok(gasCalcService.calcSegment(request));
    }

    @PostMapping("/gas-pipeline")
    public Result<List<GasCalcResult>> calcGasPipeline(
            @RequestParam Long pipelineId,
            @RequestParam Long fromStationId,
            @RequestParam(required = false) Long toStationId,
            @RequestParam BigDecimal firstOutletPressure,
            @RequestParam BigDecimal lastInletPressure,
            @RequestParam BigDecimal gasFlowRate,
            @RequestParam LocalDateTime dispatchTime) {
        return Result.ok(gasCalcService.calcPipeline(
            pipelineId, fromStationId, toStationId, firstOutletPressure, lastInletPressure, gasFlowRate, dispatchTime));
    }

    @PostMapping("/liquid-pipeline")
    public Result<List<SegmentCalcResult>> calcLiquidPipeline(
            @RequestParam Long pipelineId,
            @RequestParam Long fromStationId,
            @RequestParam(required = false) Long toStationId,
            @RequestParam BigDecimal displacement,
            @RequestParam LocalDateTime dispatchTime) {
        return Result.ok(liquidCalcService.calcPipeline(pipelineId, fromStationId, toStationId, displacement, dispatchTime));
    }
}
