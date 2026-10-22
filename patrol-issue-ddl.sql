-- ============================================================
-- 巡检问题（现场照片）建表脚本（独立执行版）
-- 对应《新增巡检问题》页面与接口 /patrol-issue/*
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：IF NOT EXISTS，可重复执行；DDL 由事务包裹，中途失败自动回滚
-- 执行方式：以具备 "qixiao-apaas" schema 建表权限的账号连接目标库后整体执行
-- ============================================================
BEGIN;

-- 巡检问题现场照片：上传时 issue_id 为空（仅 created_by 归属），提交问题时回填并写排序；
-- 文件落盘 file-dir/patrol-issue/{yyyy-MM}/，移除时物理删文件 + 删行。
-- 说明：问题主表为低代码表 t_auto_hltgq_knc3g_bpzjoh，本表只承载自建页面上传的照片，
--      不写平台 jmltds 关系表（平台文件服务上传契约未接入，详见 H5接口.md）。
CREATE TABLE IF NOT EXISTS "qixiao-apaas".t_auto_hltgq_water_patrol_issue_photo (
    id           VARCHAR(64)  PRIMARY KEY,
    issue_id     VARCHAR(64),                       -- 问题记录 id（上传时为空，提交时回填）
    file_name    VARCHAR(255) NOT NULL,             -- 原始文件名（展示/下载用）
    file_path    VARCHAR(500) NOT NULL,             -- 相对 file-dir 的存储路径（patrol-issue/yyyy-MM/uuid_原名）
    file_size    BIGINT,                            -- 文件大小（字节）
    file_ext     VARCHAR(16),                       -- 扩展名（小写，Content-Type 判别）
    nature_order INTEGER,                           -- 展示顺序（1 起，提交时按上传顺序写入）
    corp_code    VARCHAR(64),
    created_at   TIMESTAMP,
    created_by   VARCHAR(64),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_patrol_issue_photo_issue
    ON "qixiao-apaas".t_auto_hltgq_water_patrol_issue_photo(issue_id);

COMMIT;

-- ============================================================
-- 执行后自查（应返回 1 行）：
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'qixiao-apaas'
--    AND table_name = 't_auto_hltgq_water_patrol_issue_photo';
-- ============================================================
