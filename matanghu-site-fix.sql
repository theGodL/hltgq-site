-- ============================================================================
-- 麻塘湖灌区站点档案修正（渠系含父子结构）
-- 依据：E:\文件\启效\3.0\客户项目\中水三立-花凉亭\设备接入\麻塘湖站点 - 副本.xlsx
-- 目标库：qixiao-apaas（KingbaseES v8 / PG 兼容），schema 带连字符需双引号
--
-- 渠系结构（18 个节点）：
--   麻塘湖灌区 grTcKYo1RogxZCVkd5H
--   └── 总干渠                 8000000016 怀宁分干渠进水闸、8000000017 望江分干渠进水闸
--       ├── 越级支渠
--       │   ├── 东方红支渠     8000000004
--       │   ├── 胜利支渠       8000000005
--       │   ├── 五圩支渠       8000000006
--       │   ├── 长青支渠       8000000007
--       │   ├── 万全支渠       8000000008
--       │   ├── 庆洲支渠       8000000009
--       │   ├── 新光支渠       8000000014
--       │   └── 中心支渠       8000000015
--       ├── 怀宁分干渠         8000000002 水质站
--       │   ├── 易潭支渠       8000000010
--       │   ├── 彭西进水闸     8000000011
--       │   ├── 同兴支渠       8000000012
--       │   ├── 周上墩支渠     8000000018
--       │   └── 九朋支渠       8000000019
--       └── 望江分干渠         8000000003 水质站、8000000020 张屋（直接挂本节点）
--           └── 高排沟支渠     8000000013
--
-- 父子依据：两个分干渠进水闸的 Excel 渠系均为「总干渠」（分干渠从总干渠分水）；
--           水质站以「怀宁分干渠」「望江分干渠」命名（两个县级分灌区）；
--           各支渠坐标均落在所属分干渠进水闸下游 0.3~3.2km 沿线；
--           越级 8 条位于水库与总干渠分叉口之间，属总干渠首段分水口。
--
-- 内容：
--   零、执行前校验（只读）
--   一、新增 18 个渠系节点
--   二、19 个站点改挂渠系（17 个 Excel 指定渠系 + 2 个水质站按同名分干渠）
--       其余站点保持挂根节点：水质 8000000001、雨量 6 条（Excel 渠系为「无」）、
--       19 条独立视频记录、墒情 9000000135，合计 27 条，属预期不改
--   三、8000000005 补经纬度（Excel：116.631488E / 30.406190N）
-- 不改动：站点名称（视频点位按名称匹配，个别字差异保持原名，避免匹配歧义）
--   8000000005 朱山咀进水闸 / 8000000013 高排沟进水闸 / 8000000020 张屋 / 8000000021 望江分干渠
--
-- 重要：本版取代上一版「16 个平铺节点」方案。若上一版已执行，
--       请先执行文件末尾回滚段，再执行本脚本；第 0.2 节查询可判断是否有旧版残留。
--
-- 执行要求：
--   1. 执行前备份两张表（可用第 0.3 节快照语句，或客户端自带备份）
--   2. 必须整段连续执行，不要逐条执行：BEGIN/COMMIT 只对同一会话内连续执行有效
--   3. 幂等：渠系按固定 id 判重、站点按站码定位，可重复执行
--   4. 执行后无需重启服务（渠系树每次实时查库，无缓存）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 零、执行前校验（只读，建议先单独跑一遍，有问题先处理再往下）
-- ----------------------------------------------------------------------------
-- 0.1 19 个站码必须各存在且唯一：应返回 0 行
SELECT iofhpi, COUNT(*) AS cnt
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
 WHERE iofhpi IN ('8000000002','8000000003',
                  '8000000004','8000000005','8000000006','8000000007','8000000008',
                  '8000000009','8000000010','8000000011','8000000012','8000000013',
                  '8000000014','8000000015','8000000016','8000000017','8000000018',
                  '8000000019','8000000020')
 GROUP BY iofhpi
HAVING COUNT(*) <> 1;

