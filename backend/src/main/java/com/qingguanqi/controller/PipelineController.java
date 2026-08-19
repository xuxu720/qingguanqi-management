package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.Pipeline;
import com.qingguanqi.service.PipelineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping
    public Result<Pipeline> create(@Valid @RequestBody Pipeline pipeline) {
        pipelineService.save(pipeline);
        return Result.ok(pipeline);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pipelineService.deleteCascade(id);
        return Result.ok();
    }

    @PutMapping
    public Result<Pipeline> update(@Valid @RequestBody Pipeline pipeline) {
        pipelineService.updateById(pipeline);
        return Result.ok(pipeline);
    }

    @GetMapping("/{id}")
    public Result<Pipeline> getById(@PathVariable Long id) {
        return Result.ok(pipelineService.getById(id));
    }

    @GetMapping
    public Result<List<Pipeline>> list(@RequestParam(required = false) String mediumType) {
        LambdaQueryWrapper<Pipeline> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(mediumType != null, Pipeline::getMediumType, mediumType);
        wrapper.orderByDesc(Pipeline::getCreateTime);
        return Result.ok(pipelineService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<Pipeline>> page(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int size) {
        Page<Pipeline> page = new Page<>(current, size);
        page = pipelineService.page(page, new LambdaQueryWrapper<Pipeline>().orderByDesc(Pipeline::getCreateTime));
        return Result.ok(page);
    }
}
