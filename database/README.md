# 清管器作业管理系统 — 数据库说明文档

## 数据库信息

- **数据库名称**：`qingguanqi`
- **字符集**：utf8mb4
- **存储引擎**：InnoDB

## 表关系图

```
pipeline (管线)
    │
    ├── station (站点) ──────────────────────────────────┐
    │       │                                             │
    │       └── pipeline_segment (管段) ── from_station   │
    │                      └────────────── to_station     │
    │                                                     │
    ├── operation (作业) ───── tracking_record (跟踪) ─── station
    │       │
    │       └── warning (预警)
    │
pig (清管器) ─── operation (作业)
```

## 表详细说明

### 1. pig — 清管器基础信息

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| type | VARCHAR(50) | 是 | 四皮碗清管器 / 泡沫清管器 / 测径清管器 等 |
| spec | VARCHAR(100) | 是 | 规格型号 |
| interference_rate | DECIMAL(5,2) | 是 | 过盈量（%） |
| applicable_scene | VARCHAR(200) | 否 | 适用场景描述 |
| medium_type | VARCHAR(10) | 是 | 液体 / 气体 / 通用 |
| status | VARCHAR(20) | 是 | 可用 / 使用中 / 报废，默认"可用" |
| remark | VARCHAR(500) | 否 | 备注 |

### 2. pipeline — 管线基础信息

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| name | VARCHAR(100) | 是 | 唯一，如"石兰线""兰成线" |
| medium_type | VARCHAR(10) | 是 | 液体 / 气体 |
| diameter | DECIMAL(10,2) | 否 | 管径（mm） |
| design_pressure_min | DECIMAL(8,2) | 否 | 设计压力下限（MPa） |
| design_pressure_max | DECIMAL(8,2) | 否 | 设计压力上限（MPa） |
| total_length | DECIMAL(10,4) | 否 | 总长（km） |

### 3. station — 站点/阀室

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| pipeline_id | BIGINT | 是 | 所属管线，关联 pipeline.id |
| name | VARCHAR(100) | 是 | 站点名称 |
| station_type | VARCHAR(20) | 是 | 站场 / 阀室 |
| mileage | DECIMAL(10,4) | 是 | 累计里程（km），从管线起点起算 |
| elevation | DECIMAL(8,2) | 否 | 高程（m） |
| sort_order | INT | 是 | 沿管线方向排序号，从 0 开始 |

### 4. pipeline_segment — 管段参数

相邻两站之间的管段信息。液体管道填 `unit_capacity`，气体管道填 `inner_diameter`。

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| pipeline_id | BIGINT | 是 | 所属管线 |
| from_station_id | BIGINT | 是 | 起始站 |
| to_station_id | BIGINT | 是 | 到达站 |
| distance | DECIMAL(10,4) | 是 | 站间距（km） |
| unit_capacity | DECIMAL(10,4) | 否 | 单位管容（m³/km），**液体管道专用** |
| inner_diameter | DECIMAL(10,2) | 否 | 管道内径（mm），**气体管道专用** |

### 5. operation — 清管作业

液体管道填 `displacement`，气体管道填 `gas_flow_rate + outlet_pressure + inlet_pressure`，互斥使用。

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| pipeline_id | BIGINT | 是 | 管线 |
| pig_id | BIGINT | 是 | 清管器 |
| operation_type | VARCHAR(50) | 是 | 常规清管 / 应急清管 |
| from_station_id | BIGINT | 是 | 发球站 |
| to_station_id | BIGINT | 是 | 收球站（最终目标） |
| dispatch_time | DATETIME | 是 | 发球时间 |
| displacement | DECIMAL(10,2) | 否 | 排量（m³/h），液体 |
| gas_flow_rate | DECIMAL(12,2) | 否 | 输气量（Nm³/d），气体 |
| outlet_pressure | DECIMAL(8,2) | 否 | 出站压力（MPa），气体 |
| inlet_pressure | DECIMAL(8,2) | 否 | 进站压力（MPa），气体 |
| status | VARCHAR(20) | 是 | 准备 → 运行中 → 已完成 / 异常 |

### 6. tracking_record — 跟踪记录

每次作业对应多条记录（每个途经站点/阀室一条）。支持滚动修正链。

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| operation_id | BIGINT | 是 | 作业ID |
| station_id | BIGINT | 是 | 站点ID |
| segment_distance | DECIMAL(10,4) | 是 | 距上一站的管段长度（km） |
| predicted_arrival_time | DATETIME | 是 | 预计到达时间 |
| actual_arrival_time | DATETIME | 否 | 实际到达时间，节点反馈后填写 |
| pig_speed | DECIMAL(8,2) | 是 | 该段清管器速度（km/h） |
| is_revised | TINYINT(1) | 是 | 0=首次预测，1=修正后 |
| parent_record_id | BIGINT | 否 | 修正来源记录ID |
| revision_count | INT | 是 | 修正次数 |

### 7. warning — 预警记录

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| operation_id | BIGINT | 是 | 关联作业 |
| warning_type | VARCHAR(30) | 是 | 延迟 / 速度异常 / 卡阻 |
| level | VARCHAR(10) | 是 | 高 / 中 / 低 |
| content | TEXT | 是 | 预警内容描述 |
| suggestion | TEXT | 否 | 处置建议 |
| status | VARCHAR(20) | 是 | 未处理 → 已确认 → 已关闭 |
| resolved_time | DATETIME | 否 | 解除时间 |

### 8. gas_compress_factor — 气体压缩因子（Z 值）

| 列名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | 主键 |
| avg_pressure | DECIMAL(8,2) | 是 | 平均压力（MPa） |
| temperature | DECIMAL(6,2) | 是 | 介质温度（℃），默认 20 |
| compress_factor | DECIMAL(6,4) | 是 | 压缩因子 Z 值 |

> **注意**：压缩因子取决于天然气具体组分，init.sql 中预填的为示意值，正式使用前需根据实际气质参数替换。

## 数据流转示意

```
1. 录入管线 → 录入站点 → 自动生成管段
                         ↓
2. 创建作业（选管线+清管器+起止站+填工况参数）
                         ↓
3. 计算引擎 → 生成 tracking_record（各站点预计到达时间）
                         ↓
4. 值班人员反馈节点实际到达时间 → 更新 actual_arrival_time
                         ↓
5. 触发重算 → 新增修正版 tracking_record（is_revised=1）
                         ↓
6. 异常检测 → 生成 warning（延迟/卡阻/速度异常）
                         ↓
7. 作业完成 → operation.status = '已完成'
```
