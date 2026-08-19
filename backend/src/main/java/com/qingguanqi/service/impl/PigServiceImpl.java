package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingguanqi.entity.Operation;
import com.qingguanqi.entity.Pig;
import com.qingguanqi.enums.PigStatus;
import com.qingguanqi.mapper.OperationMapper;
import com.qingguanqi.mapper.PigMapper;
import com.qingguanqi.service.PigService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PigServiceImpl extends ServiceImpl<PigMapper, Pig> implements PigService {

    private final OperationMapper operationMapper;

    public PigServiceImpl(OperationMapper operationMapper) {
        this.operationMapper = operationMapper;
    }

    @Override
    public void updateStatus(Long pigId, String newStatus) {
        Pig pig = getById(pigId);
        if (pig == null) {
            throw new IllegalArgumentException("清管器不存在");
        }

        PigStatus current = PigStatus.valueOf(pig.getStatus());
        PigStatus target = PigStatus.valueOf(newStatus);

        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("不允许从「%s」变更为「%s」", current.getLabel(), target.getLabel()));
        }

        pig.setStatus(newStatus);
        updateById(pig);
    }

    @Override
    public List<Operation> getOperations(Long pigId) {
        return operationMapper.selectList(
                new LambdaQueryWrapper<Operation>()
                        .eq(Operation::getPigId, pigId)
                        .orderByDesc(Operation::getCreateTime));
    }

    @Override
    public Page<Operation> getOperationsPage(Long pigId, int current, int size) {
        Page<Operation> page = new Page<>(current, size);
        return operationMapper.selectPage(page,
                new LambdaQueryWrapper<Operation>()
                        .eq(Operation::getPigId, pigId)
                        .orderByDesc(Operation::getCreateTime));
    }
}
