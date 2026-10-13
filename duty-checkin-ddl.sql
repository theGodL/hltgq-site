-- 值班签到改造：每人独立签到（独立执行版）
-- 对应《值班交接班接口.md》§4 与《值班交接班实施方案.md》；累积初始化脚本见 src/main/resources/init.sql
-- 背景：原实现为"班维度一次性签到"——首人点击开始值班即整班进入值班中，未签到成员也被判定为值班中；
--       本改造把签到时间按人落库：签到按人幂等（首签建记录、其余补签），状态判定按人（未签到→待签到，已签到→值班中）。
-- 变更：
--   ① 记录主表 t_auto_hltgq_yn8cm_igahxz 加 leader_checkin_at：带班领导签到时间（领导不在人员关系表中，签到位落主表；null=未签到）
--   ② 人员关系表 t_auto_hltgq_yn8cm_igahxz_ahygpx_rel 加 checkin_at：值班人员各自签到时间（每人一行、按人补签；null=未签到）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 ADD COLUMN IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 修改权限的账号连接目标库后整体执行
-- 历史数据说明：改造前已在途的 #2# 记录签到列为 NULL，参与者会看到「待签到」，各自补签一次即可；
--       已交班（#3#）记录不受影响，无需回填。
-- ============================================================
BEGIN;

-- ① 记录主表：带班领导签到时间
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz"
    ADD COLUMN IF NOT EXISTS leader_checkin_at TIMESTAMP;

-- ② 人员关系表：值班人员各自签到时间
ALTER TABLE "qixiao-apaas"."t_auto_hltgq_yn8cm_igahxz_ahygpx_rel"
    ADD COLUMN IF NOT EXISTS checkin_at TIMESTAMP;

COMMIT;

-- ============================================================
-- 执行后自查（应返回 2 行）：
-- SELECT table_name, column_name FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas' AND
--        ((table_name = 't_auto_hltgq_yn8cm_igahxz' AND column_name = 'leader_checkin_at') OR
--         (table_name = 't_auto_hltgq_yn8cm_igahxz_ahygpx_rel' AND column_name = 'checkin_at'));
-- ============================================================
