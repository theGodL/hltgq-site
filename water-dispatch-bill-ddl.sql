-- ============================================================
-- 水资源调度 · 调度与水费四页面建表脚本（独立执行版）
-- 对应页面：需水上报（water-demand-report）/ 调度方案（water-dispatch-plan）/
--           调度记录（water-dispatch-record）/ 水费账单（water-bill）
-- 累积初始化脚本见 src/main/resources/init.sql（同内容已追加）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- 说明：① 字典字段统一 "#N#" 文本编码（同平台风格，后端常量维护；接口对外映射为
--         前端英文码 wait/pass/reject、pending/running/done/canceled、up/down/adjust、
--         generated/paid、month/quarter/year，见 WaterUsageDict）；
--       ② 水量库内一律 m³（NUMERIC 2 位小数）；四页面接口对外输出万m³（÷10000，4 位小数），
--          金额 = 计入水量(m³) × 水价(元/m³)，元（2 位小数）；
--       ③ 编号由应用层 MAX+1 生成、唯一索引兜底并发（冲突由 Controller 重试）：
--          需水上报 XS+4 位；调度方案 DD+4 位；账单 SF+期间码+'-'+户号+4 位（如 SF202610-YH0010001）；
--          调度记录无编号；
--       ④ 计入水量口径（与流量监测/灌溉用水一致）：区间累计 = ttf(窗口内末行) − ttf(起点前最近一条
--          ttf 非空行)，起点前无基准按 0，哨兵值 -999/-9991 视为缺失；
--          差额策略（用水户 calc_mode=#2#）：扣减闸口方案取负值参与汇总；
--       ⑤ 计费关系：方案行经 bill_id 挂接账单（1 账单 : N 方案），出账时以 billed_qty 快照"计入水量"，
--          账单删除时清空挂接、方案恢复未计费（实时重新计量）。
-- ============================================================
BEGIN;

