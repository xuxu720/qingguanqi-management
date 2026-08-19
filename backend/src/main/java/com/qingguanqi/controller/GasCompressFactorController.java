package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.GasCompressFactor;
import com.qingguanqi.service.GasCompressFactorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/gas-compress-factors")
public class GasCompressFactorController {

    private final GasCompressFactorService factorService;

    public GasCompressFactorController(GasCompressFactorService factorService) {
        this.factorService = factorService;
    }

    @PostMapping
    public Result<GasCompressFactor> create(@Valid @RequestBody GasCompressFactor factor) {
        factorService.save(factor);
        return Result.ok(factor);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        factorService.removeById(id);
        return Result.ok();
    }

    @PutMapping
    public Result<GasCompressFactor> update(@Valid @RequestBody GasCompressFactor factor) {
        factorService.updateById(factor);
        return Result.ok(factor);
    }

    @GetMapping("/{id}")
    public Result<GasCompressFactor> getById(@PathVariable Long id) {
        return Result.ok(factorService.getById(id));
    }

    @GetMapping
    public Result<List<GasCompressFactor>> list() {
        LambdaQueryWrapper<GasCompressFactor> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(GasCompressFactor::getAvgPressure);
        return Result.ok(factorService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<GasCompressFactor>> page(@RequestParam(defaultValue = "1") int current,
                                                @RequestParam(defaultValue = "10") int size) {
        Page<GasCompressFactor> page = new Page<>(current, size);
        page = factorService.page(page, new LambdaQueryWrapper<GasCompressFactor>().orderByAsc(GasCompressFactor::getAvgPressure));
        return Result.ok(page);
    }
}
