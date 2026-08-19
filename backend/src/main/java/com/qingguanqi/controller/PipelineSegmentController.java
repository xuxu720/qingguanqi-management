package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.PipelineSegment;
import com.qingguanqi.service.PipelineSegmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pipeline-segments")
public class PipelineSegmentController {

    private final PipelineSegmentService segmentService;

    public PipelineSegmentController(PipelineSegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @PostMapping
    public Result<PipelineSegment> create(@Valid @RequestBody PipelineSegment segment) {
        segmentService.save(segment);
        return Result.ok(segment);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        segmentService.removeById(id);
        return Result.ok();
    }

    @PutMapping
    public Result<PipelineSegment> update(@Valid @RequestBody PipelineSegment segment) {
        segmentService.updateById(segment);
        return Result.ok(segment);
    }

    @GetMapping("/{id}")
    public Result<PipelineSegment> getById(@PathVariable Long id) {
        return Result.ok(segmentService.getById(id));
    }

    @GetMapping
    public Result<List<PipelineSegment>> list(@RequestParam(required = false) Long pipelineId) {
        LambdaQueryWrapper<PipelineSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(pipelineId != null, PipelineSegment::getPipelineId, pipelineId);
        return Result.ok(segmentService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<PipelineSegment>> page(@RequestParam(defaultValue = "1") int current,
                                              @RequestParam(defaultValue = "10") int size) {
        Page<PipelineSegment> page = new Page<>(current, size);
        return Result.ok(segmentService.page(page));
    }

    @GetMapping("/by-pipeline/{pipelineId}")
    public Result<List<PipelineSegment>> getByPipeline(@PathVariable Long pipelineId) {
        return Result.ok(segmentService.list(
                new LambdaQueryWrapper<PipelineSegment>()
                        .eq(PipelineSegment::getPipelineId, pipelineId)));
    }

    @GetMapping("/by-pipeline/{pipelineId}/page")
    public Result<Page<PipelineSegment>> getByPipelinePage(@PathVariable Long pipelineId,
                                                           @RequestParam(defaultValue = "1") int current,
                                                           @RequestParam(defaultValue = "10") int size) {
        Page<PipelineSegment> page = new Page<>(current, size);
        page = segmentService.page(page,
                new LambdaQueryWrapper<PipelineSegment>()
                        .eq(PipelineSegment::getPipelineId, pipelineId));
        return Result.ok(page);
    }
}
