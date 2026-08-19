package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingguanqi.entity.PipelineSegment;
import com.qingguanqi.mapper.PipelineSegmentMapper;
import com.qingguanqi.service.PipelineSegmentService;
import org.springframework.stereotype.Service;

@Service
public class PipelineSegmentServiceImpl extends ServiceImpl<PipelineSegmentMapper, PipelineSegment> implements PipelineSegmentService {
}
