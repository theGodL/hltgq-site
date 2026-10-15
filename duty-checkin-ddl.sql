-- 值班记录按人改造：值班记录 = 一个人的值班记录（独立执行版，2026-10 二次改造）
-- 对应《值班交接班接口.md》与《值班交接班实施方案.md》；累积初始化脚本见 src/main/resources/init.sql
-- 业务定义：每人签到各自生成一条 igahxz 记录（值班人员 ahygpx = 本人；领导记录同时 atfzxl = 本人），
--       同一班排班内 N 人签到 → N 条记录（报告各自独立互不覆盖）；一次交班 → 全班记录一并收口（同一实际交班时间）。
-- 变更（对齐平台模型：igahxz.ahygpx 单关联用户；物理列名 ahygpx，类型对齐平台单关联列 atfzxl/alidpq：varchar 无长度）：
--   ① 记录主表 t_auto_hltgq_yn8cm_igahxz 新增 ahygpx：该记录归属的值班人员（= 登录人）。
--      新代码的判定（我的在途记录）/签到幂等/报告归属/交班权限/收口范围均以本列为准。
-- 首版已废列（保留不删、新代码不再读写，兼容历史环境与旧版回滚）：
--   leader_checkin_at（主表）、checkin_at（t_..._igahxz_ahygpx_rel）——首版「班维度 + 按人补签」遗留。
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 ADD COLUMN IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 修改权限的账号连接目标库后整体执行
-- 历史数据说明：改造前在途（#2#）/已完成（#3#）记录 ahygpx 为 NULL（历史「班记录」形态，人员体现在 rel 表）。
--       新代码按人判定：历史在途记录参与者会回到「待签到」，各自签到生成自己的记录即可，无需数据回填；
--       历史记录仍可正常列表/时间轴展示（值班人员列自动回退显示 rel 人员聚合），已交班（#3#）记录不受影响。
-- ============================================================
BEGIN;

-- ① 记录主表：该记录归属的值班人员（单关联用户，同 atfzxl 形态）
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz"
    ADD COLUMN IF NOT EXISTS ahygpx varchar;

COMMIT;

-- ============================================================
-- 执行后自查（应返回 1 行）：
-- SELECT table_name, column_name, data_type FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_yn8cm_igahxz'
--    AND column_name = 'ahygpx';
-- ============================================================