-- 0.2 旧版残留检查：应返回 0 行；若返回 16 行（且 001 名为「越级支渠东方红支渠」），
--     说明上一版平铺方案已执行，先跑文件末尾回滚段再继续
SELECT id, gfaegg, cyjihq
  FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw"
 WHERE id LIKE 'MthCanal%'
 ORDER BY id;

-- 0.3 备份快照（需要留底时去掉注释执行）
-- CREATE TABLE "qixiao-apaas".bak_mth_canal_20261118 AS
-- SELECT * FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw";
-- CREATE TABLE "qixiao-apaas".bak_mth_site_20261118 AS
-- SELECT id, iofhpi, zzkaec, ywvyds, bviiio_x, bviiio_y, bviiio_geohash
--   FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef";

BEGIN;

-- ----------------------------------------------------------------------------
-- 一、新增 18 个渠系节点
-- 层级：总干渠(001) → 越级支渠(002) / 怀宁分干渠(011) / 望江分干渠(017) → 各支渠(003~010、012~016、018)
-- 渠系树同级按 created_at 倒序展示，序号越小给越晚的创建时间，页面顺序即 id 顺序
-- 若平台侧对 cyjihq 有存在性校验触发器，可拆两次执行：先 001/002/011/017，再其余 14 条
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw"
       (id, corp_code, created_at, created_by, updated_at, updated_by, gfaegg, cyjihq)
SELECT v.id, 'hltgq',
       NOW() - (v.seq - 1) * INTERVAL '1 second', 'SYSTEM',
       NOW() - (v.seq - 1) * INTERVAL '1 second', 'SYSTEM',
       v.name, v.pid
  FROM (VALUES
        ('MthCanal000000000001', '总干渠',       'grTcKYo1RogxZCVkd5H', 1),
        ('MthCanal000000000002', '越级支渠',     'MthCanal000000000001', 2),
        ('MthCanal000000000003', '东方红支渠',   'MthCanal000000000002', 3),
        ('MthCanal000000000004', '胜利支渠',     'MthCanal000000000002', 4),
        ('MthCanal000000000005', '五圩支渠',     'MthCanal000000000002', 5),
        ('MthCanal000000000006', '长青支渠',     'MthCanal000000000002', 6),
        ('MthCanal000000000007', '万全支渠',     'MthCanal000000000002', 7),
        ('MthCanal000000000008', '庆洲支渠',     'MthCanal000000000002', 8),
        ('MthCanal000000000009', '新光支渠',     'MthCanal000000000002', 9),
        ('MthCanal000000000010', '中心支渠',     'MthCanal000000000002', 10),
        ('MthCanal000000000011', '怀宁分干渠',   'MthCanal000000000001', 11),
        ('MthCanal000000000012', '易潭支渠',     'MthCanal000000000011', 12),
        ('MthCanal000000000013', '彭西进水闸',   'MthCanal000000000011', 13),
        ('MthCanal000000000014', '同兴支渠',     'MthCanal000000000011', 14),
        ('MthCanal000000000015', '周上墩支渠',   'MthCanal000000000011', 15),
        ('MthCanal000000000016', '九朋支渠',     'MthCanal000000000011', 16),
        ('MthCanal000000000017', '望江分干渠',   'MthCanal000000000001', 17),
        ('MthCanal000000000018', '高排沟支渠',   'MthCanal000000000017', 18)
       ) AS v(id, name, pid, seq)
 WHERE NOT EXISTS (
        SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw" c WHERE c.id = v.id
       );

-- ----------------------------------------------------------------------------
-- 二、站点改挂渠系
-- 2.1 总干渠上的两座分干渠进水闸
-- ----------------------------------------------------------------------------
-- 8000000016 怀宁分干渠进水闸 -> 总干渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000001', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000016';

-- 8000000017 望江分干渠进水闸 -> 总干渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000001', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000017';

-- ----------------------------------------------------------------------------
-- 2.2 越级支渠下的 8 条支渠
-- ----------------------------------------------------------------------------
-- 8000000004 东方红进水闸 -> 越级支渠 / 东方红支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000003', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000004';

