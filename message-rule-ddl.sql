-- 消息发送设置：消息接收规则表增加站点范围列（独立执行版）
-- 对应《消息发送设置.md》§1 字段口径；
-- 累积初始化脚本见 src/main/resources/init.sql（已同步更新，全新建库无需执行本文件）。
-- 背景（线上只读探针核对 2026-10）：
--   t_auto_hltgq_water_message_rule 现有列 message_type/target_type/target_id 只能表达
--   "某类消息发给哪些部门/岗位/角色/人员"，无法表达"告警只发给某些站点"，故增加 site_id 列。
-- 语义约定：
--   ① site_id 仅对告警（message_type=#1#）生效，其他类型写入时服务层归一化为空；
--   ② site_id 为空 = 不限站点（存量规则 #1#/#user#/两个登录名 保持原语义：全部站点）；
--   ③ 多个站点以英文逗号分隔（与 target_id 同规约）；命中多条规则时合并站点范围，
--      任一条命中规则 site_id 为空 = 不限站点。
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：ALTER TABLE ... ADD COLUMN IF NOT EXISTS，可重复执行；存量规则 site_id 自动为 NULL（= 不限站点）
-- 执行方式：以具备 "qixiao-apaas" schema 修改权限的账号连接目标库后整体执行（BEGIN/COMMIT 事务包裹）
-- ============================================================
BEGIN;

ALTER TABLE "qixiao-apaas".t_auto_hltgq_water_message_rule
    ADD COLUMN IF NOT EXISTS site_id VARCHAR;   -- 站点范围（仅告警：#1# 生效；站点档案主键，多个逗号分隔；空=不限站点）
-- 注：与 target_id 同为无长度 varchar（线上实测），205 个站点全选约 4100 字符不超限。

COMMENT ON COLUMN "qixiao-apaas".t_auto_hltgq_water_message_rule.site_id IS
    '站点范围（仅告警消息类型生效）：站点档案主键（t_auto_hltgq_5nw74_vnqqef.id），多个以英文逗号分隔；为空=不限站点';

COMMIT;

-- ============================================================
-- 执行后自查（应返回 1 行：site_id / character varying）：
-- SELECT column_name, data_type FROM information_schema.columns
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name = 't_auto_hltgq_water_message_rule'
--    AND column_name = 'site_id';
--
-- 存量数据核对（应为 1 行，site_id 为 NULL = 不限站点）：
-- SELECT id, message_type, target_type, site_id FROM "qixiao-apaas".t_auto_hltgq_water_message_rule;
-- ============================================================
