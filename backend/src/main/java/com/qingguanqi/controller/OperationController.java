package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingguanqi.dto.OperationVO;
import com.qingguanqi.dto.Result;
import com.qingguanqi.dto.TrackingRecordVO;
import com.qingguanqi.entity.Operation;
import com.qingguanqi.entity.Pig;
import com.qingguanqi.entity.Pipeline;
import com.qingguanqi.entity.Station;
import com.qingguanqi.entity.TrackingRecord;
import com.qingguanqi.entity.Warning;
import com.qingguanqi.mapper.PigMapper;
import com.qingguanqi.mapper.PipelineMapper;
import com.qingguanqi.mapper.StationMapper;
import com.qingguanqi.mapper.WarningMapper;
import com.qingguanqi.service.OperationService;
import com.qingguanqi.service.TrackingRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/operations")
public class OperationController {

    private final OperationService operationService;
    private final TrackingRecordService trackingRecordService;
    private final WarningMapper warningMapper;
    private final PipelineMapper pipelineMapper;
    private final StationMapper stationMapper;
    private final PigMapper pigMapper;

    public OperationController(OperationService operationService, TrackingRecordService trackingRecordService,
                               WarningMapper warningMapper, PipelineMapper pipelineMapper,
                               StationMapper stationMapper, PigMapper pigMapper) {
        this.operationService = operationService;
        this.trackingRecordService = trackingRecordService;
        this.warningMapper = warningMapper;
        this.pipelineMapper = pipelineMapper;
        this.stationMapper = stationMapper;
        this.pigMapper = pigMapper;
    }

    @PostMapping
    public Result<Operation> create(@Valid @RequestBody Operation operation) {
        operationService.createWithTracking(operation);
        return Result.ok(operation);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Operation op = operationService.getById(id);
        if (op == null) {
            return Result.fail(404, "作业不存在");
        }
        if ("运行中".equals(op.getStatus())) {
            return Result.fail(400, "作业正在执行中，无法删除。请等待作业完成或标记为异常后再操作");
        }
        // cascade: warnings → tracking records → operation
        warningMapper.delete(new LambdaQueryWrapper<Warning>().eq(Warning::getOperationId, id));
        // 先解除 tracking_record 自引用 FK（parent_record_id 指向同操作的被修正记录）
        // 用 setSql 而非 .set(column, null)，避免 MyBatis-Plus 默认 null 跳过策略
        trackingRecordService.update(
            new UpdateWrapper<TrackingRecord>()
                .setSql("parent_record_id = NULL")
                .eq("operation_id", id));
        trackingRecordService.remove(
            new LambdaQueryWrapper<TrackingRecord>().eq(TrackingRecord::getOperationId, id));
        operationService.removeById(id);
        return Result.ok();
    }

    @PutMapping
    public Result<Operation> update(@Valid @RequestBody Operation operation) {
        operationService.updateById(operation);
        return Result.ok(operation);
    }

    @GetMapping("/{id}")
    public Result<Operation> getById(@PathVariable Long id) {
        return Result.ok(operationService.getById(id));
    }

    @GetMapping
    public Result<List<OperationVO>> list(@RequestParam(required = false) Long pipelineId,
                                          @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Operation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(pipelineId != null, Operation::getPipelineId, pipelineId);
        wrapper.eq(status != null, Operation::getStatus, status);
        wrapper.orderByDesc(Operation::getCreateTime);
        List<Operation> operations = operationService.list(wrapper);

        if (operations.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 批量加载关联数据，避免 N+1 问题
        Set<Long> pipelineIds = operations.stream().map(Operation::getPipelineId).collect(Collectors.toSet());
        Set<Long> stationIds = operations.stream()
            .flatMap(o -> java.util.stream.Stream.of(o.getFromStationId(), o.getToStationId()))
            .collect(Collectors.toSet());
        Set<Long> pigIds = operations.stream().map(Operation::getPigId).collect(Collectors.toSet());

        Map<Long, Pipeline> pipelineMap = pipelineMapper.selectBatchIds(pipelineIds).stream()
            .collect(Collectors.toMap(Pipeline::getId, Function.identity()));
        Map<Long, Station> stationMap = stationMapper.selectBatchIds(stationIds).stream()
            .collect(Collectors.toMap(Station::getId, Function.identity()));
        Map<Long, Pig> pigMap = pigMapper.selectBatchIds(pigIds).stream()
            .collect(Collectors.toMap(Pig::getId, Function.identity()));

        List<OperationVO> vos = operations.stream().map(o -> {
            OperationVO vo = new OperationVO();
            vo.setId(o.getId());
            vo.setPipelineId(o.getPipelineId());
            vo.setPigId(o.getPigId());
            vo.setOperationType(o.getOperationType());
            vo.setFromStationId(o.getFromStationId());
            vo.setToStationId(o.getToStationId());
            vo.setDispatchTime(o.getDispatchTime());
            vo.setDisplacement(o.getDisplacement());
            vo.setGasFlowRate(o.getGasFlowRate());
            vo.setOutletPressure(o.getOutletPressure());
            vo.setInletPressure(o.getInletPressure());
            vo.setStatus(o.getStatus());
            vo.setRemark(o.getRemark());
            vo.setCreateTime(o.getCreateTime());
            vo.setUpdateTime(o.getUpdateTime());

            Pipeline pipeline = pipelineMap.get(o.getPipelineId());
            vo.setPipelineName(pipeline != null ? pipeline.getName() : null);

            Station fromStation = stationMap.get(o.getFromStationId());
            vo.setFromStationName(fromStation != null ? fromStation.getName() : null);

            Station toStation = stationMap.get(o.getToStationId());
            vo.setToStationName(toStation != null ? toStation.getName() : null);

            Pig pig = pigMap.get(o.getPigId());
            if (pig != null) {
                vo.setPigName(pig.getType() + " " + pig.getSpec());
                vo.setPigType(pig.getType());
                vo.setPigSpec(pig.getSpec());
            }
            return vo;
        }).collect(Collectors.toList());

        return Result.ok(vos);
    }

    @GetMapping("/page")
    public Result<Page<Operation>> page(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int size) {
        Page<Operation> page = new Page<>(current, size);
        page = operationService.page(page, new LambdaQueryWrapper<Operation>().orderByDesc(Operation::getCreateTime));
        return Result.ok(page);
    }

    @PostMapping("/{id}/node-arrival")
    public Result<Void> nodeArrival(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long stationId = Long.valueOf(body.get("stationId").toString());
        LocalDateTime actualArrivalTime = LocalDateTime.parse(body.get("actualArrivalTime").toString());
        operationService.nodeArrival(id, stationId, actualArrivalTime);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        operationService.updateStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/{id}/tracking")
    public Result<List<TrackingRecordVO>> getTracking(@PathVariable Long id) {
        return Result.ok(trackingRecordService.getByOperation(id));
    }
}
