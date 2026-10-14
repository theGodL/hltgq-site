-- ============================================================
-- 水资源调度 · 用水管理三页面建表脚本（独立执行版）
-- 对应《水资源调度系统 · 三页面设计方案》二、数据模型：
--   用水户管理（water_user）/ 用水定额标准库（water_quota）/ 用水计划管理（water_plan + water_plan_item）
-- 累积初始化脚本见 src/main/resources/init.sql（同内容已追加）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- 说明：① 字典字段统一 "#N#" 文本编码（同平台风格，后端常量维护、前端经接口获取不写死）；
--       ② 水量单位 m³（NUMERIC 2 位小数，展示层换算万m³ 1 位）；面积亩（2 位）；水价 元/m³（3 位）；
--       ③ 编号（YH/YJ + 3 位序号）由应用层生成，唯一索引兜底并发。
-- ============================================================
BEGIN;

-- 1.1 用水户：编号 YH+3位序号（YH001 起）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_user (
    id              VARCHAR(64)  PRIMARY KEY,
    user_no         VARCHAR(32)  NOT NULL,          -- 编号：YH + 3 位序号（全局唯一）
    name            VARCHAR(100) NOT NULL,          -- 名称（必填，全局唯一）
    user_type       VARCHAR(16)  NOT NULL,          -- 用水类型：#1# 农业 / #2# 工业 / #3# 生活 / #4# 生态
    contact_name    VARCHAR(50)  NOT NULL,          -- 联系人（必填）
    contact_phone   VARCHAR(20),                    -- 联系电话（可空；填写须 11 位数字，应用层校验）
    irrigation_area NUMERIC(12,2),                  -- 灌溉面积（亩；农业/生态必填，工业/生活可空）
    water_price     NUMERIC(10,3) NOT NULL,         -- 水价（元/m³，>0）
    calc_mode       VARCHAR(16)  NOT NULL,          -- 计算策略：#1# 指定闸口累加 / #2# 闸口差额计量
    gate_config     TEXT         NOT NULL,          -- 关联闸口（JSON，闸站 id + 名称快照）：
                                                    --   累加 {"mode":"SUM","gates":[{"id":"…","name":"…"},…]}
                                                    --   差额 {"mode":"DIFF","main":{…},"deduct":[{…},…]}
    settle_cycle    VARCHAR(16)  NOT NULL,          -- 统计周期：#1# 月度结算 / #2# 季度结算 / #3# 年度结算
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 状态：#1# 正常 / #2# 停用
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_user_no
    ON "qixiao-apaas".t_auto_hltgq_water_user(user_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_user_name
    ON "qixiao-apaas".t_auto_hltgq_water_user(name);

-- 1.2 用水定额标准：作物种类 × 灌溉方式 → 定额标准（m³/亩）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_quota (
    id                VARCHAR(64)  PRIMARY KEY,
    crop_type         VARCHAR(100) NOT NULL,        -- 作物种类（水稻/冬小麦/…；亦含工业用水、城镇生活等行业类型）
    irrigation_method VARCHAR(100),                 -- 灌溉方式（可空）
    quota_value       NUMERIC(12,2) NOT NULL,       -- 定额标准（m³/亩，>0）
    remark            VARCHAR(200),                 -- 备注（如"泡田+生育期全周期定额"）
    corp_code         VARCHAR(64),
    created_at        TIMESTAMP,
    created_by        VARCHAR(64),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(64)
);

-- 1.3 用水计划主表：计划总水量为子表汇总（应用层维护）；编制中可删（主表+明细物理删除），其余状态终态保护
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_plan (
    id              VARCHAR(64)  PRIMARY KEY,
    plan_no         VARCHAR(32)  NOT NULL,          -- 编号：YJ + 3 位序号（YJ001 起，全局唯一）
    plan_name       VARCHAR(200) NOT NULL,          -- 计划名称（必填，全局唯一；可自动生成如"2026年10月中旬旬度调度计划"）
    plan_type       VARCHAR(16)  NOT NULL,          -- 类型：#1# 年度 / #2# 月度 / #3# 旬度
    period_start    DATE         NOT NULL,          -- 计划周期起（按类型自动填充，不可修改）
    period_end      DATE         NOT NULL,          -- 计划周期止（年度=12-31；旬度按自然月末计算）
    total_water     NUMERIC(16,2) NOT NULL DEFAULT 0,  -- 计划总水量（m³，明细计划水量汇总，只读）
    scheduled_qty   NUMERIC(16,2) NOT NULL DEFAULT 0,  -- 已调度水量（m³，默认 0；调度模块后续回写）
    dispatch_ref_id VARCHAR(64),                    -- 调度方案关联引用（预留字段，本期不使用）
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- #1# 编制中 / #2# 已生效 / #3# 已归档 / #4# 已作废
    void_by         VARCHAR(64),                    -- 作废人（作废时记录）
    void_at         TIMESTAMP,                      -- 作废时间
    void_reason     VARCHAR(200),                   -- 作废原因
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_plan_no
    ON "qixiao-apaas".t_auto_hltgq_water_plan(plan_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_plan_name
    ON "qixiao-apaas".t_auto_hltgq_water_plan(plan_name);
CREATE INDEX IF NOT EXISTS idx_water_plan_status
    ON "qixiao-apaas".t_auto_hltgq_water_plan(status);
CREATE INDEX IF NOT EXISTS idx_water_plan_type_period
    ON "qixiao-apaas".t_auto_hltgq_water_plan(plan_type, period_start, period_end);

-- 1.4 用水计划明细：行 = 用水户 × 定额（同一期可多行，区分春灌/夏灌等阶段）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_plan_item (
    id              VARCHAR(64)  PRIMARY KEY,
    plan_id         VARCHAR(64)  NOT NULL,          -- 计划主表 ID（water_plan.id）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（water_user.id）
    irrigation_area NUMERIC(12,2) NOT NULL,         -- 灌溉面积（亩，选用水户带出的快照，编制中可改，不回写用水户）
    quota_id        VARCHAR(64)  NOT NULL,          -- 适用定额 ID（water_quota.id）
    calc_water      NUMERIC(16,2) NOT NULL,         -- 测算水量（m³，= 面积 × 定额，只读自动计算）
    plan_water      NUMERIC(16,2) NOT NULL,         -- 计划水量（m³，核定填写，默认 = 测算水量）
    override_reason VARCHAR(200),                   -- 超测算说明（计划水量 > 测算水量时必填）
    stage_tag       VARCHAR(50),                    -- 阶段标签（春灌/夏灌等，可空）
    sort_order      INTEGER      NOT NULL DEFAULT 0, -- 明细行序（页面展示顺序）
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_water_plan_item_plan
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(plan_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_water_plan_item_user
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_plan_item_quota
    ON "qixiao-apaas".t_auto_hltgq_water_plan_item(quota_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 4 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name IN ('t_auto_hltgq_water_user', 't_auto_hltgq_water_quota',
--                       't_auto_hltgq_water_plan', 't_auto_hltgq_water_plan_item');
-- ============================================================