-- 8000000005 朱山咀进水闸 -> 越级支渠 / 胜利支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000004', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000005';

-- 8000000006 五圩支渠进水闸 -> 越级支渠 / 五圩支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000005', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000006';

-- 8000000007 长青支渠 -> 越级支渠 / 长青支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000006', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000007';

-- 8000000008 万全支渠 -> 越级支渠 / 万全支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000007', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000008';

-- 8000000009 庆洲支渠 -> 越级支渠 / 庆洲支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000008', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000009';

-- 8000000014 新光支渠 -> 越级支渠 / 新光支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000009', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000014';

-- 8000000015 中心支渠 -> 越级支渠 / 中心支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000010', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000015';

-- ----------------------------------------------------------------------------
-- 2.3 怀宁分干渠下的 5 条支渠/进水闸
-- ----------------------------------------------------------------------------
-- 8000000010 易潭支渠 -> 怀宁分干渠 / 易潭支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000012', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000010';

-- 8000000011 彭西进水闸 -> 怀宁分干渠 / 彭西进水闸
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000013', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000011';

-- 8000000012 同兴支渠 -> 怀宁分干渠 / 同兴支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000014', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000012';

-- 8000000018 周上墩支渠 -> 怀宁分干渠 / 周上墩支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000015', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000018';

-- 8000000019 九朋支渠 -> 怀宁分干渠 / 九朋支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000016', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000019';

-- ----------------------------------------------------------------------------
-- 2.4 望江分干渠及其下级
-- ----------------------------------------------------------------------------
-- 8000000020 张屋 -> 望江分干渠（Excel 渠系即望江分干渠，不单独建张屋节点）
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000017', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000020';

-- 8000000013 高排沟进水闸 -> 望江分干渠 / 高排沟支渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000018', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000013';

-- ----------------------------------------------------------------------------
-- 2.5 水质站按同名分干渠挂载（Excel 水质表未填渠系，不需要可删去本节）
-- ----------------------------------------------------------------------------
-- 8000000002 怀宁分干渠 -> 怀宁分干渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000011', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000002';

-- 8000000003 望江分干渠 -> 望江分干渠
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'MthCanal000000000017', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000003';

-- ----------------------------------------------------------------------------
-- 三、8000000005 补经纬度
-- geohash 取自同坐标视频记录 MthSite0000000000004（朱山咀进水渠），
-- 若平台侧会自动重算可去掉 bviiio_geohash 赋值
-- ----------------------------------------------------------------------------
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET bviiio_x = 116.631488,
       bviiio_y = 30.406190,
       bviiio_geohash = 'wt6uyp22',
       updated_at = NOW(),
       updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000005';

-- ----------------------------------------------------------------------------
-- 四、执行后校验（只读，确认无误再 COMMIT）
-- ----------------------------------------------------------------------------
-- 1) 渠系树：应为 18 条，层级 1~3（lvl 相对总干渠，实际路径前面还有麻塘湖灌区）
-- 两处平台差异（均已实测）：
--   a. lvl 两侧必须显式 CAST 成 integer：KingbaseES 会把 integer 算术提升为 bigint，
--      否则报 "recursive query column has type integer in non-recursive term but bigint overall"
--   b. 字符串拼接必须用 concat()，不能用 || ：本库 || 被解析成逻辑或，
--      'ab' || 'cd' 会返回 boolean f（即使两侧 CAST 成 text 也一样）
WITH RECURSIVE t(id, name, pid, lvl, path) AS (
    SELECT id, gfaegg, cyjihq, CAST(1 AS integer), CAST(gfaegg AS varchar(500))
      FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw"
     WHERE id = 'MthCanal000000000001'
    UNION ALL
    SELECT c.id, c.gfaegg, c.cyjihq, CAST(t.lvl + 1 AS integer),
           CAST(concat(t.path, ' / ', c.gfaegg) AS varchar(500))
      FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw" c
      JOIN t ON c.cyjihq = t.id
)
SELECT lvl, path FROM t ORDER BY id;

