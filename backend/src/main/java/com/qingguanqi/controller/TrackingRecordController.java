package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.TrackingRecord;
import com.qingguanqi.service.TrackingRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracking-records")
public class TrackingRecordController {

    private final TrackingRecordService trackingService;

    public TrackingRecordController(TrackingRecordService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping
    public Result<TrackingRecord> create(@Valid @RequestBody TrackingRecord record) {
        trackingService.save(record);
        return Result.ok(record);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trackingService.removeById(id);
        return Result.ok();
    }

    @PutMapping
    public Result<TrackingRecord> update(@Valid @RequestBody TrackingRecord record) {
        trackingService.updateById(record);
        return Result.ok(record);
    }

    @GetMapping("/{id}")
    public Result<TrackingRecord> getById(@PathVariable Long id) {
        return Result.ok(trackingService.getById(id));
    }

    @GetMapping
    public Result<List<TrackingRecord>> list(@RequestParam(required = false) Long operationId) {
        LambdaQueryWrapper<TrackingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(operationId != null, TrackingRecord::getOperationId, operationId);
        wrapper.orderByAsc(TrackingRecord::getPredictedArrivalTime);
        return Result.ok(trackingService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<TrackingRecord>> page(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "10") int size) {
        Page<TrackingRecord> page = new Page<>(current, size);
        return Result.ok(trackingService.page(page));
    }
}
