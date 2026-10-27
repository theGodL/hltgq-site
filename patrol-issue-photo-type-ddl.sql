-- ============================================================
-- 巡检问题照片表补列：photo_type（1 现场照片 / 2 处理结果附件）
-- 对应接口：POST /patrol-issue/result、GET /patrol-issue/photos?photoType=
-- 适用数据库：KingbaseES v8（PostgreSQL 语法），schema：qixiao-apaas
-- 幂等性：ADD COLUMN IF NOT EXISTS + 存量回填 WHERE photo_type IS NULL，可重复执行
-- 执行方式：以具备 "qixiao-apaas" schema 改表权限的账号连接目标库后整体执行
-- ============================================================
BEGIN;

-- 上传时该列为空（用途未定），由绑定动作写入：
--   1 = 现场照片（/patrol-issue/create 绑定）
--   2 = 处理结果附件（/patrol-issue/result 绑定）
ALTER TABLE "qixiao-apaas".t_auto_hltgq_water_patrol_issue_photo
    ADD COLUMN IF NOT EXISTS photo_type VARCHAR(8);

-- 已绑定的存量数据全部产生于“现场照片”阶段，统一回填 1；
-- 上传后未绑定（issue_id 为空）的行保持 NULL，由后续绑定动作写入 1 或 2
UPDATE "qixiao-apaas".t_auto_hltgq_water_patrol_issue_photo
   SET photo_type = '1'
 WHERE photo_type IS NULL
   AND issue_id IS NOT NULL;

-- 详情页按问题 + 类型分区查询
CREATE INDEX IF NOT EXISTS idx_patrol_issue_photo_issue_type
    ON "qixiao-apaas".t_auto_hltgq_water_patrol_issue_photo(issue_id, photo_type);

COMMIT;

-- ============================================================
-- 执行后自查：
-- SELECT photo_type, COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_patrol_issue_photo
--  GROUP BY photo_type;
-- ============================================================
