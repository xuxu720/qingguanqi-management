package com.qingguanqi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qingguanqi.entity.Operation;
import com.qingguanqi.entity.Pig;

import java.util.List;

public interface PigService extends IService<Pig> {

    /** 变更清管器状态，校验状态流转合法性 */
    void updateStatus(Long pigId, String newStatus);

    /** 查询该清管器的所有运行记录 */
    List<Operation> getOperations(Long pigId);

    /** 分页查询该清管器的运行记录 */
    Page<Operation> getOperationsPage(Long pigId, int current, int size);
}
