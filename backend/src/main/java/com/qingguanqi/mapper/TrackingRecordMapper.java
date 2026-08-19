package com.qingguanqi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingguanqi.dto.SpeedStatsDTO;
import com.qingguanqi.entity.TrackingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface TrackingRecordMapper extends BaseMapper<TrackingRecord> {

    @Select("SELECT AVG(tr.pig_speed) as avg_speed, " +
            "STDDEV_POP(tr.pig_speed) as stddev_speed, " +
            "COUNT(*) as sample_count " +
            "FROM tracking_record tr " +
            "JOIN operation o ON tr.operation_id = o.id " +
            "WHERE tr.station_id = #{stationId} " +
            "AND o.pipeline_id = #{pipelineId} " +
            "AND o.status = '已完成' " +
            "AND tr.pig_speed IS NOT NULL")
    SpeedStatsDTO getHistoricalSpeedStats(@Param("pipelineId") Long pipelineId,
                                           @Param("stationId") Long stationId);

    @Select("SELECT AVG(tr.pig_speed) as avg_speed, " +
            "STDDEV_POP(tr.pig_speed) as stddev_speed, " +
            "COUNT(*) as sample_count " +
            "FROM tracking_record tr " +
            "JOIN operation o ON tr.operation_id = o.id " +
            "WHERE o.pipeline_id = #{pipelineId} " +
            "AND o.status = '已完成' " +
            "AND tr.pig_speed IS NOT NULL")
    SpeedStatsDTO getOverallSpeedStats(@Param("pipelineId") Long pipelineId);
}
