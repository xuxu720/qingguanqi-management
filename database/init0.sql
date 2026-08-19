-- ============================================================================
-- 清管器作业管理系统 — 数据库初始化脚本
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4
-- ============================================================================

CREATE DATABASE IF NOT EXISTS qingguanqi
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE qingguanqi;


-- ============================================================================
-- 1. 清管器基础信息表
-- ============================================================================
CREATE TABLE IF NOT EXISTS pig (
    id              BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    type            VARCHAR(50)     NOT NULL                        COMMENT '清管器类型：四皮碗清管器/泡沫清管器/测径清管器等',
    spec            VARCHAR(100)    NOT NULL                        COMMENT '规格型号',
    interference_rate DECIMAL(5,2) NOT NULL                        COMMENT '过盈量（%）',
    applicable_scene VARCHAR(200)   DEFAULT NULL                    COMMENT '适用场景描述',
    medium_type     VARCHAR(10)     NOT NULL                        COMMENT '适用介质：液体/气体/通用',
    status          VARCHAR(20)     NOT NULL    DEFAULT '可用'       COMMENT '状态：可用/使用中/报废',
    remark          VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_pig_type (type),
    INDEX idx_pig_status (status),
    INDEX idx_pig_medium (medium_type)
) ENGINE=InnoDB COMMENT='清管器基础信息表';


-- ============================================================================
-- 2. 管线基础信息表
-- ============================================================================
CREATE TABLE IF NOT EXISTS pipeline (
    id                  BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    name                VARCHAR(100)    NOT NULL                        COMMENT '管线名称（如：石兰线/兰成线）',
    medium_type         VARCHAR(10)     NOT NULL                        COMMENT '介质类型：液体/气体',
    diameter            DECIMAL(10,2)   DEFAULT NULL                    COMMENT '管径（mm）',
    design_pressure_min DECIMAL(8,2)    DEFAULT NULL                    COMMENT '设计压力下限（MPa）',
    design_pressure_max DECIMAL(8,2)    DEFAULT NULL                    COMMENT '设计压力上限（MPa）',
    total_length        DECIMAL(10,4)   DEFAULT NULL                    COMMENT '管线总长度（km）',
    remark              VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uk_pipeline_name (name),
    INDEX idx_pipeline_medium (medium_type)
) ENGINE=InnoDB COMMENT='管线基础信息表';


-- ============================================================================
-- 3. 站点/阀室表
-- ============================================================================
CREATE TABLE IF NOT EXISTS station (
    id              BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    pipeline_id     BIGINT          NOT NULL                        COMMENT '所属管线ID',
    name            VARCHAR(100)    NOT NULL                        COMMENT '站点名称',
    station_type    VARCHAR(20)     NOT NULL                        COMMENT '站点类型：站场/阀室',
    mileage         DECIMAL(10,4)   NOT NULL                        COMMENT '累计里程（km），从管线起点起算',
    elevation       DECIMAL(8,2)    DEFAULT NULL                    COMMENT '高程（m）',
    sort_order      INT             NOT NULL    DEFAULT 0           COMMENT '沿管线方向的排序序号，从0开始',
    remark          VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_station_pipeline (pipeline_id),
    INDEX idx_station_sort (pipeline_id, sort_order),
    CONSTRAINT fk_station_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipeline(id)
) ENGINE=InnoDB COMMENT='站点/阀室表';


-- ============================================================================
-- 4. 管段表（相邻两站之间的管段参数）
-- ============================================================================
CREATE TABLE IF NOT EXISTS pipeline_segment (
    id                  BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    pipeline_id         BIGINT          NOT NULL                        COMMENT '所属管线ID',
    from_station_id     BIGINT          NOT NULL                        COMMENT '起始站/阀室ID',
    to_station_id       BIGINT          NOT NULL                        COMMENT '到达站/阀室ID',
    distance            DECIMAL(10,4)   NOT NULL                        COMMENT '站间距（km）',
    unit_capacity       DECIMAL(10,4)   DEFAULT NULL                    COMMENT '单位管容（m³/km），液体管道填写',
    inner_diameter      DECIMAL(10,2)   DEFAULT NULL                    COMMENT '管道内径（mm），气体管道填写',
    remark              VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_seg_pipeline (pipeline_id),
    INDEX idx_seg_from (from_station_id),
    INDEX idx_seg_to (to_station_id),
    UNIQUE INDEX uk_seg_stations (pipeline_id, from_station_id, to_station_id),
    CONSTRAINT fk_seg_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipeline(id),
    CONSTRAINT fk_seg_from FOREIGN KEY (from_station_id) REFERENCES station(id),
    CONSTRAINT fk_seg_to FOREIGN KEY (to_station_id) REFERENCES station(id)
) ENGINE=InnoDB COMMENT='管段参数表（相邻两站之间的管段）';


