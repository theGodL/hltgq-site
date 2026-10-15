-- 节水管理（节水方案和措施 / 节水指标体系）：新建三张自研表（独立执行版）
-- 对应《节水潜力开发实施方案》V1.4 §3 数据模型、§2 业务规则、§6 核心计算与状态机；
-- 累积初始化脚本见 src/main/resources/init.sql（已同步更新，全新建库无需执行本文件）。
-- 背景（旧表核对结论，2026-10-15 只读探针实测）：
--   ① t_auto_hltgq_yn8cm_bqinjk（平台低代码·节水方案台账，8 行）——列为 年度/名称/措施/责任单位/状态/审核意见，
--      无 baseline 快照、节水目标、依据关联、指标关联、核验留痕、附件，且低代码表加列需平台侧操作，不满足；
--   ② t_auto_hltgq_yn8cm_iiitnh（平台低代码·节水体系台账，4 行）——明细 JSON 仅"指标名+数值"，
--      无计算方式/用途/启停维护，无法参与方案计算，不满足；
--   ③ t_auto_hltgq_water_saving_potential（自研）——潜力测算页专用（年度+范围+行业基准），语义不同不复用。
--   故按项目惯例自研新建；旧台账保持不动（不迁移、不读写）。
-- 变更：
--   ① t_auto_hltgq_water_saving_annual     年度水量记录：potential_year 唯一；潜力 = 实际 − 计划（可为负，不截断）
--   ② t_auto_hltgq_water_saving_scheme     节水方案：名称年度内唯一；baseline 快照固化；verify_* 核验留痕 + verify_invalid 作废标记（方案A）
--   ③ t_auto_hltgq_water_saving_indicator  节水指标：名称全局唯一（含停用）；method 固定三选一（方法驱动服务端计算）
-- 口径：水量一律万m³（NUMERIC(18,6)；用水计划 total_water 库内为 m³，接口层 ÷10000 换算后落库）；
--       字典字段沿用 "#N#" 文本编码（后端常量维护、前端经接口获取不写死）；布尔语义用 "#1#/#2#" 文本（项目惯例）。
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：CREATE TABLE / CREATE INDEX 均带 IF NOT EXISTS，可重复执行；三表均不存在，无存量数据影响
-- 执行方式：以具备 "qixiao-apaas" schema 修改权限的账号连接目标库后整体执行（BEGIN/COMMIT 事务包裹）
-- ============================================================
BEGIN;