-- 2.1 需水上报：编号 XS+4 位序号；审核态 待审核(#1#)→已通过(#2#)/已驳回(#3#)
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_demand (
    id              VARCHAR(64)  PRIMARY KEY,
    demand_no       VARCHAR(32)  NOT NULL,          -- 编号：XS + 4 位序号（全局唯一）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（water_user.id）
    gate_id         VARCHAR(64),                    -- 申报拟用闸口（可空；编制方案时预填，须属于该户闸口组）
    amount          NUMERIC(16,2) NOT NULL,         -- 申报计划水量（m³，>0；对外展示万m³）
    start_date      DATE         NOT NULL,          -- 用水时段起
    end_date        DATE         NOT NULL,          -- 用水时段止（>= start_date）
    usage_desc      VARCHAR(200),                   -- 用途（如 冬灌前补水）
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 审核状态：#1# 待审核 / #2# 已通过 / #3# 已驳回
    audit_opinion   VARCHAR(200),                   -- 审核意见（驳回必填）
    audit_by        VARCHAR(64),                    -- 审核人
    audit_at        TIMESTAMP,                      -- 审核时间
    remark          VARCHAR(200),                   -- 备注
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_demand_no
    ON "qixiao-apaas".t_auto_hltgq_water_demand(demand_no);
CREATE INDEX IF NOT EXISTS idx_water_demand_user
    ON "qixiao-apaas".t_auto_hltgq_water_demand(water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_demand_status
    ON "qixiao-apaas".t_auto_hltgq_water_demand(status);

-- 2.2 调度方案：编号 DD+4 位序号；执行态 待执行(#1#)→执行中(#2#)→已完成(#3#)；待执行/执行中→已取消(#4#)
--     行 = 闸口 × 时段的放水载体：计入水量 = 该闸口在 [start_time, end_time] 的实测累计流量（带符号）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_dispatch (
    id              VARCHAR(64)  PRIMARY KEY,
    dispatch_no     VARCHAR(32)  NOT NULL,          -- 编号：DD + 4 位序号（全局唯一）
    plan_id         VARCHAR(64),                    -- 关联用水计划（water_plan.id，可空；关联后受该计划该户配额约束）
    demand_id       VARCHAR(64),                    -- 关联需水申请（water_demand.id，可空；须为已通过且同户）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID
    gate_id         VARCHAR(64)  NOT NULL,          -- 调度闸口（须属于该户闸口组；计量取数锚点）
    plan_amount     NUMERIC(16,2) NOT NULL,         -- 计划放水量（m³，>0；对外展示万m³）
    start_time      TIMESTAMP    NOT NULL,          -- 执行时段起（计入水量取数窗口起点）
    end_time        TIMESTAMP    NOT NULL,          -- 执行时段止（>= start_time）
    owner           VARCHAR(50),                    -- 调度负责人
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 执行状态：#1# 待执行 / #2# 执行中 / #3# 已完成 / #4# 已取消
    bill_id         VARCHAR(64),                    -- 计费账单 ID（water_bill.id；非空=已计费，禁改禁删）
    billed_qty      NUMERIC(16,2),                  -- 出账时"计入水量"快照（m³，带符号；账单删除时清空）
    remark          VARCHAR(200),                   -- 备注
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_dispatch_no
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(dispatch_no);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_user
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(water_user_id);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_plan
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(plan_id);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_bill
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch(bill_id);

-- 2.3 调度记录：闸口操作留痕（不参与水量与费用计算）；无编号
--     方案删除时 dispatch_id 置空（记录保留，历史留痕不随方案消失）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_dispatch_record (
    id              VARCHAR(64)  PRIMARY KEY,
    dispatch_id     VARCHAR(64),                    -- 关联调度方案（water_dispatch.id，可空）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（关联方案时须与方案一致）
    gate_id         VARCHAR(64)  NOT NULL,          -- 操作闸口（关联方案时须与方案一致）
    op_time         TIMESTAMP    NOT NULL,          -- 操作时间
    op_type         VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 操作类型：#1# 闸门提升 / #2# 闸门下降 / #3# 流量调整
    height          INTEGER,                        -- 升降高度（cm，0~999；可空）
    operator        VARCHAR(50)  NOT NULL,          -- 操作人 / 闸管员
    remark          VARCHAR(200),                   -- 备注（现场情况、指令依据等）
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_record_time
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch_record(op_time);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_record_gate
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch_record(gate_id);
CREATE INDEX IF NOT EXISTS idx_water_dispatch_record_dispatch
    ON "qixiao-apaas".t_auto_hltgq_water_dispatch_record(dispatch_id);

-- 2.3.1 调度记录.方案外键置空保护：删除方案时先置空挂接（应用层事务内执行，此处仅注释说明）
--        （不建物理外键，保持与平台其它表一致的应用层维护风格）

-- 2.4 水费账单：编号 SF+期间码+'-'+户号+4 位序号；状态 已生成(#1#)→已缴清(#2#)
--     账期按用水户统计周期分期：月（2026-10）/ 季（2026-Q3）/ 年（2026）；一份账单可含多方案（bill_id 反向挂接）
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_bill (
    id              VARCHAR(64)  PRIMARY KEY,
    bill_no         VARCHAR(64)  NOT NULL,          -- 编号：SF + 期间码 + '-' + 户号 + 4 位序号（全局唯一）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（台账维度）
    settle_cycle    VARCHAR(16)  NOT NULL,          -- 统计周期快照：#1# 月度 / #2# 季度 / #3# 年度
    cycle_key       VARCHAR(16)  NOT NULL,          -- 分期 key：月 2026-10 / 季 2026-Q3 / 年 2026
    period_start    DATE         NOT NULL,          -- 账期区间起（cycle_key 推导）
    period_end      DATE         NOT NULL,          -- 账期区间止
    quantity        NUMERIC(16,2) NOT NULL,         -- 计费水量（m³，Σ方案计入水量快照，带符号；>0 才允许出账）
    price           NUMERIC(10,3) NOT NULL,         -- 水价快照（元/m³，出账时刻用水户配置）
    amount          NUMERIC(16,2) NOT NULL,         -- 应收金额（元 = quantity × price，2 位小数）
    paid            NUMERIC(16,2) NOT NULL DEFAULT 0,  -- 已收金额（元；确认收款时 = amount）
    status          VARCHAR(16)  NOT NULL DEFAULT '#1#',  -- 账单状态：#1# 已生成 / #2# 已缴清
    pay_time        TIMESTAMP,                      -- 收款时间
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_bill_no
    ON "qixiao-apaas".t_auto_hltgq_water_bill(bill_no);
CREATE INDEX IF NOT EXISTS idx_water_bill_user
    ON "qixiao-apaas".t_auto_hltgq_water_bill(water_user_id, cycle_key);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 4 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name IN ('t_auto_hltgq_water_demand', 't_auto_hltgq_water_dispatch',
--                       't_auto_hltgq_water_dispatch_record', 't_auto_hltgq_water_bill');
-- ============================================================
