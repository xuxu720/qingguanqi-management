package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.Operation;
import com.qingguanqi.entity.Pig;
import com.qingguanqi.service.PigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pigs")
public class PigController {

    private final PigService pigService;

    public PigController(PigService pigService) {
        this.pigService = pigService;
    }

    @PostMapping
    public Result<Pig> create(@Valid @RequestBody Pig pig) {
        pigService.save(pig);
        return Result.ok(pig);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pigService.removeById(id);
        return Result.ok();
    }

    @PutMapping
    public Result<Pig> update(@Valid @RequestBody Pig pig) {
        pigService.updateById(pig);
        return Result.ok(pig);
    }

    @GetMapping("/{id}")
    public Result<Pig> getById(@PathVariable Long id) {
        return Result.ok(pigService.getById(id));
    }

    @GetMapping
    public Result<List<Pig>> list(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Pig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null, Pig::getType, type);
        wrapper.eq(status != null, Pig::getStatus, status);
        wrapper.orderByDesc(Pig::getCreateTime);
        return Result.ok(pigService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<Pig>> page(@RequestParam(defaultValue = "1") int current,
                                  @RequestParam(defaultValue = "10") int size) {
        Page<Pig> page = new Page<>(current, size);
        page = pigService.page(page, new LambdaQueryWrapper<Pig>().orderByDesc(Pig::getCreateTime));
        return Result.ok(page);
    }

    /** 变更清管器状态 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        pigService.updateStatus(id, status);
        return Result.ok();
    }

    /** 查询该清管器所有运行记录 */
    @GetMapping("/{id}/operations")
    public Result<List<Operation>> getOperations(@PathVariable Long id) {
        return Result.ok(pigService.getOperations(id));
    }

    /** 分页查询该清管器运行记录 */
    @GetMapping("/{id}/operations/page")
    public Result<Page<Operation>> getOperationsPage(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "1") int current,
                                                     @RequestParam(defaultValue = "10") int size) {
        return Result.ok(pigService.getOperationsPage(id, current, size));
    }
}