-- ============================================================================
-- 5. 清管作业表
-- ============================================================================
CREATE TABLE IF NOT EXISTS operation (
    id                  BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    pipeline_id         BIGINT          NOT NULL                        COMMENT '管线ID',
    pig_id              BIGINT          NOT NULL                        COMMENT '清管器ID',
    operation_type      VARCHAR(50)     NOT NULL                        COMMENT '作业类型：常规清管/应急清管',
    from_station_id     BIGINT          NOT NULL                        COMMENT '发球站ID',
    to_station_id       BIGINT          NOT NULL                        COMMENT '收球站ID（最终目标站）',
    dispatch_time       DATETIME        NOT NULL                        COMMENT '发球时间',
    displacement        DECIMAL(10,2)   DEFAULT NULL                    COMMENT '排量（m³/h），液体管道填写',
    gas_flow_rate       DECIMAL(12,2)   DEFAULT NULL                    COMMENT '输气量（Nm³/d），气体管道填写',
    outlet_pressure     DECIMAL(8,2)    DEFAULT NULL                    COMMENT '出站压力（MPa），气体管道填写',
    inlet_pressure      DECIMAL(8,2)    DEFAULT NULL                    COMMENT '进站压力（MPa），气体管道填写',
    status              VARCHAR(20)     NOT NULL    DEFAULT '准备'       COMMENT '作业状态：准备/运行中/已完成/异常',
    remark              VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_op_pipeline (pipeline_id),
    INDEX idx_op_pig (pig_id),
    INDEX idx_op_status (status),
    INDEX idx_op_dispatch (dispatch_time),
    CONSTRAINT fk_op_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipeline(id),
    CONSTRAINT fk_op_pig FOREIGN KEY (pig_id) REFERENCES pig(id),
    CONSTRAINT fk_op_from_station FOREIGN KEY (from_station_id) REFERENCES station(id),
    CONSTRAINT fk_op_to_station FOREIGN KEY (to_station_id) REFERENCES station(id)
) ENGINE=InnoDB COMMENT='清管作业表';


