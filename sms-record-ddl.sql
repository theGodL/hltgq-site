-- 短信发送记录表（2026-11，短信接入：日汇总 + 阈值告警）
-- 用途：每次短信发送（成功/失败/静默跳过）逐行落记录，用于
--   ① 告警回调幂等：同一 alert_id 只处理一次（success/skipped 均算已处理，countHandledByAlertId）
--   ② 同站点同指标静默：silence_key + sent_at 判定 30 分钟内不重发
--   ③ 运维排查：模板参数、阿里云返回码、BizId 全量留痕
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：CREATE TABLE/INDEX IF NOT EXISTS，可重复执行
-- ============================================================
BEGIN;

CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_sms_record (
    id                VARCHAR(64)  PRIMARY KEY,
    biz_type          VARCHAR(32)  NOT NULL,   -- daily=日汇总 / alarm_rain=雨量告警 / alarm_level=水位告警 / test=测试
    alert_id          VARCHAR(64),             -- 告警表 t_auto_hltgq_water_alert.id（告警类，日汇总为空）
    silence_key       VARCHAR(64),             -- 静默键 rain:{stcd} / level:{stcd}（同站点同指标 30 分钟静默）
    phone             VARCHAR(32)  NOT NULL,   -- 接收手机号
    template_code     VARCHAR(64),             -- 模板编码（SMS_xxx）
    template_params   TEXT,                    -- 模板变量 JSON
    send_status       VARCHAR(16),             -- success / failed / skipped（静默跳过，补偿按已处理跳过）
    request_id        VARCHAR(128),            -- 阿里云 RequestId
    biz_id            VARCHAR(64),             -- 阿里云 BizId（发送回执，查送达用）
    response_code     VARCHAR(64),             -- 阿里云返回 Code（OK / isv.xxx）
    response_message  VARCHAR(500),            -- 阿里云返回 Message
    sent_at           TIMESTAMP,               -- 发送时间
    corp_code         VARCHAR(64),
    created_at        TIMESTAMP,
    created_by        VARCHAR(64),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(64)
);

-- 存量环境补列（可重复执行；已按旧版建过表的环境补 biz_id）
ALTER TABLE "qixiao-apaas".t_auto_hltgq_water_sms_record
    ADD COLUMN IF NOT EXISTS biz_id VARCHAR(64);

-- 告警幂等查询（按 alert_id 判"是否已处理"：success/skipped 均算已处理）
CREATE INDEX IF NOT EXISTS idx_sms_record_alert
    ON "qixiao-apaas".t_auto_hltgq_water_sms_record (alert_id);

-- 静默窗口查询（silence_key + sent_at >= ?）
CREATE INDEX IF NOT EXISTS idx_sms_record_silence
    ON "qixiao-apaas".t_auto_hltgq_water_sms_record (silence_key, sent_at);

-- 记录分页/运维排查（按号码、时间）
CREATE INDEX IF NOT EXISTS idx_sms_record_phone_time
    ON "qixiao-apaas".t_auto_hltgq_water_sms_record (phone, created_at);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 1 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas' AND table_name = 't_auto_hltgq_water_sms_record';
-- ============================================================