-- 2) 节点结构与期望逐条比对：应返回 0 行（有差异会列出期望值与实际值）
WITH expect(id, name, pid) AS (VALUES
        ('MthCanal000000000001', '总干渠',     'grTcKYo1RogxZCVkd5H'),
        ('MthCanal000000000002', '越级支渠',   'MthCanal000000000001'),
        ('MthCanal000000000003', '东方红支渠', 'MthCanal000000000002'),
        ('MthCanal000000000004', '胜利支渠',   'MthCanal000000000002'),
        ('MthCanal000000000005', '五圩支渠',   'MthCanal000000000002'),
        ('MthCanal000000000006', '长青支渠',   'MthCanal000000000002'),
        ('MthCanal000000000007', '万全支渠',   'MthCanal000000000002'),
        ('MthCanal000000000008', '庆洲支渠',   'MthCanal000000000002'),
        ('MthCanal000000000009', '新光支渠',   'MthCanal000000000002'),
        ('MthCanal000000000010', '中心支渠',   'MthCanal000000000002'),
        ('MthCanal000000000011', '怀宁分干渠', 'MthCanal000000000001'),
        ('MthCanal000000000012', '易潭支渠',   'MthCanal000000000011'),
        ('MthCanal000000000013', '彭西进水闸', 'MthCanal000000000011'),
        ('MthCanal000000000014', '同兴支渠',   'MthCanal000000000011'),
        ('MthCanal000000000015', '周上墩支渠', 'MthCanal000000000011'),
        ('MthCanal000000000016', '九朋支渠',   'MthCanal000000000011'),
        ('MthCanal000000000017', '望江分干渠', 'MthCanal000000000001'),
        ('MthCanal000000000018', '高排沟支渠', 'MthCanal000000000017')
       )
SELECT e.id, e.name AS exp_name, c.gfaegg AS act_name, e.pid AS exp_pid, c.cyjihq AS act_pid
  FROM expect e
  LEFT JOIN "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw" c ON c.id = e.id
 WHERE c.id IS NULL
    OR c.gfaegg IS DISTINCT FROM e.name
    OR c.cyjihq IS DISTINCT FROM e.pid;

-- 3) 19 个站的改挂结果
SELECT a.iofhpi, a.zzkaec, c.gfaegg AS canal_name, a.bviiio_x, a.bviiio_y
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" a
  LEFT JOIN "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw" c ON c.id = a.ywvyds
 WHERE a.iofhpi IN ('8000000002','8000000003',
                    '8000000004','8000000005','8000000006','8000000007','8000000008',
                    '8000000009','8000000010','8000000011','8000000012','8000000013',
                    '8000000014','8000000015','8000000016','8000000017','8000000018',
                    '8000000019','8000000020')
 ORDER BY a.iofhpi;

-- 4) 应返回 0：改挂后仍有站点不在麻塘湖灌区子树内
WITH RECURSIVE scope AS (
    SELECT id FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw"
     WHERE id = 'grTcKYo1RogxZCVkd5H'
    UNION ALL
    SELECT c.id FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw" c
      JOIN scope s ON c.cyjihq = s.id
)
SELECT COUNT(*) AS bad_scope
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" a
 WHERE a.iofhpi IN ('8000000002','8000000003',
                    '8000000004','8000000005','8000000006','8000000007','8000000008',
                    '8000000009','8000000010','8000000011','8000000012','8000000013',
                    '8000000014','8000000015','8000000016','8000000017','8000000018',
                    '8000000019','8000000020')
   AND NOT EXISTS (SELECT 1 FROM scope s WHERE s.id = a.ywvyds);

-- 5) 8000000005 坐标
SELECT iofhpi, zzkaec, bviiio_x, bviiio_y, bviiio_geohash
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
 WHERE iofhpi = '8000000005';

-- 6) 麻塘湖灌区子树下站点数应仍为 46（不含管理单位节点 OOH2pADzfwQioCb5qAF）
WITH RECURSIVE scope AS (
    SELECT id FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw"
     WHERE id = 'grTcKYo1RogxZCVkd5H'
    UNION ALL
    SELECT c.id FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw" c
      JOIN scope s ON c.cyjihq = s.id
)
SELECT COUNT(*) AS mth_total
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" a
 WHERE a.id <> 'OOH2pADzfwQioCb5qAF'
   AND a.ywvyds IN (SELECT id FROM scope);

