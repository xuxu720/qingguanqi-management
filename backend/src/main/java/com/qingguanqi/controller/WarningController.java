package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.Warning;
import com.qingguanqi.service.WarningService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warnings")
public class WarningController {

    private final WarningService warningService;

    public WarningController(WarningService warningService) {
        this.warningService = warningService;
    }

    @GetMapping
    public Result<List<Warning>> list(@RequestParam(required = false) Long operationId,
                                      @RequestParam(required = false) String warningType,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String level) {
        LambdaQueryWrapper<Warning> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(operationId != null, Warning::getOperationId, operationId);
        wrapper.eq(warningType != null && !warningType.isBlank(), Warning::getWarningType, warningType);
        wrapper.eq(status != null && !status.isBlank(), Warning::getStatus, status);
        wrapper.eq(level != null && !level.isBlank(), Warning::getLevel, level);
        wrapper.orderByDesc(Warning::getCreateTime);
        return Result.ok(warningService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<Warning>> page(@RequestParam(defaultValue = "1") int current,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) Long operationId,
                                      @RequestParam(required = false) String warningType,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String level) {
        Page<Warning> page = new Page<>(current, size);
        LambdaQueryWrapper<Warning> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(operationId != null, Warning::getOperationId, operationId);
        wrapper.eq(warningType != null && !warningType.isBlank(), Warning::getWarningType, warningType);
        wrapper.eq(status != null && !status.isBlank(), Warning::getStatus, status);
        wrapper.eq(level != null && !level.isBlank(), Warning::getLevel, level);
        wrapper.orderByDesc(Warning::getCreateTime);
        return Result.ok(warningService.page(page, wrapper));
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        warningService.confirm(id);
        return Result.ok();
    }

    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        warningService.resolve(id, remark);
        return Result.ok();
    }

    @PutMapping("/batch-confirm")
    public Result<Void> batchConfirm(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            warningService.confirm(id);
        }
        return Result.ok();
    }

    @PutMapping("/batch-resolve")
    public Result<Void> batchResolve(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        String remark = (String) body.get("remark");
        for (Integer rawId : rawIds) {
            warningService.resolve(rawId.longValue(), remark);
        }
        return Result.ok();
    }
}
