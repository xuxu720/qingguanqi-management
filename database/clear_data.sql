-- ============================================
-- 清管器作业管理系统 - 清空所有业务数据
-- 生成时间: 2026-06-12
-- 说明: 按外键依赖顺序删除，先删子表再删父表
--       同时重置自增 ID（AUTO_INCREMENT = 1）
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- 注意：关闭外键检查后顺序不再重要，但仍按依赖层级排列便于阅读

-- ====== 第 1 层：叶子表（无子表依赖） ======

-- 1. AI 对话消息
TRUNCATE TABLE message;
ALTER TABLE message AUTO_INCREMENT = 1;

-- 2. 跟踪记录（有自引用 parent_record_id，先清）
TRUNCATE TABLE tracking_record;
ALTER TABLE tracking_record AUTO_INCREMENT = 1;

-- 3. 预警记录
TRUNCATE TABLE warning;
ALTER TABLE warning AUTO_INCREMENT = 1;

-- 4. 气体压缩因子（参考数据，非业务数据，可保留或清空）
TRUNCATE TABLE gas_compress_factor;
ALTER TABLE gas_compress_factor AUTO_INCREMENT = 1;

-- ====== 第 2 层：中间表 ======

-- 5. 清管作业
TRUNCATE TABLE operation;
ALTER TABLE operation AUTO_INCREMENT = 1;

-- 6. 管段
TRUNCATE TABLE pipeline_segment;
ALTER TABLE pipeline_segment AUTO_INCREMENT = 1;

-- 7. 站点/阀室
TRUNCATE TABLE station;
ALTER TABLE station AUTO_INCREMENT = 1;

-- ====== 第 3 层：根表 ======

-- 8. 清管器
TRUNCATE TABLE pig;
ALTER TABLE pig AUTO_INCREMENT = 1;

-- 9. 管线
TRUNCATE TABLE pipeline;
ALTER TABLE pipeline AUTO_INCREMENT = 1;

-- 10. AI 对话会话
TRUNCATE TABLE conversation;
ALTER TABLE conversation AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 验证：查询所有表行数，确认已清空
-- ============================================
SELECT 'pig'                 AS table_name, COUNT(*) AS row_count FROM pig
UNION ALL
SELECT 'pipeline',            COUNT(*) FROM pipeline
UNION ALL
SELECT 'station',             COUNT(*) FROM station
UNION ALL
SELECT 'pipeline_segment',    COUNT(*) FROM pipeline_segment
UNION ALL
SELECT 'operation',           COUNT(*) FROM operation
UNION ALL
SELECT 'tracking_record',     COUNT(*) FROM tracking_record
UNION ALL
SELECT 'warning',             COUNT(*) FROM warning
UNION ALL
SELECT 'conversation',        COUNT(*) FROM conversation
UNION ALL
SELECT 'message',             COUNT(*) FROM message
UNION ALL
SELECT 'gas_compress_factor', COUNT(*) FROM gas_compress_factor;
