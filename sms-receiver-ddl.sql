-- 短信接收人表：短信管理页（/message-rule-setting.html「短信管理」Tab）维护的接收人清单
-- 对应功能：①每日 8 点报（biz_type=daily）②报警短信（biz_type=alarm，雨量告警/水位涨幅共用）
-- 发送取人口径（SmsReceiverService.resolvePhones）：完全以本表为准，无任何配置兜底——
--   表不存在 / 查询失败  → 该类短信不发送（error 日志）
--   某类型 0 人         → 该类短信不发送（页面删除即停发）
-- 唯一约束：同类型同手机号仅一条（同一号码可同时加入两类，互不影响）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：CREATE TABLE / INDEX IF NOT EXISTS，可重复执行
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行（BEGIN/COMMIT 事务包裹）
--   ⚠ 未执行本脚本时短信不会发送（页面显示"接收人表尚未创建"，新增/编辑/删除均不可用）
-- 执行后：打开页面向日 8 点报/报警短信两类逐个新增接收人（姓名 + 手机号），
--         或用页面「导入」批量导入 xlsx（导出文件可编辑后直接回导）
-- ============================================================
BEGIN;

CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_sms_receiver (
    id         VARCHAR(64) PRIMARY KEY,
    biz_type   VARCHAR(16) NOT NULL,          -- 短信类型：daily=每日8点报 / alarm=报警短信
    name       VARCHAR(64),                   -- 接收人姓名（应用层必填，最长 32 字）
    phone      VARCHAR(20) NOT NULL,          -- 手机号码（11 位，应用层校验）
    corp_code  VARCHAR(64),
    created_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sms_receiver_biz_phone
    ON "qixiao-apaas".t_auto_hltgq_water_sms_receiver(biz_type, phone);

COMMIT;

-- ============================================================
-- 执行前自查（两条均应返回 0；非 0 说明表或索引已存在，先进库核对结构再执行）：
-- SELECT count(*) FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_sms_receiver';
-- SELECT count(*) FROM pg_indexes
--  WHERE schemaname = 'qixiao-apaas' AND indexname = 'uk_sms_receiver_biz_phone';
-- ============================================================
-- 执行后自查（应返回 1 行）：
-- SELECT count(*) FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_sms_receiver';
-- ============================================================