-- ============================================================================
-- 6. 跟踪记录表（记录每个站点/阀室的到达预测与实际到达情况）
-- ============================================================================
CREATE TABLE IF NOT EXISTS tracking_record (
    id                      BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    operation_id            BIGINT          NOT NULL                        COMMENT '作业ID',
    station_id              BIGINT          NOT NULL                        COMMENT '站点/阀室ID',
    segment_distance        DECIMAL(10,4)   NOT NULL                        COMMENT '距离上一站的管段长度（km）',
    predicted_arrival_time  DATETIME        NOT NULL                        COMMENT '预计到达时间',
    actual_arrival_time     DATETIME        DEFAULT NULL                    COMMENT '实际到达时间（节点反馈后填写）',
    pig_speed               DECIMAL(8,2)    NOT NULL                        COMMENT '该段清管器速度（km/h）',
    is_revised              TINYINT(1)      NOT NULL    DEFAULT 0           COMMENT '是否修正过：0=首次预测，1=修正后',
    parent_record_id        BIGINT          DEFAULT NULL                    COMMENT '修正来源：上一版记录的ID',
    revision_count          INT             NOT NULL    DEFAULT 0           COMMENT '修正次数',
    remark                  VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    is_key_station          TINYINT(1)      NOT NULL    DEFAULT 0           COMMENT '是否关键站：0=普通站，1=关键站（收发球站/大型跨越/转折点）',
    create_time             DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_tr_key_station (is_key_station),
    INDEX idx_tr_operation (operation_id),
    INDEX idx_tr_station (station_id),
    INDEX idx_tr_predicted (predicted_arrival_time),
    CONSTRAINT fk_tr_operation FOREIGN KEY (operation_id) REFERENCES operation(id),
    CONSTRAINT fk_tr_station FOREIGN KEY (station_id) REFERENCES station(id),
    CONSTRAINT fk_tr_parent FOREIGN KEY (parent_record_id) REFERENCES tracking_record(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='跟踪记录表';


-- ============================================================================
-- 7. 预警记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS warning (
    id              BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    operation_id    BIGINT          NOT NULL                        COMMENT '关联作业ID',
    warning_type    VARCHAR(30)     NOT NULL                        COMMENT '预警类型：延迟/速度异常/卡阻',
    level           VARCHAR(10)     NOT NULL                        COMMENT '预警等级：高/中/低',
    content         TEXT            NOT NULL                        COMMENT '预警内容描述',
    suggestion      TEXT            DEFAULT NULL                    COMMENT '处置建议',
    status          VARCHAR(20)     NOT NULL    DEFAULT '未处理'     COMMENT '状态：未处理/已确认/已关闭',
    resolved_time   DATETIME        DEFAULT NULL                    COMMENT '解除时间',
    remark          VARCHAR(500)    DEFAULT NULL                    COMMENT '备注',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_war_operation (operation_id),
    INDEX idx_war_type (warning_type),
    INDEX idx_war_level (level),
    INDEX idx_war_status (status),
    CONSTRAINT fk_war_operation FOREIGN KEY (operation_id) REFERENCES operation(id)
) ENGINE=InnoDB COMMENT='预警记录表';


-- ============================================================================
-- 8. 气体压缩因子参考表（用于气体管道计算的查表）
-- ============================================================================
CREATE TABLE IF NOT EXISTS gas_compress_factor (
    id                  BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    avg_pressure        DECIMAL(8,2)    NOT NULL                        COMMENT '平均压力（MPa）',
    temperature         DECIMAL(6,2)    NOT NULL    DEFAULT 20.00       COMMENT '介质温度（℃），默认20℃',
    compress_factor     DECIMAL(6,4)    NOT NULL                        COMMENT '压缩因子 Z',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE INDEX uk_pressure_temp (avg_pressure, temperature),
    INDEX idx_factor_pressure (avg_pressure)
) ENGINE=InnoDB COMMENT='气体压缩因子参考表';


-- ============================================================================
-- 9. 智能助手会话表
-- ============================================================================
CREATE TABLE IF NOT EXISTS conversation (
    id              BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    title           VARCHAR(200)    DEFAULT NULL                    COMMENT '会话标题（首条用户消息摘要）',
    create_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='智能助手会话表';


-- ============================================================================
-- 10. 智能助手消息表
-- ============================================================================
CREATE TABLE IF NOT EXISTS message (
    id                  BIGINT          AUTO_INCREMENT  PRIMARY KEY     COMMENT '主键ID',
    conversation_id     BIGINT          NOT NULL                        COMMENT '会话ID',
    role                VARCHAR(20)     NOT NULL                        COMMENT '角色：user / assistant',
    content             TEXT            NOT NULL                        COMMENT '消息内容',
    intent              VARCHAR(30)     DEFAULT NULL                    COMMENT '意图：CREATE_OPERATION / NODE_ARRIVAL / QUERY_STATUS / FOLLOW_UP',
    operation_id        BIGINT          DEFAULT NULL                    COMMENT '关联作业ID（创建或反馈时关联）',
    metadata_json       TEXT            DEFAULT NULL                    COMMENT '附加元数据JSON（提取的参数、计算结果等）',
    create_time         DATETIME        NOT NULL    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_msg_conversation (conversation_id),
    INDEX idx_msg_operation (operation_id),
    CONSTRAINT fk_msg_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB COMMENT='智能助手消息表';


-- ============================================================================
-- 初始化数据：天然气压缩因子对照表（来源：Excel「压缩因子」分页）
-- ============================================================================
SET SQL_SAFE_UPDATES = 0;
DELETE FROM gas_compress_factor;
SET SQL_SAFE_UPDATES = 1;

INSERT INTO gas_compress_factor (avg_pressure, temperature, compress_factor) VALUES
(0.0,  20, 1.0000),
(2.0,  20, 0.9532),
(2.5,  20, 0.9423),
(3.0,  20, 0.9315),
(3.5,  20, 0.9209),
(4.0,  20, 0.9105),
(4.5,  20, 0.9003),
(5.0,  20, 0.8903),
(5.5,  20, 0.8806),
(6.0,  20, 0.8713),
(6.5,  20, 0.8623),
(7.0,  20, 0.8537),
(7.5,  20, 0.8455),
(8.0,  20, 0.8377),
(8.5,  20, 0.8305),
(9.0,  20, 0.8228),
(9.5,  20, 0.8177),
(9.9,  20, 0.8132),
(10.0, 20, 0.8128),
(10.5, 20, 0.8063),
(11.0, 20, 0.8003);
