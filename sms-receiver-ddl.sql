-- 短信接收人表：短信管理页（/message-rule-setting.html「短信管理」Tab）维护的接收人清单
-- 对应功能：①每日 8 点报（biz_type=daily）②晚 8 点报（biz_type=evening）③报警短信（biz_type=alarm，雨量告警/水位涨幅共用）
-- 发送取人口径（SmsReceiverService.resolvePhones）：完全以本表为准，无任何配置兜底——
--   表不存在 / 查询失败  → 该类短信不发送（error 日志）
--   某类型 0 人         → 该类短信不发送（页面删除即停发）
-- 唯一约束：同类型下同一条号码文本仅一行（单个号码是否被同类型其他行占用，由应用层拆分校验）
-- 号码存储：一行可存多个号码（英文逗号分隔、升序归一，最多 10 个），发送时拆分逐号发送
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：CREATE TABLE / INDEX IF NOT EXISTS + ALTER COLUMN 扩宽，可重复执行
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行（BEGIN/COMMIT 事务包裹）
--   ⚠ 未执行本脚本时短信不会发送（页面显示"接收人表尚未创建"，新增/编辑/删除均不可用）
-- 执行后：打开页面向每日 8 点报/晚 8 点报/报警短信三类逐个新增接收人（手机号必填，姓名选填；
--         一人多号用逗号分隔一次录入，存为一条记录），
--         或用页面「导入」批量导入 xlsx（导出文件可编辑后直接回导）
-- ============================================================
BEGIN;

CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_sms_receiver (
    id         VARCHAR(64) PRIMARY KEY,
    biz_type   VARCHAR(16) NOT NULL,          -- 短信类型：daily=每日8点报 / evening=晚8点报 / alarm=报警短信
    name       VARCHAR(64),                   -- 接收人姓名（选填，填写时最长 32 字）
    phone      VARCHAR(255) NOT NULL,         -- 手机号码：一行可存多个（英文逗号分隔，最多 10 个，发送时拆分逐号发送）
    corp_code  VARCHAR(64),
    created_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);

-- 存量表扩宽 phone（原 VARCHAR(20) 只够单号；10 号 CSV 最长 119 字符）：重复执行无副作用
ALTER TABLE "qixiao-apaas".t_auto_hltgq_water_sms_receiver
    ALTER COLUMN phone TYPE VARCHAR(255);

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
