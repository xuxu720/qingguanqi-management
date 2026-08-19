package com.qingguanqi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingguanqi.entity.Station;
import com.qingguanqi.mapper.StationMapper;
import com.qingguanqi.service.StationService;
import org.springframework.stereotype.Service;

@Service
public class StationServiceImpl extends ServiceImpl<StationMapper, Station> implements StationService {
}
