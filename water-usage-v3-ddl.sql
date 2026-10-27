-- ============================================================
-- 灌区用水管理 · 调度指令 / 调度记录 / 需水旬维度 / 计划附件（独立执行版）
-- 累积初始化脚本见 src/main/resources/init.sql（同内容已同步）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：ADD COLUMN 带 IF NOT EXISTS；DROP NOT NULL 先查 information_schema 再改；
--         建表/索引均 IF NOT EXISTS；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 修改权限的账号连接目标库后整体执行
-- ------------------------------------------------------------
-- 变更内容：
--   ① 调度方案更名「调度指令」：
--      · 新增 exec_time（执行时间 = 开闸时刻，计入水量取数窗口起点）
--      · 新增 finish_time（完成时间 = 关闸时刻，状态改为已完成时写入；为空取当前时间）
--      · 新增 gate_height_cm（指定闸门高度，cm，0~999）
--      · water_user_id / plan_amount / start_time / end_time 放开 NOT NULL
--        （用水户、计划放水量改为选填；开始/结束时间不再录入，由 exec_time/finish_time 取代）
--      · 旧数据 exec_time / finish_time 保持 NULL：计量窗口仍取 [start_time, end_time]，行为不变
--   ② 调度记录：新增 gate_height_after（操作后闸门高度，cm，0~999）；
--      op_type / height 保留列以兼容历史数据，新数据不再写入
--   ③ 需水上报：新增 period_year / period_month / xun_list（旬多选，逗号分隔 1上旬/2中旬/3下旬）；
--      start_date / end_date 由所选年月 + 旬自动派生后写入（保持原字段语义不变）
--   ④ 用水计划附件：新增 t_auto_hltgq_water_plan_attachment（先上传落盘、保存计划时绑定 plan_id）
-- ============================================================
BEGIN;

-- ① 调度指令：执行时间 / 完成时间 / 闸门高度
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_dispatch"
    ADD COLUMN IF NOT EXISTS exec_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS finish_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS gate_height_cm INTEGER;

COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_dispatch".exec_time IS '执行时间（开闸时刻；计入水量取数窗口起点）';
COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_dispatch".finish_time IS '完成时间（关闸时刻；状态改为已完成时写入，为空取当前时间）';
COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_dispatch".gate_height_cm IS '指定闸门高度（cm，0~999）';

-- 放开必填（幂等：仅当当前为 NOT NULL 时执行）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_dispatch'
                  AND column_name = 'water_user_id' AND is_nullable = 'NO') THEN
        ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_dispatch" ALTER COLUMN water_user_id DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_dispatch'
                  AND column_name = 'plan_amount' AND is_nullable = 'NO') THEN
        ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_dispatch" ALTER COLUMN plan_amount DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_dispatch'
                  AND column_name = 'start_time' AND is_nullable = 'NO') THEN
        ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_dispatch" ALTER COLUMN start_time DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_dispatch'
                  AND column_name = 'end_time' AND is_nullable = 'NO') THEN
        ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_dispatch" ALTER COLUMN end_time DROP NOT NULL;
    END IF;
END $$;

-- 执行时间建索引（列表按执行时间排序 / 账期归属推导）
CREATE INDEX IF NOT EXISTS idx_water_dispatch_exec
    ON "qixiao-apaas"."t_auto_hltgq_water_dispatch"(exec_time);

-- ② 调度记录：操作后闸门高度
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_dispatch_record"
    ADD COLUMN IF NOT EXISTS gate_height_after INTEGER;

COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_dispatch_record".gate_height_after IS '操作后闸门高度（cm，0~999）';

-- ③ 需水上报：年月 + 旬多选
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_demand"
    ADD COLUMN IF NOT EXISTS period_year SMALLINT,
    ADD COLUMN IF NOT EXISTS period_month SMALLINT,
    ADD COLUMN IF NOT EXISTS xun_list VARCHAR(32);

COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_demand".period_year IS '用水年份（如 2026；旧数据为空）';
COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_demand".period_month IS '用水月份（1~12；旧数据为空）';
COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_water_demand".xun_list IS '旬多选（逗号分隔：1上旬/2中旬/3下旬，如 1,3；旧数据为空）';

-- ④ 用水计划附件
-- 上传即落盘并插入一行（plan_id 为空），保存计划时按附件 id 回填 plan_id；
-- 计划删除时先删文件再删行（Service 事务内执行）；file_path 为相对 hltgq.file-dir 的路径。
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_plan_attachment (
    id              VARCHAR(64)  PRIMARY KEY,
    plan_id         VARCHAR(64),                    -- 所属用水计划（water_plan.id；为空 = 已上传未绑定）
    file_name       VARCHAR(255) NOT NULL,          -- 原始文件名（下载时回填 Content-Disposition）
    file_path       VARCHAR(500) NOT NULL,          -- 相对存储路径（plan/yyyy-MM/uuid_原名）
    file_size       BIGINT,                         -- 文件字节数
    file_ext        VARCHAR(16),                    -- 扩展名（小写，不含点）
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_water_plan_attach_plan
    ON "qixiao-apaas".t_auto_hltgq_water_plan_attachment(plan_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 4 行，且 nullable 全为 YES）：
-- SELECT table_name, column_name, is_nullable FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_dispatch'
--    AND column_name IN ('exec_time','finish_time','gate_height_cm','water_user_id','plan_amount','start_time','end_time')
--  ORDER BY column_name;
-- ============================================================
