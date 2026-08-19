package com.qingguanqi.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingguanqi.dto.AgentReply;
import com.qingguanqi.dto.TrackingRecordVO;
import com.qingguanqi.dto.Widget;
import com.qingguanqi.entity.*;
import com.qingguanqi.mapper.*;
import com.qingguanqi.service.OperationService;
import com.qingguanqi.service.TrackingRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final DeepSeekClient deepSeekClient;
    private final PromptTemplates promptTemplates;
    private final OperationService operationService;
    private final TrackingRecordService trackingRecordService;
    private final PipelineMapper pipelineMapper;
    private final StationMapper stationMapper;
    private final PigMapper pigMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final WarningMapper warningMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("M月d日HH:mm");

    // ==================== Natural language param labels ====================

    private static final Map<String, String> PARAM_LABELS = Map.ofEntries(
        Map.entry("pipelineName", "管线名称"),
        Map.entry("fromStationName", "发球站"),
        Map.entry("toStationName", "收球站"),
        Map.entry("displacement", "排量（m³/h）"),
        Map.entry("dispatchTime", "发球时间"),
        Map.entry("gasFlowRate", "输气量（10⁴Nm³/d）"),
        Map.entry("outletPressure", "出站压力（MPa）"),
        Map.entry("inletPressure", "进站压力（MPa）"),
        Map.entry("operationType", "作业类型"),
        Map.entry("pigType", "清管器类型"),
        Map.entry("interferenceRate", "过盈量（%）"),
        Map.entry("pigSpec", "清管器规格"),
        Map.entry("mediumType", "介质类型"),
        Map.entry("diameter", "管径（mm）"),
        Map.entry("designPressureMin", "设计压力下限（MPa）"),
        Map.entry("designPressureMax", "设计压力上限（MPa）"),
        Map.entry("totalLength", "总长度（km）"),
        Map.entry("stationName", "站点名称"),
        Map.entry("stationType", "站点类型"),
        Map.entry("mileage", "累计里程（km）"),
        Map.entry("elevation", "高程（m）"),
        Map.entry("arrivedStationName", "到达站点"),
        Map.entry("actualArrivalTime", "实际到达时间")
    );

    private static final Map<String, String> PARAM_HINTS = Map.ofEntries(
        Map.entry("displacement", "m³/h，液体管道填写"),
        Map.entry("dispatchTime", "格式：年-月-日 时:分，例如 2026-06-01 08:00"),
        Map.entry("gasFlowRate", "10⁴Nm³/d，气体管道填写"),
        Map.entry("outletPressure", "MPa，气体管道填写"),
        Map.entry("inletPressure", "MPa，气体管道填写"),
        Map.entry("interferenceRate", "百分比，例如 3 表示 3%"),
        Map.entry("pigSpec", "例如 DN200、DN150"),
        Map.entry("diameter", "管线内径，例如 219、325、426"),
        Map.entry("designPressureMin", "例如 4.0"),
        Map.entry("designPressureMax", "例如 6.4"),
        Map.entry("totalLength", "管线全长，例如 120.5"),
        Map.entry("mileage", "从管线起点起算的距离（km）"),
        Map.entry("elevation", "可选，站点海拔高度（m）"),
        Map.entry("stationName", "例如 景泰站、3#阀室"),
        Map.entry("arrivedStationName", "清管器当前到达的站点名称"),
        Map.entry("actualArrivalTime", "格式：年-月-日 时:分，例如 2026-06-01 14:30")
    );

    private String paramLabel(String key) {
        return PARAM_LABELS.getOrDefault(key, key);
    }

    // ==================== Main chat entry ====================

    public AgentReply chat(String userInput, Long conversationId, String apiKey, String apiBaseUrl) {
        // 1. Load/create conversation
        Conversation conv;
        if (conversationId != null) {
            conv = conversationMapper.selectById(conversationId);
            if (conv == null) conversationId = null;
        }
        if (conversationId == null) {
            conv = new Conversation();
            String title = userInput.length() > 50 ? userInput.substring(0, 50) + "..." : userInput;
            conv.setTitle(title);
            conversationMapper.insert(conv);
            conversationId = conv.getId();
        } else {
            conv = conversationMapper.selectById(conversationId);
        }

        // 2. Load conversation history
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime));
        List<DeepSeekDTO.ChatRequest.Message> dsHistory = new ArrayList<>();
        for (Message m : history) {
            dsHistory.add(new DeepSeekDTO.ChatRequest.Message(m.getRole(), m.getContent()));
        }

        // 3. Save user message
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        messageMapper.insert(userMsg);

        // 4. Build prompt and call DeepSeek
        String systemPrompt = promptTemplates.buildSystemPrompt();
        String userPrompt = promptTemplates.buildUserMessage(userInput, history);
        String dsResponse = deepSeekClient.chat(systemPrompt, userPrompt, dsHistory, apiKey, apiBaseUrl);

        // 5. Parse DeepSeek response
        ParsedIntent parsed = parseDeepSeekResponse(dsResponse);
        if (parsed == null) {
            return buildFallbackReply(conversationId, userInput,
                "抱歉，无法解析您的输入。您可以点击下方按钮前往对应页面手动操作：");
        }

        // 5b. ADD_STATION always redirects to dedicated page
        if ("ADD_STATION".equals(parsed.intent)) {
            return handleAddStation(parsed, conversationId);
        }

        // 6. Handle missing params → follow-up with widgets
        if (parsed.missingParams != null && !parsed.missingParams.isEmpty()) {
            String followUp = buildFollowUpText(parsed);
            List<Widget> widgets = buildFollowUpWidgets(parsed, parsed.intent);
            saveAssistantMsg(conversationId, followUp, parsed.intent, null, parsed);
            return AgentReply.builder()
                    .reply(followUp).intent(parsed.intent)
                    .needFollowUp(true).conversationId(conversationId)
                    .widgets(widgets)
                    .build();
        }

        // 7. Execute based on intent
        try {
            return executeIntent(parsed, conversationId);
        } catch (Exception e) {
            log.error("Agent execution error for intent {}", parsed.intent, e);
            return buildFallbackReply(conversationId, null, "操作执行失败：" + e.getMessage() + "。请检查参数或使用手动表单。");
        }
    }

    // ==================== Intent router ====================

    private AgentReply executeIntent(ParsedIntent parsed, Long conversationId) {
        return switch (parsed.intent) {
            case "CREATE_OPERATION" -> handleCreateOperation(parsed, conversationId);
            case "NODE_ARRIVAL" -> handleNodeArrival(parsed, conversationId);
            case "QUERY_STATUS" -> handleQueryStatus(parsed, conversationId);
            case "QUERY_OPTIONS" -> handleQueryOptions(parsed, conversationId);
            case "QUERY_WARNING" -> handleQueryWarning(parsed, conversationId);
            case "ADD_PIPELINE" -> handleAddPipeline(parsed, conversationId);
            case "ADD_PIG" -> handleAddPig(parsed, conversationId);
            case "ADD_STATION" -> handleAddStation(parsed, conversationId);
            default -> buildFallbackReply(conversationId, null, "无法识别您的意图，请描述清管作业（发球/过站反馈/查询进度/查询数据/添加记录）。");
        };
    }

    // ==================== Smart follow-up ====================

    private String buildFollowUpText(ParsedIntent parsed) {
        if (parsed.missingParams == null || parsed.missingParams.isEmpty()) return null;
        List<String> labels = parsed.missingParams.stream()
                .map(this::paramLabel)
                .collect(Collectors.toList());
        if (labels.size() == 1) {
            return "请提供" + labels.get(0) + "：";
        }
        return "请提供以下信息：" + String.join("、", labels) + "。";
    }

    private List<Widget> buildFollowUpWidgets(ParsedIntent parsed, String intent) {
        if (parsed.missingParams == null || parsed.missingParams.isEmpty()) return List.of();

        // Pipeline: in ADD_PIPELINE context show form, otherwise show existing options
        if (parsed.missingParams.contains("pipelineName")) {
            if ("ADD_PIPELINE".equals(intent)) {
                return List.of(buildScalarForm(List.of("pipelineName")));
            }
            return List.of(buildPipelineOptionList());
        }

        // Stations — show for current pipeline if known
        if (parsed.missingParams.contains("fromStationName") || parsed.missingParams.contains("toStationName")) {
            Pipeline p = fuzzyMatchPipeline(parsed.pipelineName);
            if (p != null) {
                String title = parsed.missingParams.contains("fromStationName") && parsed.missingParams.contains("toStationName")
                        ? "请选择发球站和收球站（" + p.getName() + "）"
                        : "请选择站点（" + p.getName() + "）";
                return List.of(buildStationOptionList(p, title));
            }
            return List.of(buildPipelineOptionList());
        }

        // Pig: in ADD_PIG context show form, in CREATE_OPERATION show existing options
        if (parsed.missingParams.contains("pigType") || parsed.missingParams.contains("pigSpec")) {
            if ("ADD_PIG".equals(intent)) {
                return List.of(buildScalarForm(parsed.missingParams));
            }
            return List.of(buildPigOptionList());
        }

        // Arrived station: if pipeline known, show station options
        if (parsed.missingParams.contains("arrivedStationName")) {
            Pipeline p = fuzzyMatchPipeline(parsed.pipelineName);
            if (p != null) {
                return List.of(buildStationOptionList(p, "请选择到达站点（" + p.getName() + "）"));
            }
        }

        // Enum-type params → option_list
        String first = parsed.missingParams.get(0);
        return switch (first) {
            case "mediumType" -> List.of(buildMediumTypeOptions());
            case "stationType" -> List.of(buildStationTypeOptions());
            case "operationType" -> List.of(buildOperationTypeOptions());
            default -> List.of(buildScalarForm(parsed.missingParams));
        };
    }

    // ---------- Widget builders ----------

    private Widget buildPipelineOptionList() {
        List<Pipeline> pipelines = pipelineMapper.selectList(null);
        List<Widget.WidgetOption> options = pipelines.stream()
                .map(p -> Widget.WidgetOption.builder()
                        .label(p.getName())
                        .value(p.getName())
                        .description(p.getMediumType() + "，管径" + p.getDiameter() + "mm，总长" + p.getTotalLength() + "km")
                        .build())
                .collect(Collectors.toList());
        if (options.isEmpty()) {
            return Widget.builder().type("info_card").title("暂无管线数据，请先在管线管理中添加。").build();
        }
        return Widget.builder()
                .type("option_list")
                .title("请选择管线")
                .options(options)
                .build();
    }

    private Widget buildStationOptionList(Pipeline pipeline, String title) {
        List<Station> stations = stationMapper.selectList(
                new LambdaQueryWrapper<Station>()
                        .eq(Station::getPipelineId, pipeline.getId())
                        .orderByAsc(Station::getSortOrder));
        List<Widget.WidgetOption> options = stations.stream()
                .map(s -> Widget.WidgetOption.builder()
                        .label(s.getName())
                        .value(s.getName())
                        .description(s.getStationType() + "，" + s.getMileage() + "km" + (s.getElevation() != null ? "，高程" + s.getElevation() + "m" : ""))
                        .build())
                .collect(Collectors.toList());
        return Widget.builder()
                .type("option_list")
                .title(title)
                .description("点击选择站点")
                .options(options)
                .build();
    }

    private Widget buildPigOptionList() {
        List<Pig> pigs = pigMapper.selectList(
                new LambdaQueryWrapper<Pig>().eq(Pig::getStatus, "可用"));
        List<Widget.WidgetOption> options = pigs.stream()
                .map(p -> Widget.WidgetOption.builder()
                        .label(p.getType() + " " + p.getSpec())
                        .value(p.getType() + " " + p.getSpec())
                        .description("介质：" + p.getMediumType() + "，过盈量：" + p.getInterferenceRate() + "%")
                        .build())
                .collect(Collectors.toList());
        if (options.isEmpty()) {
            return Widget.builder().type("info_card").title("暂无可用清管器，请先在清管器管理中添加。").build();
        }
        return Widget.builder()
                .type("option_list")
                .title("请选择清管器")
                .options(options)
                .build();
    }

    private Widget buildMediumTypeOptions() {
        return Widget.builder()
                .type("option_list")
                .title("请选择介质类型")
                .options(List.of(
                        Widget.WidgetOption.builder().label("液体").value("液体").build(),
                        Widget.WidgetOption.builder().label("气体").value("气体").build(),
                        Widget.WidgetOption.builder().label("通用").value("通用").build()
                ))
                .build();
    }

    private Widget buildStationTypeOptions() {
        return Widget.builder()
                .type("option_list")
                .title("请选择站点类型")
                .options(List.of(
                        Widget.WidgetOption.builder().label("站场").value("站场").build(),
                        Widget.WidgetOption.builder().label("阀室").value("阀室").build()
                ))
                .build();
    }

    private Widget buildOperationTypeOptions() {
        return Widget.builder()
                .type("option_list")
                .title("请选择作业类型")
                .options(List.of(
                        Widget.WidgetOption.builder().label("常规清管").value("常规清管").build(),
                        Widget.WidgetOption.builder().label("应急清管").value("应急清管").build()
                ))
                .build();
    }

    private static final Set<String> NUMBER_PARAMS = Set.of(
        "displacement", "gasFlowRate", "outletPressure", "inletPressure",
        "interferenceRate", "diameter", "designPressureMin", "designPressureMax",
        "totalLength", "mileage", "elevation"
    );

    private Widget buildScalarForm(List<String> missingParams) {
        List<Widget.WidgetField> fields = new ArrayList<>();
        for (String param : missingParams) {
            String label = paramLabel(param);
            String hint = PARAM_HINTS.getOrDefault(param, "");
            String type;
            if ("dispatchTime".equals(param) || "actualArrivalTime".equals(param)) {
                type = "datetime";
            } else if (NUMBER_PARAMS.contains(param)) {
                type = "number";
            } else {
                type = "text";
            }
            fields.add(Widget.WidgetField.builder()
                    .label(label).key(param).type(type)
                    .placeholder("请输入" + label)
                    .hint(hint).required(true).build());
        }
        return Widget.builder()
                .type("form_card")
                .title("请填写以下信息")
                .fields(fields)
                .submitLabel("确认提交")
                .build();
    }

    // ==================== CREATE_OPERATION (updated with widgets) ====================

    private AgentReply handleCreateOperation(ParsedIntent parsed, Long conversationId) {
        Pipeline pipeline = fuzzyMatchPipeline(parsed.pipelineName);
        if (pipeline == null) {
            return buildFallbackReply(conversationId, null, "未找到管线「" + parsed.pipelineName + "」，请确认管线名称。");
        }

        List<Station> stations = stationMapper.selectList(
                new LambdaQueryWrapper<Station>()
                        .eq(Station::getPipelineId, pipeline.getId())
                        .orderByAsc(Station::getSortOrder));
        Station fromStation = fuzzyMatchStation(stations, parsed.fromStationName);
        Station toStation = fuzzyMatchStation(stations, parsed.toStationName);
        if (fromStation == null) {
            return buildFallbackReply(conversationId, null, "未找到发球站「" + parsed.fromStationName + "」，可用站点："
                    + stations.stream().map(Station::getName).collect(Collectors.joining("、")));
        }
        if (toStation == null) {
            return buildFallbackReply(conversationId, null, "未找到收球站「" + parsed.toStationName + "」，可用站点："
                    + stations.stream().map(Station::getName).collect(Collectors.joining("、")));
        }

        int fromIdx = -1, toIdx = -1;
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).getId().equals(fromStation.getId())) fromIdx = i;
            if (stations.get(i).getId().equals(toStation.getId())) toIdx = i;
        }
        if (fromIdx >= toIdx) {
            return buildFallbackReply(conversationId, null, "收球站必须在发球站下游，请重新选择站点。");
        }

        // Validate medium-specific params
        if ("气体".equals(pipeline.getMediumType())) {
            if (parsed.outletPressure == null || parsed.inletPressure == null || parsed.gasFlowRate == null) {
                ParsedIntent followUp = new ParsedIntent();
                followUp.intent = "CREATE_OPERATION";
                followUp.pipelineName = parsed.pipelineName;
                followUp.fromStationName = parsed.fromStationName;
                followUp.toStationName = parsed.toStationName;
                followUp.dispatchTime = parsed.dispatchTime;
                followUp.operationType = parsed.operationType;
                followUp.pigType = parsed.pigType;
                followUp.interferenceRate = parsed.interferenceRate;
                followUp.missingParams = new ArrayList<>();
                if (parsed.outletPressure == null) followUp.missingParams.add("outletPressure");
                if (parsed.inletPressure == null) followUp.missingParams.add("inletPressure");
                if (parsed.gasFlowRate == null) followUp.missingParams.add("gasFlowRate");
                String text = buildFollowUpText(followUp);
                List<Widget> widgets = buildFollowUpWidgets(followUp, followUp.intent);
                saveAssistantMsg(conversationId, text, "CREATE_OPERATION", null, followUp);
                return AgentReply.builder()
                        .reply(text).intent("CREATE_OPERATION").needFollowUp(true)
                        .conversationId(conversationId).widgets(widgets).build();
            }
        } else {
            if (parsed.displacement == null) {
                ParsedIntent followUp = new ParsedIntent();
                followUp.intent = "CREATE_OPERATION";
                followUp.pipelineName = parsed.pipelineName;
                followUp.fromStationName = parsed.fromStationName;
                followUp.toStationName = parsed.toStationName;
                followUp.dispatchTime = parsed.dispatchTime;
                followUp.operationType = parsed.operationType;
                followUp.pigType = parsed.pigType;
                followUp.interferenceRate = parsed.interferenceRate;
                followUp.missingParams = List.of("displacement");
                String text = buildFollowUpText(followUp);
                List<Widget> widgets = buildFollowUpWidgets(followUp, followUp.intent);
                saveAssistantMsg(conversationId, text, "CREATE_OPERATION", null, followUp);
                return AgentReply.builder()
                        .reply(text).intent("CREATE_OPERATION").needFollowUp(true)
                        .conversationId(conversationId).widgets(widgets).build();
            }
        }

        Pig pig = null;
        if (parsed.pigType != null) {
            pig = fuzzyMatchPig(parsed.pigType, pipeline.getMediumType());
        }
        if (pig == null) {
            pig = pigMapper.selectList(new LambdaQueryWrapper<Pig>()
                    .eq(Pig::getStatus, "可用")
                    .and(w -> w.eq(Pig::getMediumType, pipeline.getMediumType()).or().eq(Pig::getMediumType, "通用"))
                    .last("LIMIT 1")).stream().findFirst().orElse(null);
        }
        if (pig == null) {
            String reply = "没有可用清管器（介质=" + pipeline.getMediumType() + "）。请先前往清管器管理页面添加，添加后回来对我说「继续创建作业」。";
            List<Widget> widgets = List.of(
                    Widget.builder()
                            .type("nav_card")
                            .title("添加清管器")
                            .description("点击下方按钮前往清管器管理页面")
                            .route("/pigs")
                            .routeLabel("前往清管器管理 →")
                            .build()
            );
            saveAssistantMsg(conversationId, reply, "CREATE_OPERATION", null, parsed);
            return AgentReply.builder()
                    .reply(reply).intent("CREATE_OPERATION").needFollowUp(false)
                    .conversationId(conversationId).widgets(widgets)
                    .build();
        }

        LocalDateTime dispatchTime = parseDateTime(parsed.dispatchTime);
        if (dispatchTime == null) {
            return buildFallbackReply(conversationId, null, "无法解析发出时间，请使用格式：2026-05-05 08:00 或 5月5日8:00");
        }

        Operation op = new Operation();
        op.setPipelineId(pipeline.getId());
        op.setPigId(pig.getId());
        op.setOperationType(parsed.operationType != null ? parsed.operationType : "常规清管");
        op.setFromStationId(fromStation.getId());
        op.setToStationId(toStation.getId());
        op.setDispatchTime(dispatchTime);
        op.setDisplacement(parsed.displacement);
        op.setGasFlowRate(parsed.gasFlowRate);
        op.setOutletPressure(parsed.outletPressure);
        op.setInletPressure(parsed.inletPressure);

        List<TrackingRecord> records = operationService.createWithTracking(op);

        String reply = formatCreateReply(pipeline, fromStation, toStation, pig, op, records, parsed.interferenceRate);
        List<Widget> widgets = buildCreateWidgets(op, pipeline, fromStation, toStation, pig, records);

        saveAssistantMsg(conversationId, reply, "CREATE_OPERATION", op.getId(), parsed);

        Map<String, Object> data = new HashMap<>();
        data.put("operationId", op.getId());
        data.put("trackingCount", records.size());

        return AgentReply.builder()
                .reply(reply).intent("CREATE_OPERATION").data(data)
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    private List<Widget> buildCreateWidgets(Operation op, Pipeline pipeline, Station from, Station to,
                                             Pig pig, List<TrackingRecord> records) {
        List<Widget.WidgetField> rows = new ArrayList<>();
        rows.add(field("管线", pipeline.getName()));
        rows.add(field("区间", from.getName() + " → " + to.getName()));
        rows.add(field("清管器", pig.getType() + " " + pig.getSpec()));
        rows.add(field("作业类型", op.getOperationType()));
        rows.add(field("发球时间", op.getDispatchTime().format(FMT_DATETIME)));
        if (op.getDisplacement() != null) rows.add(field("排量", op.getDisplacement() + " m³/h"));
        if (op.getGasFlowRate() != null) rows.add(field("输气量", op.getGasFlowRate() + " 10⁴Nm³/d"));
        if (!records.isEmpty()) {
            TrackingRecord first = records.get(0);
            rows.add(field("首站预计到达", first.getPredictedArrivalTime().format(FMT_DATETIME)));
            rows.add(field("当前球速", first.getPigSpeed().setScale(2, RoundingMode.HALF_UP) + " km/h"));
        }

        Widget infoCard = Widget.builder()
                .type("info_card")
                .title("作业创建成功")
                .rows(rows)
                .actions(List.of(
                        Widget.WidgetAction.builder().label("查看作业详情").action("navigate")
                                .value("/operations/" + op.getId()).style("primary").build()
                ))
                .build();

        return List.of(infoCard);
    }

    // ==================== NODE_ARRIVAL (updated with widgets) ====================

    private AgentReply handleNodeArrival(ParsedIntent parsed, Long conversationId) {
        Pipeline pipeline = fuzzyMatchPipeline(parsed.pipelineName);
        if (pipeline == null) {
            return buildFallbackReply(conversationId, null, "未找到管线「" + parsed.pipelineName + "」，请说明管线名称。");
        }

        List<Operation> runningOps = operationService.list(
                new LambdaQueryWrapper<Operation>()
                        .eq(Operation::getPipelineId, pipeline.getId())
                        .eq(Operation::getStatus, "运行中")
                        .orderByDesc(Operation::getCreateTime));
        if (runningOps.isEmpty()) {
            return buildFallbackReply(conversationId, null, "管线「" + pipeline.getName() + "」当前没有运行中的清管作业。");
        }
        Operation op = runningOps.get(0);

        List<Station> stations = stationMapper.selectList(
                new LambdaQueryWrapper<Station>()
                        .eq(Station::getPipelineId, pipeline.getId())
                        .orderByAsc(Station::getSortOrder));
        Station arrivedStation = fuzzyMatchStation(stations, parsed.arrivedStationName);
        if (arrivedStation == null) {
            return buildFallbackReply(conversationId, null, "未找到站点「" + parsed.arrivedStationName + "」。");
        }

        LocalDateTime actualTime = parseDateTime(parsed.actualArrivalTime);
        if (actualTime == null) {
            actualTime = LocalDateTime.now();
        }

        operationService.nodeArrival(op.getId(), arrivedStation.getId(), actualTime);

        List<TrackingRecordVO> tracking = trackingRecordService.getByOperation(op.getId());
        Pig pig = pigMapper.selectById(op.getPigId());
        boolean isLastStation = arrivedStation.getId().equals(op.getToStationId());

        String reply;
        if (isLastStation) {
            reply = formatArrivalFinalReply(pipeline, op, pig, arrivedStation, actualTime, tracking, parsed.interferenceRate);
        } else {
            reply = formatArrivalMidReply(pipeline, op, arrivedStation, actualTime, tracking);
        }

        List<Widget> widgets = buildArrivalWidgets(op, pipeline, tracking);

        saveAssistantMsg(conversationId, reply, "NODE_ARRIVAL", op.getId(), parsed);

        Map<String, Object> data = new HashMap<>();
        data.put("operationId", op.getId());

        return AgentReply.builder()
                .reply(reply).intent("NODE_ARRIVAL").data(data)
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    private List<Widget> buildArrivalWidgets(Operation op, Pipeline pipeline, List<TrackingRecordVO> tracking) {
        List<Widget.WidgetField> rows = new ArrayList<>();
        rows.add(field("管线", pipeline.getName()));
        rows.add(field("状态", op.getStatus()));

        for (TrackingRecordVO t : tracking) {
            String key = (t.getIsKeyStation() ? "⭐ " : "") + t.getStationName();
            String val;
            if (t.getActualArrivalTime() != null) {
                val = "✓ 已到达 " + t.getActualArrivalTime().format(FMT_DATETIME);
            } else {
                val = "预计 " + t.getPredictedArrivalTime().format(FMT_DATETIME);
            }
            rows.add(field(key, val));
        }

        // Find next key station for action button
        Widget.WidgetAction arrivalAction = null;
        if ("运行中".equals(op.getStatus())) {
            for (TrackingRecordVO t : tracking) {
                if (t.getActualArrivalTime() == null && t.getIsKeyStation()) {
                    arrivalAction = Widget.WidgetAction.builder()
                            .label("标记到达 " + t.getStationName())
                            .action("arrival")
                            .value(t.getStationName() + "|" + pipeline.getName())
                            .style("warning")
                            .build();
                    break;
                }
            }
        }

        List<Widget.WidgetAction> actions = new ArrayList<>();
        actions.add(Widget.WidgetAction.builder().label("查看详情").action("navigate")
                .value("/operations/" + op.getId()).style("primary").build());
        if (arrivalAction != null) {
            actions.add(arrivalAction);
        }

        Widget infoCard = Widget.builder()
                .type("info_card")
                .title("跟踪进度")
                .rows(rows)
                .actions(actions)
                .build();

        return List.of(infoCard);
    }

    // ==================== QUERY_STATUS (updated with widgets) ====================

    private AgentReply handleQueryStatus(ParsedIntent parsed, Long conversationId) {
        Pipeline pipeline = fuzzyMatchPipeline(parsed.pipelineName);
        if (pipeline == null) {
            return buildFallbackReply(conversationId, null, "未找到管线，请说明管线名称。");
        }

        List<Operation> ops = operationService.list(
                new LambdaQueryWrapper<Operation>()
                        .eq(Operation::getPipelineId, pipeline.getId())
                        .orderByDesc(Operation::getCreateTime));
        if (ops.isEmpty()) {
            String reply = "管线「" + pipeline.getName() + "」暂无清管作业记录。";
            saveAssistantMsg(conversationId, reply, "QUERY_STATUS", null, parsed);
            return AgentReply.builder().reply(reply).intent("QUERY_STATUS")
                    .needFollowUp(false).conversationId(conversationId).build();
        }

        Operation op = ops.get(0);
        List<TrackingRecordVO> tracking = trackingRecordService.getByOperation(op.getId());
        String reply = formatStatusReply(pipeline, op, tracking);
        List<Widget> widgets = buildStatusWidgets(pipeline, op, tracking);

        saveAssistantMsg(conversationId, reply, "QUERY_STATUS", op.getId(), parsed);

        return AgentReply.builder()
                .reply(reply).intent("QUERY_STATUS")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    private List<Widget> buildStatusWidgets(Pipeline pipeline, Operation op, List<TrackingRecordVO> tracking) {
        List<Widget.WidgetField> rows = new ArrayList<>();
        rows.add(field("管线", pipeline.getName()));
        rows.add(field("状态", op.getStatus()));
        rows.add(field("发球时间", op.getDispatchTime().format(FMT_DATETIME)));

        for (TrackingRecordVO t : tracking) {
            String key = (t.getIsKeyStation() ? "⭐ " : "") + t.getStationName();
            String val;
            if (t.getActualArrivalTime() != null) {
                val = "✓ 已到达 " + t.getActualArrivalTime().format(FMT_DATETIME);
            } else {
                val = "预计 " + t.getPredictedArrivalTime().format(FMT_DATETIME)
                        + (t.getPigSpeed() != null ? "，" + t.getPigSpeed().setScale(2, RoundingMode.HALF_UP) + " km/h" : "");
            }
            rows.add(field(key, val));
        }

        List<Widget.WidgetAction> actions = new ArrayList<>();
        actions.add(Widget.WidgetAction.builder().label("查看详情").action("navigate")
                .value("/operations/" + op.getId()).style("primary").build());

        // Next key station arrival button
        if ("运行中".equals(op.getStatus())) {
            for (TrackingRecordVO t : tracking) {
                if (t.getActualArrivalTime() == null && t.getIsKeyStation()) {
                    actions.add(Widget.WidgetAction.builder()
                            .label("标记到达 " + t.getStationName())
                            .action("arrival")
                            .value(t.getStationName() + "|" + pipeline.getName())
                            .style("warning")
                            .build());
                    break;
                }
            }
        }

        return List.of(Widget.builder()
                .type("info_card")
                .title("作业进度")
                .rows(rows)
                .actions(actions)
                .build());
    }

    // ==================== QUERY_OPTIONS (new) ====================

    private AgentReply handleQueryOptions(ParsedIntent parsed, Long conversationId) {
        String target = parsed.queryTarget;
        if (target == null) target = "pipeline";

        String reply;
        List<Widget> widgets = new ArrayList<>();

        if ("pipeline".equals(target)) {
            List<Pipeline> pipelines = pipelineMapper.selectList(null);
            if (pipelines.isEmpty()) {
                reply = "暂无管线数据。";
            } else {
                reply = "当前共有 " + pipelines.size() + " 条管线：";
                widgets.add(buildPipelineOptionList());
                // Also add info_cards for each pipeline
                for (Pipeline p : pipelines) {
                    List<Station> sts = stationMapper.selectList(
                            new LambdaQueryWrapper<Station>()
                                    .eq(Station::getPipelineId, p.getId())
                                    .orderByAsc(Station::getSortOrder));
                    String stationList = sts.stream().map(Station::getName).collect(Collectors.joining(" → "));
                    widgets.add(Widget.builder()
                            .type("info_card")
                            .title(p.getName())
                            .rows(List.of(
                                    field("介质", p.getMediumType()),
                                    field("管径", p.getDiameter() + "mm"),
                                    field("设计压力", p.getDesignPressureMin() + " ~ " + p.getDesignPressureMax() + " MPa"),
                                    field("总长", p.getTotalLength() + " km"),
                                    field("站点", stationList)
                            ))
                            .build());
                }
            }
        } else if ("station".equals(target)) {
            Pipeline p = parsed.pipelineName != null ? fuzzyMatchPipeline(parsed.pipelineName) : null;
            if (p == null) {
                reply = "请说明要查询哪条管线的站点。";
                widgets.add(buildPipelineOptionList());
            } else {
                widgets.add(buildStationOptionList(p, p.getName() + " 沿线站点"));
                reply = p.getName() + " 沿线站点如下：";
            }
        } else if ("pig".equals(target)) {
            List<Pig> pigs = pigMapper.selectList(null);
            if (pigs.isEmpty()) {
                reply = "暂无清管器数据。";
            } else {
                reply = "当前共有 " + pigs.size() + " 个清管器：";
                widgets.add(buildPigOptionList());
                List<Widget.WidgetField> pigRows = new ArrayList<>();
                for (Pig pg : pigs) {
                    pigRows.add(field(pg.getType() + " " + pg.getSpec(),
                            "介质：" + pg.getMediumType() + "，过盈量：" + pg.getInterferenceRate() + "%，状态：" + pg.getStatus()));
                }
                widgets.add(Widget.builder().type("info_card").title("清管器清单").rows(pigRows).build());
            }
        } else if ("help".equals(target)) {
            reply = "我可以帮您完成以下操作：";
            widgets.add(Widget.builder()
                    .type("info_card")
                    .title("我能做什么")
                    .rows(List.of(
                            field("🚀 创建作业", "对我说「发球」或「创建作业」，我会逐步引导您选择管线、站点、清管器"),
                            field("📍 节点反馈", "对我说「XX站到达」，我会记录时间并自动修正下游预测"),
                            field("📈 查询进度", "对我说「XX管线到哪了」，我会展示当前跟踪状态"),
                            field("🔍 查询数据", "对我说「有哪些管线/清管器/站点」，我会列出清单"),
                            field("⚠️ 查看预警", "对我说「最近有什么预警」，我会展示预警列表"),
                            field("➕ 添加记录", "对我说「添加清管器/新建管线/加个站点」，我会引导填写"),
                            field("✏️ 精确修改", "需要修改已有数据的某个值时，我会引导您前往专用页面操作")
                    ))
                    .build());
            widgets.add(Widget.builder()
                    .type("option_list")
                    .title("快速开始")
                    .options(List.of(
                            Widget.WidgetOption.builder().label("🚀 创建作业").value("发一个清管器").build(),
                            Widget.WidgetOption.builder().label("🔍 查看管线").value("有哪些管线").build(),
                            Widget.WidgetOption.builder().label("⚠️ 查看预警").value("最近有什么预警").build(),
                            Widget.WidgetOption.builder().label("➕ 添加清管器").value("添加一个清管器").build()
                    ))
                    .build());
        } else {
            reply = "可以查询以下内容：管线列表、站点列表、清管器列表。请说明您想查什么。";
        }

        saveAssistantMsg(conversationId, reply, "QUERY_OPTIONS", null, parsed);
        return AgentReply.builder()
                .reply(reply).intent("QUERY_OPTIONS")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    // ==================== QUERY_WARNING (new) ====================

    private AgentReply handleQueryWarning(ParsedIntent parsed, Long conversationId) {
        List<Warning> warnings;
        if (parsed.pipelineName != null) {
            Pipeline pipeline = fuzzyMatchPipeline(parsed.pipelineName);
            if (pipeline == null) {
                return buildFallbackReply(conversationId, null, "未找到管线「" + parsed.pipelineName + "」。");
            }
            // Find operations for this pipeline, then warnings for those operations
            List<Operation> ops = operationService.list(
                    new LambdaQueryWrapper<Operation>().eq(Operation::getPipelineId, pipeline.getId()));
            List<Long> opIds = ops.stream().map(Operation::getId).toList();
            if (opIds.isEmpty()) {
                warnings = List.of();
            } else {
                warnings = warningMapper.selectList(
                        new LambdaQueryWrapper<Warning>().in(Warning::getOperationId, opIds)
                                .orderByDesc(Warning::getCreateTime));
            }
        } else {
            warnings = warningMapper.selectList(
                    new LambdaQueryWrapper<Warning>()
                            .eq(Warning::getStatus, "未处理")
                            .orderByDesc(Warning::getCreateTime));
        }

        if (warnings.isEmpty()) {
            String reply = parsed.pipelineName != null
                    ? "管线「" + parsed.pipelineName + "」暂无预警。"
                    : "当前没有未处理的预警。";
            saveAssistantMsg(conversationId, reply, "QUERY_WARNING", null, parsed);
            return AgentReply.builder().reply(reply).intent("QUERY_WARNING")
                    .needFollowUp(false).conversationId(conversationId).build();
        }

        String reply = "共 " + warnings.size() + " 条预警：";
        List<Widget> widgets = new ArrayList<>();
        for (Warning w : warnings) {
            Operation op = operationService.getById(w.getOperationId());
            Pipeline pipe = op != null ? pipelineMapper.selectById(op.getPipelineId()) : null;
            String prefix = pipe != null ? "【" + pipe.getName() + "】" : "";

            String levelEmoji = switch (w.getLevel()) {
                case "高" -> "🔴";
                case "中" -> "🟡";
                default -> "🟢";
            };

            widgets.add(Widget.builder()
                    .type("info_card")
                    .title(levelEmoji + " " + w.getWarningType() + " — " + w.getLevel() + "风险")
                    .rows(List.of(
                            field("内容", w.getContent()),
                            field("建议", w.getSuggestion() != null ? w.getSuggestion() : "—"),
                            field("状态", w.getStatus())
                    ))
                    .actions(List.of(
                            Widget.WidgetAction.builder().label("确认").action("ack_warning")
                                    .value(String.valueOf(w.getId())).style("primary").build(),
                            Widget.WidgetAction.builder().label("查看作业").action("navigate")
                                    .value("/operations/" + w.getOperationId()).style("default").build()
                    ))
                    .build());
        }

        saveAssistantMsg(conversationId, reply, "QUERY_WARNING", null, parsed);
        return AgentReply.builder()
                .reply(reply).intent("QUERY_WARNING")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    // ==================== ADD_PIPELINE (new) ====================

    private AgentReply handleAddPipeline(ParsedIntent parsed, Long conversationId) {
        Pipeline p = new Pipeline();
        p.setName(parsed.pipelineName);
        p.setMediumType(parsed.mediumType);
        if (parsed.diameter != null) p.setDiameter(parsed.diameter);
        if (parsed.designPressureMin != null) p.setDesignPressureMin(parsed.designPressureMin);
        if (parsed.designPressureMax != null) p.setDesignPressureMax(parsed.designPressureMax);
        if (parsed.totalLength != null) p.setTotalLength(parsed.totalLength);

        pipelineMapper.insert(p);

        String reply = "管线「" + p.getName() + "」已添加。";
        List<Widget> widgets = List.of(Widget.builder()
                .type("info_card")
                .title("管线已添加")
                .rows(List.of(
                        field("管线名称", p.getName()),
                        field("介质类型", p.getMediumType()),
                        field("管径", p.getDiameter() != null ? p.getDiameter() + " mm" : "未填写"),
                        field("设计压力", p.getDesignPressureMin() != null ? p.getDesignPressureMin() + " ~ " + p.getDesignPressureMax() + " MPa" : "未填写"),
                        field("总长度", p.getTotalLength() != null ? p.getTotalLength() + " km" : "未填写")
                ))
                .actions(List.of(
                        Widget.WidgetAction.builder().label("前往管线管理").action("navigate")
                                .value("/pipelines").style("primary").build()
                ))
                .build());

        saveAssistantMsg(conversationId, reply, "ADD_PIPELINE", null, parsed);
        return AgentReply.builder()
                .reply(reply).intent("ADD_PIPELINE")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    // ==================== ADD_PIG (new) ====================

    private AgentReply handleAddPig(ParsedIntent parsed, Long conversationId) {
        Pig pig = new Pig();
        pig.setType(parsed.pigType != null ? parsed.pigType : "未分类");
        pig.setSpec(parsed.pigSpec != null ? parsed.pigSpec : "");
        pig.setMediumType(parsed.mediumType != null ? parsed.mediumType : "通用");
        pig.setInterferenceRate(parsed.interferenceRate != null ? parsed.interferenceRate : BigDecimal.ZERO);
        pig.setStatus("可用");

        pigMapper.insert(pig);

        String reply = "清管器「" + pig.getType() + " " + pig.getSpec() + "」已添加。";
        List<Widget> widgets = List.of(Widget.builder()
                .type("info_card")
                .title("清管器已添加")
                .rows(List.of(
                        field("类型", pig.getType()),
                        field("规格", pig.getSpec()),
                        field("介质", pig.getMediumType()),
                        field("过盈量", pig.getInterferenceRate() + "%"),
                        field("状态", pig.getStatus())
                ))
                .actions(List.of(
                        Widget.WidgetAction.builder().label("前往清管器管理").action("navigate")
                                .value("/pigs").style("primary").build()
                ))
                .build());

        saveAssistantMsg(conversationId, reply, "ADD_PIG", null, parsed);
        return AgentReply.builder()
                .reply(reply).intent("ADD_PIG")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    // ==================== ADD_STATION (jump to dedicated page) ====================

    private AgentReply handleAddStation(ParsedIntent parsed, Long conversationId) {
        String reply = "站点信息较为复杂，为保证数据准确，请前往站点管理页面进行添加。";
        Pipeline pipeline = parsed.pipelineName != null ? fuzzyMatchPipeline(parsed.pipelineName) : null;
        String route = pipeline != null
                ? "/stations?pipelineId=" + pipeline.getId()
                : "/stations";
        List<Widget> widgets = List.of(Widget.builder()
                .type("nav_card")
                .title("添加站点")
                .description(pipeline != null
                        ? "前往站点管理页面为「" + pipeline.getName() + "」添加站点"
                        : "前往站点管理页面添加站点")
                .route(route)
                .routeLabel("前往站点管理 →")
                .build());
        saveAssistantMsg(conversationId, reply, "ADD_STATION", null, parsed);
        return AgentReply.builder()
                .reply(reply).intent("ADD_STATION")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    // ==================== SMS Template Formatters ====================

    private String formatCreateReply(Pipeline pipeline, Station from, Station to, Pig pig,
                                     Operation op, List<TrackingRecord> records, BigDecimal interferenceRate) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(pipeline.getName()).append("】")
                .append("【").append(from.getName()).append("-").append(to.getName()).append("】")
                .append("【").append(op.getOperationType()).append("】\n");

        String timeStr = op.getDispatchTime().format(FMT_SHORT);
        sb.append(timeStr).append("清管器从").append(from.getName()).append("站发出，\n");
        sb.append("检测器类型：").append(pig.getType()).append(" ").append(pig.getSpec());
        if (interferenceRate != null) {
            sb.append("，过盈量").append(interferenceRate).append("%");
        }
        sb.append("\n");

        if (!records.isEmpty()) {
            TrackingRecord first = records.get(0);
            sb.append("当前球速：").append(first.getPigSpeed().setScale(2, RoundingMode.HALF_UP)).append("km/h，\n");
            sb.append("预计到达").append(getStationName(first.getStationId())).append("时间为")
                    .append(first.getPredictedArrivalTime().format(FMT_SHORT)).append("，\n");
        }
        if (records.size() >= 2) {
            TrackingRecord last = records.get(records.size() - 1);
            sb.append("到达").append(to.getName()).append("站时间为")
                    .append(last.getPredictedArrivalTime().format(FMT_SHORT)).append("。\n");
        }

        sb.append("该管段长度").append(pipeline.getTotalLength()).append("km");
        if (pipeline.getDiameter() != null) {
            sb.append("，管径").append(pipeline.getDiameter()).append("mm");
        }
        if (pipeline.getDesignPressureMin() != null && pipeline.getDesignPressureMax() != null) {
            sb.append("，设计压力").append(pipeline.getDesignPressureMin())
                    .append("MPa-").append(pipeline.getDesignPressureMax()).append("MPa");
        }
        sb.append("。");
        return sb.toString();
    }

    private String formatArrivalMidReply(Pipeline pipeline, Operation op, Station arrived,
                                         LocalDateTime actualTime, List<TrackingRecordVO> tracking) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(pipeline.getName()).append("】");

        Station fromSt = stationMapper.selectById(op.getFromStationId());
        Station toSt = stationMapper.selectById(op.getToStationId());
        sb.append("【").append(fromSt != null ? fromSt.getName() : "?").append("-")
                .append(toSt != null ? toSt.getName() : "?").append("】")
                .append("【").append(op.getOperationType()).append("】\n");

        sb.append(actualTime.format(FMT_SHORT)).append("，清管器过").append(arrived.getName()).append("。\n");

        TrackingRecordVO currentRecord = tracking.stream()
                .filter(t -> t.getStationId().equals(arrived.getId())).findFirst().orElse(null);
        if (currentRecord != null && currentRecord.getPigSpeed() != null) {
            sb.append("当前球速：").append(currentRecord.getPigSpeed().setScale(2, RoundingMode.HALF_UP)).append("km/h，\n");
        }

        TrackingRecordVO nextUnarrived = tracking.stream()
                .filter(t -> t.getActualArrivalTime() == null)
                .findFirst().orElse(null);
        if (nextUnarrived != null) {
            sb.append("预计").append(nextUnarrived.getPredictedArrivalTime().format(FMT_SHORT))
                    .append("到达").append(nextUnarrived.getStationName()).append("，\n");
        }

        TrackingRecordVO lastRecord = tracking.get(tracking.size() - 1);
        sb.append(lastRecord.getPredictedArrivalTime().format(FMT_SHORT))
                .append("到达").append(toSt != null ? toSt.getName() : "终点站").append("。\n");

        BigDecimal totalDist = op.getToStationId() != null ? calculateTotalDistance(op) : BigDecimal.ZERO;
        BigDecimal remainingDist = calculateRemainingDistance(op, arrived);
        sb.append("该管段长度").append(totalDist).append("km");
        if (remainingDist.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("，剩余管段长度").append(remainingDist).append("km");
        }
        Pipeline pipe = pipelineMapper.selectById(op.getPipelineId());
        if (pipe != null && pipe.getDiameter() != null) {
            sb.append("，管径").append(pipe.getDiameter()).append("mm");
        }
        if (pipe != null && pipe.getDesignPressureMin() != null) {
            sb.append("，设计压力").append(pipe.getDesignPressureMin())
                    .append("MPa-").append(pipe.getDesignPressureMax()).append("MPa");
        }
        sb.append("。");
        return sb.toString();
    }

    private String formatArrivalFinalReply(Pipeline pipeline, Operation op, Pig pig, Station arrived,
                                           LocalDateTime actualTime, List<TrackingRecordVO> tracking,
                                           BigDecimal interferenceRate) {
        StringBuilder sb = new StringBuilder();
        Station fromSt = stationMapper.selectById(op.getFromStationId());
        sb.append("【").append(pipeline.getName()).append("】")
                .append("【").append(fromSt != null ? fromSt.getName() : "?").append("-")
                .append(arrived.getName()).append("】")
                .append("【").append(op.getOperationType()).append("】\n");

        sb.append(actualTime.format(FMT_SHORT)).append("，")
                .append(pig.getType()).append(" ").append(pig.getSpec())
                .append("进入").append(arrived.getName()).append("收球筒，\n");
        if (interferenceRate != null || pig.getInterferenceRate() != null) {
            BigDecimal rate = interferenceRate != null ? interferenceRate : pig.getInterferenceRate();
            sb.append("过盈量").append(rate).append("%").append("，");
        }
        BigDecimal totalDist = calculateTotalDistance(op);
        sb.append(fromSt != null ? fromSt.getName() : "起点").append("至")
                .append(arrived.getName()).append("管段长度").append(totalDist).append("km，\n");

        Duration totalTime = Duration.between(op.getDispatchTime(), actualTime);
        long hours = totalTime.toHours();
        long minutes = totalTime.toMinutes() % 60;
        sb.append("清管总耗时").append(hours).append("小时").append(minutes).append("分，");

        if (!totalTime.isZero()) {
            BigDecimal avgSpeed = totalDist.divide(BigDecimal.valueOf(totalTime.toMinutes()), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(60)).setScale(2, RoundingMode.HALF_UP);
            sb.append("平均球速").append(avgSpeed).append("km/h，\n");
        }
        sb.append("计划今日取球。");
        return sb.toString();
    }

    private String formatStatusReply(Pipeline pipeline, Operation op, List<TrackingRecordVO> tracking) {
        StringBuilder sb = new StringBuilder();
        Station fromSt = stationMapper.selectById(op.getFromStationId());
        Station toSt = stationMapper.selectById(op.getToStationId());
        sb.append("【").append(pipeline.getName()).append("】")
                .append("【").append(fromSt != null ? fromSt.getName() : "?").append("-")
                .append(toSt != null ? toSt.getName() : "?").append("】")
                .append("【").append(op.getOperationType()).append("】\n");
        sb.append("当前状态：").append(op.getStatus()).append("\n");
        sb.append("发球时间：").append(op.getDispatchTime().format(FMT_SHORT)).append("\n");

        List<TrackingRecordVO> arrived = tracking.stream()
                .filter(t -> t.getActualArrivalTime() != null).toList();
        if (!arrived.isEmpty()) {
            sb.append("已到达站点：");
            sb.append(arrived.stream()
                    .map(t -> t.getStationName() + "(" + t.getActualArrivalTime().format(FMT_SHORT) + ")")
                    .collect(Collectors.joining(" → ")));
            sb.append("\n");
        }

        TrackingRecordVO next = tracking.stream()
                .filter(t -> t.getActualArrivalTime() == null).findFirst().orElse(null);
        if (next != null) {
            sb.append("下一站：").append(next.getStationName()).append("，预计")
                    .append(next.getPredictedArrivalTime().format(FMT_SHORT)).append("到达\n");
            if (next.getPigSpeed() != null) {
                sb.append("当前球速：").append(next.getPigSpeed().setScale(2, RoundingMode.HALF_UP)).append("km/h\n");
            }
        }

        if (toSt != null) {
            BigDecimal remain = calculateRemainingDistanceToEnd(op);
            BigDecimal total = calculateTotalDistance(op);
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("剩余距离：").append(remain).append("km / 总长").append(total).append("km");
            }
        }
        return sb.toString();
    }

    // ==================== Fuzzy Matching ====================

    private Pipeline fuzzyMatchPipeline(String name) {
        if (name == null) return null;
        List<Pipeline> all = pipelineMapper.selectList(null);
        for (Pipeline p : all) {
            if (p.getName().equals(name)) return p;
        }
        for (Pipeline p : all) {
            if (p.getName().contains(name) || name.contains(p.getName())) return p;
        }
        return null;
    }

    private Station fuzzyMatchStation(List<Station> stations, String name) {
        if (name == null) return null;
        for (Station s : stations) {
            if (s.getName().equals(name)) return s;
        }
        for (Station s : stations) {
            if (s.getName().contains(name) || (name.contains(s.getName()) && s.getName().length() >= 2)) return s;
        }
        return null;
    }

    private Pig fuzzyMatchPig(String type, String mediumType) {
        if (type == null) return null;
        List<Pig> candidates = pigMapper.selectList(
                new LambdaQueryWrapper<Pig>()
                        .eq(Pig::getStatus, "可用")
                        .and(w -> w.eq(Pig::getMediumType, mediumType).or().eq(Pig::getMediumType, "通用")));
        for (Pig p : candidates) {
            if (p.getType().equals(type)) return p;
        }
        for (Pig p : candidates) {
            if (p.getType().contains(type) || type.contains(p.getType())) return p;
        }
        return null;
    }

    // ==================== Helpers ====================

    private Widget.WidgetField field(String label, String value) {
        return Widget.WidgetField.builder().label(label).value(value).type("readonly").build();
    }

    private String getStationName(Long stationId) {
        Station s = stationMapper.selectById(stationId);
        return s != null ? s.getName() : "#" + stationId;
    }

    private BigDecimal calculateTotalDistance(Operation op) {
        Station from = stationMapper.selectById(op.getFromStationId());
        Station to = stationMapper.selectById(op.getToStationId());
        if (from == null || to == null) return BigDecimal.ZERO;
        return to.getMileage().subtract(from.getMileage()).abs();
    }

    private BigDecimal calculateRemainingDistance(Operation op, Station arrived) {
        Station to = stationMapper.selectById(op.getToStationId());
        if (to == null || arrived == null) return BigDecimal.ZERO;
        return to.getMileage().subtract(arrived.getMileage()).abs();
    }

    private BigDecimal calculateRemainingDistanceToEnd(Operation op) {
        List<TrackingRecordVO> tracking = trackingRecordService.getByOperation(op.getId());
        TrackingRecordVO lastArrived = null;
        for (int i = tracking.size() - 1; i >= 0; i--) {
            if (tracking.get(i).getActualArrivalTime() != null) {
                lastArrived = tracking.get(i);
                break;
            }
        }
        if (lastArrived == null) return calculateTotalDistance(op);
        Station to = stationMapper.selectById(op.getToStationId());
        Station current = stationMapper.selectById(lastArrived.getStationId());
        if (to == null || current == null) return BigDecimal.ZERO;
        return to.getMileage().subtract(current.getMileage()).abs();
    }

    private LocalDateTime parseDateTime(String timeStr) {
        if (timeStr == null) return null;
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
                DateTimeFormatter.ofPattern("M月d日HH:mm"),
                DateTimeFormatter.ofPattern("yyyy年M月d日HH:mm"),
                DateTimeFormatter.ofPattern("M月d日 H:mm"),
                DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
                DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm")
        );
        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDateTime.parse(timeStr, f);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private ParsedIntent parseDeepSeekResponse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            ParsedIntent p = new ParsedIntent();
            p.intent = root.path("intent").asText("UNKNOWN");
            JsonNode params = root.path("params");
            p.pipelineName = nullIfEmpty(params.path("pipelineName").asText());
            p.fromStationName = nullIfEmpty(params.path("fromStationName").asText());
            p.toStationName = nullIfEmpty(params.path("toStationName").asText());
            p.displacement = nullIfNull(params.path("displacement"));
            p.outletPressure = nullIfNull(params.path("outletPressure"));
            p.inletPressure = nullIfNull(params.path("inletPressure"));
            p.gasFlowRate = nullIfNull(params.path("gasFlowRate"));
            p.dispatchTime = nullIfEmpty(params.path("dispatchTime").asText());
            p.operationType = nullIfEmpty(params.path("operationType").asText());
            p.pigType = nullIfEmpty(params.path("pigType").asText());
            p.interferenceRate = nullIfNull(params.path("interferenceRate"));
            p.arrivedStationName = nullIfEmpty(params.path("arrivedStationName").asText());
            p.actualArrivalTime = nullIfEmpty(params.path("actualArrivalTime").asText());
            // New fields
            p.queryTarget = nullIfEmpty(params.path("queryTarget").asText());
            p.pigSpec = nullIfEmpty(params.path("pigSpec").asText());
            p.mediumType = nullIfEmpty(params.path("mediumType").asText());
            p.diameter = nullIfNull(params.path("diameter"));
            p.designPressureMin = nullIfNull(params.path("designPressureMin"));
            p.designPressureMax = nullIfNull(params.path("designPressureMax"));
            p.totalLength = nullIfNull(params.path("totalLength"));
            p.stationType = nullIfEmpty(params.path("stationType").asText());
            p.mileage = nullIfNull(params.path("mileage"));
            p.elevation = nullIfNull(params.path("elevation"));

            JsonNode missing = root.path("missingParams");
            if (missing.isArray()) {
                p.missingParams = new ArrayList<>();
                for (JsonNode m : missing) {
                    p.missingParams.add(m.asText());
                }
            }
            p.confidence = root.path("confidence").asDouble(0.5);
            return p;
        } catch (Exception e) {
            log.error("Failed to parse DeepSeek response: {}", json, e);
            return null;
        }
    }

    private BigDecimal nullIfNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return BigDecimal.valueOf(node.asDouble());
    }

    private String nullIfEmpty(String s) {
        if (s == null || s.isBlank() || "null".equals(s)) return null;
        return s.trim();
    }

    private AgentReply buildFallbackReply(Long conversationId, String userInput, String message) {
        saveAssistantMsg(conversationId, message, "UNKNOWN", null, null);

        // Match keywords to suggest relevant pages
        List<Widget> widgets = new ArrayList<>();
        String input = userInput != null ? userInput : "";
        if (input.contains("站点") || input.contains("阀室") || input.contains("站")) {
            widgets.add(Widget.builder().type("nav_card").title("站点管理")
                    .description("前往站点管理页面手动添加或编辑站点")
                    .route("/stations").routeLabel("前往站点管理 →").build());
        }
        if (input.contains("管线") || input.contains("管道")) {
            widgets.add(Widget.builder().type("nav_card").title("管线管理")
                    .description("前往管线管理页面手动添加或编辑管线")
                    .route("/pipelines").routeLabel("前往管线管理 →").build());
        }
        if (input.contains("清管器") || input.contains("清管球")) {
            widgets.add(Widget.builder().type("nav_card").title("清管器管理")
                    .description("前往清管器管理页面手动添加或编辑清管器")
                    .route("/pigs").routeLabel("前往清管器管理 →").build());
        }
        if (input.contains("预警") || input.contains("报警")) {
            widgets.add(Widget.builder().type("nav_card").title("预警管理")
                    .description("前往预警管理页面查看和处理预警")
                    .route("/warnings").routeLabel("前往预警管理 →").build());
        }
        if (input.contains("作业") || input.contains("发球") || input.contains("清管")) {
            widgets.add(Widget.builder().type("nav_card").title("清管作业")
                    .description("前往作业管理页面手动创建或查看作业")
                    .route("/operations").routeLabel("前往作业管理 →").build());
        }
        if (widgets.isEmpty()) {
            // Generic fallback: suggest main pages
            widgets.add(Widget.builder().type("nav_card").title("常用入口")
                    .description("智能助手、作业管理、预警中心")
                    .route("/dashboard").routeLabel("返回首页 →").build());
        }

        return AgentReply.builder()
                .reply(message).intent("UNKNOWN")
                .needFollowUp(false).conversationId(conversationId)
                .widgets(widgets)
                .build();
    }

    private void saveAssistantMsg(Long conversationId, String content, String intent, Long operationId, ParsedIntent parsed) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setIntent(intent);
        msg.setOperationId(operationId);
        if (parsed != null) {
            try {
                Map<String, Object> meta = new HashMap<>();
                meta.put("confidence", parsed.confidence);
                if (parsed.missingParams != null) meta.put("missingParams", parsed.missingParams);
                msg.setMetadataJson(objectMapper.writeValueAsString(meta));
            } catch (Exception ignored) { }
        }
        messageMapper.insert(msg);
    }

    // ==================== Parsed Intent DTO ====================

    private static class ParsedIntent {
        String intent;
        String pipelineName, fromStationName, toStationName;
        BigDecimal displacement, outletPressure, inletPressure, gasFlowRate, interferenceRate;
        String dispatchTime, operationType, pigType, arrivedStationName, actualArrivalTime;
        // New fields for 2.0
        String queryTarget, pigSpec, mediumType, stationType;
        BigDecimal diameter, designPressureMin, designPressureMax, totalLength, mileage, elevation;
        List<String> missingParams;
        double confidence;
    }
}
