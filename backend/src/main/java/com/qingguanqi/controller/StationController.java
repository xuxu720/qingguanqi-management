package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.Station;
import com.qingguanqi.service.StationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping
    public Result<Station> create(@Valid @RequestBody Station station) {
        stationService.save(station);
        return Result.ok(station);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stationService.removeById(id);
        return Result.ok();
    }

    @PutMapping
    public Result<Station> update(@Valid @RequestBody Station station) {
        stationService.updateById(station);
        return Result.ok(station);
    }

    @GetMapping("/{id}")
    public Result<Station> getById(@PathVariable Long id) {
        return Result.ok(stationService.getById(id));
    }

    @GetMapping
    public Result<List<Station>> list(@RequestParam(required = false) Long pipelineId) {
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(pipelineId != null, Station::getPipelineId, pipelineId);
        wrapper.orderByAsc(Station::getSortOrder);
        return Result.ok(stationService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<Station>> page(@RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "10") int size) {
        Page<Station> page = new Page<>(current, size);
        page = stationService.page(page, new LambdaQueryWrapper<Station>().orderByAsc(Station::getSortOrder));
        return Result.ok(page);
    }

    @GetMapping("/by-pipeline/{pipelineId}")
    public Result<List<Station>> getByPipeline(@PathVariable Long pipelineId) {
        return Result.ok(stationService.list(
                new LambdaQueryWrapper<Station>()
                        .eq(Station::getPipelineId, pipelineId)
                        .orderByAsc(Station::getSortOrder)));
    }

    @GetMapping("/by-pipeline/{pipelineId}/page")
    public Result<Page<Station>> getByPipelinePage(@PathVariable Long pipelineId,
                                                   @RequestParam(defaultValue = "1") int current,
                                                   @RequestParam(defaultValue = "10") int size) {
        Page<Station> page = new Page<>(current, size);
        page = stationService.page(page,
                new LambdaQueryWrapper<Station>()
                        .eq(Station::getPipelineId, pipelineId)
                        .orderByAsc(Station::getSortOrder));
        return Result.ok(page);
    }
}
