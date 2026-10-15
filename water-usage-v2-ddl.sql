-- 灌区用水三页面全面对齐《水资源调度系统_三页面设计方案》：结构增量（独立执行版）
-- 对应《水资源调度系统_三页面设计方案》§2 数据模型 与《纪要_胡宇发起的视频会议》2026-10 要求；
-- 累积初始化脚本见 src/main/resources/init.sql（已同步更新，全新建库无需执行本文件）。
-- 背景：三页面（用水户/定额标准库/用水计划）按方案补全字段——用水户增加位置经纬度、年度计划水量、备注；
--       定额"灌溉方式"由自由文本（可空）改为字典型必填下拉；计划主表增加备注；计划明细增加"同一用水户唯一"约束。
-- 变更：
--   ① 用水户 t_auto_hltgq_water_user 加 lng / lat / annual_plan_water / remark
--   ② 用水定额 t_auto_hltgq_water_quota.irrigation_method 自由文本→字典编码 #1#~#10#，改 NOT NULL
--   ③ 用水计划主表 t_auto_hltgq_water_plan 加 remark
--   ④ 用水计划明细 t_auto_hltgq_water_plan_item 加唯一索引 uk_water_plan_item_user(plan_id, water_user_id)
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：ADD COLUMN 均带 IF NOT EXISTS；②的数据规整按字典编码过滤（已规整的行不再变更）；索引创建带 IF NOT EXISTS
-- 执行方式：以具备 "qixiao-apaas" schema 修改权限的账号连接目标库后整体执行（BEGIN/COMMIT 事务包裹）
-- 执行前核查（2026-10-14 实测）：三表均 0 行数据，②的文本映射不会触达正常行；唯一索引无存量冲突
-- ============================================================
BEGIN;

-- ① 用水户：位置经纬度（灌溉保证率点位地图）、年度计划水量（m³）、备注
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_user"
    ADD COLUMN IF NOT EXISTS lng NUMERIC(10,6),
    ADD COLUMN IF NOT EXISTS lat NUMERIC(10,6),
    ADD COLUMN IF NOT EXISTS annual_plan_water NUMERIC(16,2),
    ADD COLUMN IF NOT EXISTS remark VARCHAR(200);

-- ② 用水定额：灌溉方式统一字典编码（#1#淹灌/#2#控制灌溉/#3#畦灌/#4#沟灌/#5#喷灌/#6#滴灌/
--    #7#膜下滴灌/#8#微喷/#9#泛水补水/#10#计划直供）
-- 防御式规整：历史自由文本按名称映射为编码；无法识别/为空的行按 #1#（淹灌）兜底，可在页面编辑修正
UPDATE "qixiao-apaas"."t_auto_hltgq_water_quota"
   SET irrigation_method = CASE irrigation_method
       WHEN '淹灌'     THEN '#1#'
       WHEN '控制灌溉' THEN '#2#'
       WHEN '畦灌'     THEN '#3#'
       WHEN '沟灌'     THEN '#4#'
       WHEN '喷灌'     THEN '#5#'
       WHEN '滴灌'     THEN '#6#'
       WHEN '膜下滴灌' THEN '#7#'
       WHEN '微喷'     THEN '#8#'
       WHEN '泛水补水' THEN '#9#'
       WHEN '计划直供' THEN '#10#'
       ELSE '#1#'
     END
 WHERE irrigation_method IS NULL
    OR irrigation_method NOT IN ('#1#','#2#','#3#','#4#','#5#','#6#','#7#','#8#','#9#','#10#');

ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_quota"
    ALTER COLUMN irrigation_method TYPE VARCHAR(16),
    ALTER COLUMN irrigation_method SET NOT NULL;

-- ③ 用水计划主表：备注
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_water_plan"
    ADD COLUMN IF NOT EXISTS remark VARCHAR(200);

-- ④ 用水计划明细：同一用水户在同一计划中仅可出现一次（服务层另有前置校验，此索引兜底并发）
CREATE UNIQUE INDEX IF NOT EXISTS uk_water_plan_item_user
    ON "qixiao-apaas"."t_auto_hltgq_water_plan_item"(plan_id, water_user_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 4 行：lng / lat / annual_plan_water / remark）：
-- SELECT table_name, column_name FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_user'
--    AND column_name IN ('lng','lat','annual_plan_water','remark');
--
-- 执行后自查（irrigation_method 应为 NOT NULL）：
-- SELECT is_nullable, character_maximum_length FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_quota'
--    AND column_name = 'irrigation_method';
--
-- 执行后自查（应返回 1 行 uk_water_plan_item_user）：
-- SELECT indexname FROM pg_indexes
--  WHERE schemaname = 'qixiao-apaas' AND tablename = 't_auto_hltgq_water_plan_item'
--    AND indexname = 'uk_water_plan_item_user';
-- ============================================================