-- ① 年度水量记录：行 = 一个潜力年度；被方案引用后禁止删除、潜力年度禁止修改（服务层校验）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_saving_annual (
    id                 VARCHAR(64)  PRIMARY KEY,
    potential_year     INTEGER      NOT NULL,       -- 潜力年度（如 2026；全表唯一，被方案引用后禁止修改）
    basis_year         INTEGER      NOT NULL,       -- 依据年度（= potential_year − 1，自动带出只读）
    prev_plan_volume   NUMERIC(18,6),               -- 上一年计划放水量（万m³；自动带出或手工覆盖，非负）
    prev_actual_volume NUMERIC(18,6),               -- 上一年实际放水量（万m³；本期手工录入，非负）
    plan_source        VARCHAR(20),                 -- 计划来源：auto_plan 用水计划带出 / manual 手工录入
    plan_id            VARCHAR(64),                 -- 来源计划 id（plan_source=auto_plan 时留痕；water_plan.id）
    actual_source      VARCHAR(30),                 -- 实际来源：manual 手工（canal_head_meter 渠首自动=本期预留）
    is_demo            VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- 演示数据标记：#1# 虚拟演示 / #2# 正式数据（导出默认排除 #1#）
    corp_code          VARCHAR(64),
    created_at         TIMESTAMP,
    created_by         VARCHAR(64),
    updated_at         TIMESTAMP,
    updated_by         VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_saving_annual_year
    ON "qixiao-apaas".t_auto_hltgq_water_saving_annual(potential_year);

-- ② 节水方案：baseline 建方案时快照固化（不随年度记录改动更新）；核验数据保留 + verify_invalid 作废标记（方案A）
--    状态机：#1# 草稿 / #2# 实施中 —（核验 comparable=#1#）→ #3# 已完成；—（comparable=#2#）→ #4# 待复核
--    核验作废（年度记录水量被改 / 编辑换依据 / 刷新对比依据）：status 置 #4#、verify_* 保留、verify_invalid=#1#、达标强制"待核验"
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_saving_scheme (
    id                  VARCHAR(64)  PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,      -- 方案名称（同一年度内唯一）
    "year"              INTEGER      NOT NULL,      -- 实施年度（= 关联记录的 potential_year；保留字，SQL 全程加引号）
    evidence_id         VARCHAR(64)  NOT NULL,      -- 关联年度水量记录（saving_annual.id；被引用记录禁止删除）
    baseline_volume     NUMERIC(18,6),              -- 实施前实际用水量快照（建方案时 = 记录.prev_actual_volume；仅"刷新对比依据"才更新）
    target_volume       NUMERIC(18,6) NOT NULL,     -- 节水目标（万m³；0 < 目标 ≤ 潜力）
    owner               VARCHAR(100) NOT NULL,      -- 负责人（如"工程管理处 / 张工"）
    measure             TEXT         NOT NULL,      -- 具体措施
    status              VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- #1# 草稿 / #2# 实施中 / #3# 已完成 / #4# 待复核
    indicator_ids       TEXT         NOT NULL DEFAULT '[]',   -- 关联评估指标 id 数组（JSON；核验时按公式自动计算）
    verify_after_volume NUMERIC(18,6),              -- 核验：实施后实际用水量（万m³；核验动作写入）
    verify_source       VARCHAR(200),               -- 核验资料来源（核验必填，如台账/报表编号）
    verify_note         TEXT,                       -- 核验说明（选填）
    verify_date         DATE,                       -- 核验日期（核验动作当天）
    verify_comparable   VARCHAR(8),                 -- 对比条件确认：#1# 已确认（计算指标）/ #2# 未确认（待复核、不计算）；NULL=未核验
    verify_invalid      VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- 核验作废标记（方案A）：#1# 已作废（数据保留、达标强制待核验）/ #2# 有效
    last_recalc_at      TIMESTAMP,                  -- 达标重算/核验作废最近时间（留痕）
    last_recalc_reason  VARCHAR(200),               -- 重算/作废原因（如"核验依据已变更"）
    is_demo             VARCHAR(8)   NOT NULL DEFAULT '#2#',  -- 演示数据标记：#1# 虚拟演示 / #2# 正式数据
    attachment_name     VARCHAR(255),               -- 附件原始文件名（下载展示）
    attachment_url      VARCHAR(500),               -- 附件存储路径（file-dir 相对路径：saving/{yyyy-MM}/{uuid}_{原名}）
    corp_code           VARCHAR(64),
    created_at          TIMESTAMP,
    created_by          VARCHAR(64),
    updated_at          TIMESTAMP,
    updated_by          VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_saving_scheme_year_name
    ON "qixiao-apaas".t_auto_hltgq_water_saving_scheme("year", name);
CREATE INDEX IF NOT EXISTS idx_water_saving_scheme_evidence
    ON "qixiao-apaas".t_auto_hltgq_water_saving_scheme(evidence_id);
CREATE INDEX IF NOT EXISTS idx_water_saving_scheme_status
    ON "qixiao-apaas".t_auto_hltgq_water_saving_scheme(status);

-- ③ 节水指标：计算方式固定三种（下拉选择后单位/公式自动确定，不可自由编辑）；名称全局唯一（含停用）
--    节水量(#1#)：万m³ = 实施前 − 实施后；节水率(#2#)：% = 节水量 ÷ 实施前 × 100；目标完成率(#3#)：% = 节水量 ÷ 方案目标 × 100
--    分母为 0 → 结果 null（展示"无法计算"，不参与判定）；已关联方案的指标计算方式锁定、不可删除
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_saving_indicator (
    id           VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,             -- 指标名称（全局唯一，含停用指标）
    method       VARCHAR(16)  NOT NULL,             -- 计算方式：#1# 节水量(万m³) / #2# 节水率(%) / #3# 目标完成率(%)
    target_value NUMERIC(18,6),                     -- 目标值（可空；未设目标 → 方案"待设置目标"、不出达标结论）
    purpose      TEXT,                              -- 指标用途（选填）
    enabled      VARCHAR(8)   NOT NULL DEFAULT '#1#',  -- 状态：#1# 启用 / #2# 停用（停用后新方案不可勾选，既有关联保留）
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_saving_indicator_name
    ON "qixiao-apaas".t_auto_hltgq_water_saving_indicator(name);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 3 行表名）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name IN ('t_auto_hltgq_water_saving_annual','t_auto_hltgq_water_saving_scheme','t_auto_hltgq_water_saving_indicator')
--  ORDER BY table_name;
--
-- 执行后自查（应返回 5 行：uk_water_saving_annual_year / uk_water_saving_scheme_year_name / uk_water_saving_indicator_name
--  及 idx_water_saving_scheme_evidence / idx_water_saving_scheme_status）：
-- SELECT indexname FROM pg_indexes WHERE schemaname = 'qixiao-apaas'
--   AND (indexname LIKE 'uk_water_saving%' OR indexname LIKE 'idx_water_saving%') ORDER BY indexname;
-- ============================================================
