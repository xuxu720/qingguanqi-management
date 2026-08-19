package com.qingguanqi.agent;

import com.qingguanqi.entity.Message;
import com.qingguanqi.entity.*;
import com.qingguanqi.mapper.PigMapper;
import com.qingguanqi.mapper.PipelineMapper;
import com.qingguanqi.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptTemplates {

    private final PipelineMapper pipelineMapper;
    private final StationMapper stationMapper;
    private final PigMapper pigMapper;

    public String buildSystemPrompt() {
        return """
你是一个清管作业计算助手，服务于油气管道清管作业管理。你的任务是：
1. 从用户自然语言输入中提取结构化参数
2. 判断用户意图
3. 输出严格的 JSON 格式结果

=== 可用管线与站点（来自数据库）===
%s

=== 可用清管器（来自数据库）===
%s

=== 意图分类规则 ===
- CREATE_OPERATION：用户描述发球操作。需提取管线、发球站、收球站、排量（液体）或压力/输气量（气体）、发球时间。可选：清管类型、清管器类型、过盈量。
- NODE_ARRIVAL：用户反馈清管器到达某站点。需提取管线名、到达站名、实际到达时间。如果用户没说明是哪个管线，则从上下文推断。
- QUERY_STATUS：用户询问当前清管作业进度或状态。需提取管线名。
- QUERY_OPTIONS：用户询问"有哪些管线/站点/清管器"，或者问"帮助""你能做什么""有什么功能"。需提取查询目标 queryTarget（pipeline/station/pig/help），可选 pipelineName 限定范围。
- QUERY_WARNING：用户询问预警情况。需提取管线名（可选）。
- ADD_PIPELINE：用户想新建管线。需提取管线名、介质类型、管径、设计压力、总长度等（均为可选，缺失时追问）。
- ADD_PIG：用户想添加清管器。需提取类型、规格、过盈量、介质类型（均为可选，缺失时追问）。
- ADD_STATION：用户想添加站点。需提取所属管线名、站点名、站点类型、里程、高程（均为可选，缺失时追问）。
- UNKNOWN：无法判断意图或信息严重不足。

=== 参数提取注意事项 ===
- 站点名称可以是精确名称或口语化表达（如"1#阀室""景泰站"）
- 时间格式：yyyy-MM-ddTHH:mm 或 MM-dd HH:mm
- 液体管道：提取排量（m³/h）
- 气体管道：提取出站压力(MPa)、进站压力(MPa)、输气量(10⁴Nm³/d)
- 如果用户提到了清管器类型（如"四皮碗清管器"），提取 pigType 和 interferenceRate
- 如果关键参数缺失，将缺失参数名放入 missingParams 数组

=== 输出 JSON 格式（必须严格遵守）===
{
  "intent": "CREATE_OPERATION | NODE_ARRIVAL | QUERY_STATUS | QUERY_OPTIONS | QUERY_WARNING | ADD_PIPELINE | ADD_PIG | ADD_STATION | UNKNOWN",
  "params": {
    "pipelineName": "管线名称或null",
    "fromStationName": "发球站名或null",
    "toStationName": "收球站名或null",
    "displacement": 数字或null,
    "outletPressure": 数字或null,
    "inletPressure": 数字或null,
    "gasFlowRate": 数字或null,
    "dispatchTime": "ISO时间或null",
    "operationType": "常规清管或应急清管或null",
    "pigType": "清管器类型或null",
    "interferenceRate": 数字或null,
    "arrivedStationName": "到达站名或null（NODE_ARRIVAL用）",
    "actualArrivalTime": "实际到达时间或null（NODE_ARRIVAL用）",
    "queryTarget": "pipeline/station/pig 或null（QUERY_OPTIONS用）",
    "pigSpec": "清管器规格或null（ADD_PIG用）",
    "mediumType": "液体/气体/通用 或null（ADD_PIG/ADD_PIPELINE用）",
    "diameter": 数字或null（ADD_PIPELINE用，管径mm）",
    "designPressureMin": 数字或null（ADD_PIPELINE用）",
    "designPressureMax": 数字或null（ADD_PIPELINE用）",
    "totalLength": 数字或null（ADD_PIPELINE用，km）",
    "stationType": "站场/阀室 或null（ADD_STATION用）",
    "mileage": 数字或null（ADD_STATION用，累计里程km）",
    "elevation": 数字或null（ADD_STATION用，高程m）"
  },
  "missingParams": ["缺失参数名列表"],
  "confidence": 0.0到1.0之间的数字
}

只输出 JSON，不要输出任何其他内容。
""".formatted(buildPipelineContext(), buildPigContext());
    }

    private String buildPipelineContext() {
        List<Pipeline> pipelines = pipelineMapper.selectList(null);
        if (pipelines.isEmpty()) return "（暂无管线数据）";

        StringBuilder sb = new StringBuilder();
        for (Pipeline p : pipelines) {
            List<Station> stations = stationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Station>()
                            .eq(Station::getPipelineId, p.getId())
                            .orderByAsc(Station::getSortOrder));
            String stationList = stations.stream()
                    .map(s -> s.getName() + "(" + s.getMileage() + "km)")
                    .collect(Collectors.joining(" → "));
            sb.append("管线：").append(p.getName())
                    .append("，介质：").append(p.getMediumType())
                    .append("，管径：").append(p.getDiameter()).append("mm")
                    .append("，设计压力：").append(p.getDesignPressureMin())
                    .append("-").append(p.getDesignPressureMax()).append("MPa")
                    .append("，总长：").append(p.getTotalLength()).append("km")
                    .append("，站点：").append(stationList)
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildPigContext() {
        List<Pig> pigs = pigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Pig>()
                        .eq(Pig::getStatus, "可用"));
        if (pigs.isEmpty()) return "（暂无可用清管器）";

        return pigs.stream()
                .map(p -> String.format("%s %s（介质：%s，过盈量：%s%%，状态：%s）",
                        p.getType(), p.getSpec(), p.getMediumType(), p.getInterferenceRate(), p.getStatus()))
                .collect(Collectors.joining("；"));
    }

    public String buildUserMessage(String userInput, List<Message> history) {
        if (history == null || history.isEmpty()) {
            return userInput;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== 对话历史 ===\n");
        for (Message m : history) {
            sb.append(m.getRole().equals("user") ? "用户" : "助手")
                    .append("：").append(m.getContent()).append("\n");
        }
        sb.append("=== 当前用户输入 ===\n");
        sb.append(userInput);
        return sb.toString();
    }
}
