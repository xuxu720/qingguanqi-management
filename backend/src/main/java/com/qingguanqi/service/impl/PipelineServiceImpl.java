package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingguanqi.entity.Pipeline;
import com.qingguanqi.entity.PipelineSegment;
import com.qingguanqi.entity.Station;
import com.qingguanqi.mapper.PipelineMapper;
import com.qingguanqi.mapper.PipelineSegmentMapper;
import com.qingguanqi.mapper.StationMapper;
import com.qingguanqi.service.PipelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineServiceImpl extends ServiceImpl<PipelineMapper, Pipeline> implements PipelineService {

    private final StationMapper stationMapper;
    private final PipelineSegmentMapper segmentMapper;

    public PipelineServiceImpl(StationMapper stationMapper, PipelineSegmentMapper segmentMapper) {
        this.stationMapper = stationMapper;
        this.segmentMapper = segmentMapper;
    }

    @Override
    @Transactional
    public void deleteCascade(Long id) {
        segmentMapper.delete(new LambdaQueryWrapper<PipelineSegment>()
            .eq(PipelineSegment::getPipelineId, id));
        stationMapper.delete(new LambdaQueryWrapper<Station>()
            .eq(Station::getPipelineId, id));
        baseMapper.deleteById(id);
    }
}
