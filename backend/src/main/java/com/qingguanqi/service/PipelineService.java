package com.qingguanqi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingguanqi.entity.Pipeline;

public interface PipelineService extends IService<Pipeline> {
    void deleteCascade(Long id);
}
