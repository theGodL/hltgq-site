-- ============================================================
-- 物资目录「类别」字段（库存管理 / 应急物资管理共用）
-- 表：t_auto_hltgq_knc3g_slaifi（物资目录，低代码模型）
-- 编码：#1# 常用物资 / #2# 应急物资
--
-- 说明：
--   1) 若已在低代码平台「物资目录」模型新增字段（编码 type），平台会自动建列，
--      本脚本无需执行；
--   2) 页面与接口按 type 为空即「常用物资」处理，历史 21 条数据无需回填；
--   3) 需回填历史数据时，自行取消最后一段 UPDATE 的注释后执行。
-- ============================================================

ALTER TABLE "qixiao-apaas"."t_auto_hltgq_knc3g_slaifi"
    ADD COLUMN IF NOT EXISTS "type" varchar(64);

COMMENT ON COLUMN "qixiao-apaas"."t_auto_hltgq_knc3g_slaifi"."type"
    IS '物资类别（#1# 常用物资 / #2# 应急物资）';

-- 可选：历史空值回填为常用物资（不执行也按常用物资处理）
-- UPDATE "qixiao-apaas"."t_auto_hltgq_knc3g_slaifi"
--    SET "type" = '#1#'
--  WHERE "type" IS NULL OR TRIM("type") = '';
