-- ============================================================
-- 水费 · 手工用水台账建表脚本（独立执行版）
-- 对应页面：水费台账（water-bill）视图一「用水台账」
-- 累积初始化脚本见 src/main/resources/init.sql（同内容已追加：2.4 补列 / 2.5 新表）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- 说明：① 台账行 = 用水户 × 结算周期 × 用水量 × 单价，金额 = 用水量 × 单价（应用层计算，2 位小数）；
--          水量库内 m³（NUMERIC 2 位小数），单价元/m³（NUMERIC 3 位小数），金额元（2 位小数）；
--       ② 编号：TZ + 期间码 + '-' + 户号 + 4 位全局序号（如 TZ202610-YH0010001），
--          应用层 MAX(right(ledger_no,4))+1 取号，唯一索引 uk_water_ledger_no 兜底并发；
--          编号在台账创建时固化，编辑不重编号（财务凭证习惯）；
--       ③ 出账关系：1 台账 : 1 账单（台账 bill_id 正向挂接、账单 ledger_id 反向留痕）；
--          出账后台账禁改禁删，账单删除时清空 bill_id、台账恢复未计费可重新出账；
--       ④ 结算周期字典与账单一致：#1# 月度 / #2# 季度 / #3# 年度，
--          期间 key：月 2026-10 / 季 2026-Q3 / 年 2026（与账单 cycle_key 同口径）。
-- ============================================================
BEGIN;

-- 1. 账单表补列：来源手工台账 ID（方案出账为空；存量环境 CREATE TABLE IF NOT EXISTS 不会改已有表，需显式 ADD COLUMN）
ALTER TABLE "qixiao-apaas".t_auto_hltgq_water_bill
    ADD COLUMN IF NOT EXISTS ledger_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_water_bill_ledger
    ON "qixiao-apaas".t_auto_hltgq_water_bill(ledger_id);

-- 2. 手工水费台账
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_ledger (
    id              VARCHAR(64)  PRIMARY KEY,
    ledger_no       VARCHAR(64)  NOT NULL,          -- 编号：TZ + 期间码 + '-' + 户号 + 4 位序号（全局唯一，创建后不变）
    water_user_id   VARCHAR(64)  NOT NULL,          -- 用水户 ID（water_user.id）
    settle_cycle    VARCHAR(16)  NOT NULL,          -- 统计周期快照：#1# 月度 / #2# 季度 / #3# 年度
    cycle_key       VARCHAR(16)  NOT NULL,          -- 结算周期：月 2026-10 / 季 2026-Q3 / 年 2026
    volume          NUMERIC(16,2) NOT NULL,         -- 用水量（m³，>0）
    price           NUMERIC(16,3) NOT NULL,         -- 单价（元/m³，>0）
    amount          NUMERIC(16,2) NOT NULL,         -- 应收金额（元 = volume × price，2 位小数）
    bill_id         VARCHAR(64),                    -- 出账后账单 ID（water_bill.id；非空=已计费，禁改禁删）
    remark          VARCHAR(200),                   -- 备注
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_ledger_no
    ON "qixiao-apaas".t_auto_hltgq_water_ledger(ledger_no);
CREATE INDEX IF NOT EXISTS idx_water_ledger_user
    ON "qixiao-apaas".t_auto_hltgq_water_ledger(water_user_id, cycle_key);
CREATE INDEX IF NOT EXISTS idx_water_ledger_bill
    ON "qixiao-apaas".t_auto_hltgq_water_ledger(bill_id);

COMMIT;

-- ============================================================
-- 执行后自查：
-- ① 手工台账表存在（应返回 1 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_ledger';
-- ② 账单表 ledger_id 列存在（应返回 1 行）：
-- SELECT column_name FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_bill'
--    AND column_name = 'ledger_id';
-- ③ 索引齐全（应返回 uk_water_ledger_no / idx_water_ledger_user / idx_water_ledger_bill / idx_water_bill_ledger）：
-- SELECT indexname FROM pg_indexes
--  WHERE schemaname = 'qixiao-apaas'
--    AND indexname IN ('uk_water_ledger_no', 'idx_water_ledger_user',
--                      'idx_water_ledger_bill', 'idx_water_bill_ledger');
-- ============================================================
