-- ============================================================
-- 应急保障附件建表脚本（独立执行版）
-- 应急保障主表 t_auto_hltgq_bcmxdv_ifjcmy 由低代码平台创建（已有），本脚本只补自研页所需附件表
-- 累积初始化脚本见 src/main/resources/init.sql（第 8 节）
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：全部 IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- ============================================================
BEGIN;

-- 应急保障附件：上传时 support_id 为空，保存/编辑时回填；
-- 文件落盘 file-dir/emergency/{yyyy-MM}/，编辑移除/整条删除时提交后物理删文件
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_emergency_attachment (
    id         VARCHAR(64)  PRIMARY KEY,
    support_id VARCHAR(64),                       -- 应急保障记录 ID（ifjcmy.id，上传时为空）
    file_name  VARCHAR(255) NOT NULL,             -- 原始文件名（下载展示用）
    file_path  VARCHAR(500) NOT NULL,             -- 相对 file-dir 的存储路径（emergency/yyyy-MM/uuid_原名）
    file_size  BIGINT,                            -- 文件大小（字节）
    file_ext   VARCHAR(16),                       -- 扩展名（小写，下载 Content-Type 判别）
    corp_code  VARCHAR(64),
    created_at TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_emergency_attachment_support
    ON "qixiao-apaas".t_auto_hltgq_water_emergency_attachment(support_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 1 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name IN ('t_auto_hltgq_water_emergency_attachment');
-- 说明：附件为可选增强。未执行本脚本时，应急保障列表/新增/编辑/删除照常可用（附件列按空处理，
--       上传会提示先执行本脚本）；执行后服务自动探测到表存在，无需重启即恢复附件能力。
-- ============================================================
