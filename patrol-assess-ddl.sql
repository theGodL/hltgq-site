-- ============================================================
-- 巡查考核功能建表脚本（独立执行版，2026-10）
-- 对应巡查计划列表页「考核 / 查看考核」入口（PatrolAssessController）；本脚本为唯一建表来源
-- 业务口径：
--   ① 仅「已完成（#3#）」计划可考核，一计划一条，提交后不可重复考核（唯一索引兜底）
--   ② 自动项在提交时重算并落库（快照），查看读快照——后续问题流转不会改写已考核数据
--   ③ 统计范围 = 该计划下巡检记录关联的问题（问题表 abqezf = 巡检记录 id）
--   ④ 作废问题（#5#）不计入任何一项；恒等式：发现问题 = 已整改 + 整改中 + 未整改
--      已整改 = #4# 已关闭；整改中 = #2# 处理中 + #3# 已转工单；未整改 = #1# 待处理
--   ⑤ 手工项只有「得分」和「结论」；等级由得分分档自动生成（≥90 优秀 / 80~89 良好 / 60~79 及格 / <60 不及格），
--      以服务端推导为准，不接受人工指定
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- ============================================================
BEGIN;

CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_patrol_assess (
    id             VARCHAR(64)   PRIMARY KEY,
    schedule_id    VARCHAR(64)   NOT NULL,              -- 巡检计划 ID（patrol_schedule.id）
    plan_code      VARCHAR(64),                         -- 计划编号快照
    plan_title     VARCHAR(255),                        -- 计划名称快照
    plan_site      INTEGER       NOT NULL DEFAULT 0,    -- 计划巡查站点（个）：巡查范围关系表行数
    patrolled_site INTEGER       NOT NULL DEFAULT 0,    -- 已巡查站点（个）：巡检记录站点去重
    record_count   INTEGER       NOT NULL DEFAULT 0,    -- 巡查记录（条）：不含空白占位草稿
    issue_total    INTEGER       NOT NULL DEFAULT 0,    -- 发现问题（项）
    issue_fixed    INTEGER       NOT NULL DEFAULT 0,    -- 已整改（项）：已关闭 #4#
    issue_fixing   INTEGER       NOT NULL DEFAULT 0,    -- 整改中（项）：处理中 #2# + 已转工单 #3#
    issue_open     INTEGER       NOT NULL DEFAULT 0,    -- 未整改（项）：待处理 #1#
    score          NUMERIC(5,1)  NOT NULL,              -- 考核得分（0~100，1 位小数，手工填写）
    grade          VARCHAR(16)   NOT NULL,              -- 考核等级（由得分自动分档）：#1# 不及格 / #2# 及格 / #3# 良好 / #4# 优秀
    conclusion     VARCHAR(1000),                       -- 考核结论（手工填写，可空）
    assessor_id    VARCHAR(64),                         -- 考核人用户 id（提交时登录人，自动留痕）
    assessor_name  VARCHAR(64),                         -- 考核人姓名快照
    corp_code      VARCHAR(64),
    created_at     TIMESTAMP,
    created_by     VARCHAR(64),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(64)
);

-- 一计划一考核：应用层先查后插 + 数据库唯一索引双重保证
CREATE UNIQUE INDEX IF NOT EXISTS uk_patrol_assess_schedule
    ON "qixiao-apaas".t_auto_hltgq_water_patrol_assess(schedule_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 1 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name = 't_auto_hltgq_water_patrol_assess';
-- ============================================================