-- 7) 仍挂根节点的站点清单：预期 27 条
--    （水质 8000000001、雨量 6 条、独立视频记录 19 条、墒情 9000000135；
--      若出现 8000000002~8000000020 中任何一条，说明对应的改挂 UPDATE 没命中）
SELECT a.iofhpi, a.zzkaec, a.epjutj
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" a
 WHERE a.id <> 'OOH2pADzfwQioCb5qAF'
   AND a.ywvyds = 'grTcKYo1RogxZCVkd5H'
 ORDER BY a.iofhpi;

-- 上面校验全部符合预期后执行提交；任何一条不符，把下面这行改成 ROLLBACK; 再执行
COMMIT;

-- ----------------------------------------------------------------------------
-- 回滚脚本（仅在需要撤销时去掉注释块执行；上一版平铺方案同样用本段清理）
--
-- 执行顺序说明：
--   R1 先把 19 个站点还原为麻塘湖灌区根节点，R2 守卫查询此时才可能为 0 行
--   R2 应返回 0 行；若有输出，立刻把下面 COMMIT 改成 ROLLBACK，
--      说明还有本次 19 个以外的站点引用这些节点，先查清来源再删
--   R3 把 8000000005 坐标还原为空：执行前该站经纬度已核实为空；
--      若你执行前该站已有坐标，跳过 R3，改用第 0.3 节快照表回填原值
--   R4 按 18 个固定 id 精确删除，不用 LIKE 模糊匹配，避免误删其他数据
-- ----------------------------------------------------------------------------
/*
BEGIN;

-- R1 站点渠系还原为麻塘湖灌区根节点
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET ywvyds = 'grTcKYo1RogxZCVkd5H', updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi IN ('8000000002','8000000003',
                  '8000000004','8000000005','8000000006','8000000007','8000000008',
                  '8000000009','8000000010','8000000011','8000000012','8000000013',
                  '8000000014','8000000015','8000000016','8000000017','8000000018',
                  '8000000019','8000000020');

-- R2 守卫：删除节点前应返回 0 行
SELECT a.iofhpi, a.zzkaec, a.ywvyds
  FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" a
 WHERE a.ywvyds IN ('MthCanal000000000001','MthCanal000000000002',
                    'MthCanal000000000003','MthCanal000000000004',
                    'MthCanal000000000005','MthCanal000000000006',
                    'MthCanal000000000007','MthCanal000000000008',
                    'MthCanal000000000009','MthCanal000000000010',
                    'MthCanal000000000011','MthCanal000000000012',
                    'MthCanal000000000013','MthCanal000000000014',
                    'MthCanal000000000015','MthCanal000000000016',
                    'MthCanal000000000017','MthCanal000000000018')
 ORDER BY a.iofhpi;

-- R3 8000000005 坐标还原为空（执行前为空值）
UPDATE "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
   SET bviiio_x = NULL, bviiio_y = NULL, bviiio_geohash = NULL,
       updated_at = NOW(), updated_by = 'SYSTEM'
 WHERE iofhpi = '8000000005';

-- R4 按固定 id 删除 18 个渠系节点
DELETE FROM "qixiao-apaas"."t_auto_hltgq_knc3g_egvnhw"
 WHERE id IN ('MthCanal000000000001','MthCanal000000000002',
              'MthCanal000000000003','MthCanal000000000004',
              'MthCanal000000000005','MthCanal000000000006',
              'MthCanal000000000007','MthCanal000000000008',
              'MthCanal000000000009','MthCanal000000000010',
              'MthCanal000000000011','MthCanal000000000012',
              'MthCanal000000000013','MthCanal000000000014',
              'MthCanal000000000015','MthCanal000000000016',
              'MthCanal000000000017','MthCanal000000000018');

COMMIT;
*/
