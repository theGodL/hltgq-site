-- ============================================================
-- 工单处理结果附件 · 增量 DDL（KingbaseES V8R6，schema qixiao-apaas）
--
-- 需求：工单管理 →「填写处理结果」弹窗支持上传附件（与处理结果一并保存）。
-- 背景：工单主表 t_auto_hltgq_water_work_order 无附件物理列（file / xdkvyp 均不存在），
--       参照用水计划附件模式独立建表：上传即落盘并插入一行（order_id 为空），
--       保存处理结果时按附件 id 回填 order_id；工单删除时连带清空行与文件。
-- 路径：file_path 为相对 hltgq.file-dir 的路径（work-order/yyyy-MM/uuid_原始名）。
--
-- 执行方式：连 qixiao-apaas 库执行本文件（幂等，可重复执行）。
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_work_order_attachment (
    id              VARCHAR(64)  PRIMARY KEY,
    order_id        VARCHAR(64),                    -- 所属工单（t_auto_hltgq_water_work_order.id；为空 = 已上传未绑定）
    file_name       VARCHAR(255) NOT NULL,          -- 原始文件名（下载时回填 Content-Disposition）
    file_path       VARCHAR(500) NOT NULL,          -- 相对存储路径（work-order/yyyy-MM/uuid_原名）
    file_size       BIGINT,                         -- 文件字节数
    file_ext        VARCHAR(16),                    -- 扩展名（小写，不含点）
    corp_code       VARCHAR(64),
    created_at      TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_work_order_attach_order
    ON "qixiao-apaas".t_auto_hltgq_water_work_order_attachment(order_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 11 行；file_name / file_path 的 is_nullable 应为 NO）：
-- SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name = 't_auto_hltgq_water_work_order_attachment'
--  ORDER BY ordinal_position;
--
-- 索引自查（应返回 1 行 idx_work_order_attach_order）：
-- SELECT indexname FROM pg_indexes
--  WHERE schemaname = 'qixiao-apaas'
--    AND tablename = 't_auto_hltgq_water_work_order_attachment';
-- ============================================================
