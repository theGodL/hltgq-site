-- ============================================================================
-- 用水计划页「2026 年实际保证率」模拟数据（仅补闸口流量，不改业务表）
-- ----------------------------------------------------------------------------
-- 口径（WaterPlanService.guarantee，按服务端当前自然年度）：
--   need   = Σ 本年度审核通过(#2#)需水申请 amount
--   actual = Σ 每条申请 [start 00:00, end 00:00] 内该户 gate_config 全部闸口的
--            区间累计 = ttf(窗口内末条有效行) − ttf(起点前最近一条有效行)
--   missing> 0 → 页面显示「待补计量数据」；否则展示 actual ÷ need
--
-- 现状（2026-11-05 测试库实测）：
--   need = 31,500,000 m³（XS0002 宿松 1200 万 / XS0003 怀宁 550 万 / XS0004 望江 1400 万）
--   XS0002 章大进水闸     ZhangdaGate20261027   流量表 0 行 → missing
--   XS0003 太怀干渠长喉槽 jqEawVNMpMgzawQKXJb   流量表 0 行 → missing
--   XS0004 南/北干渠      真实数据在 → 实测 1,065,974.70 m³
--   故页面当前显示「待补计量数据」（missing = 2）
--
-- 本脚本只给两个缺口闸口补历史 ttf 序列（site = 闸口 id，与计量匹配列一致），目标整体保证率 96.37%：
--   need = 31,500,000 → 目标 actual = 31,500,000 × 96.37% = 30,356,550.00
--   望江真实实测 1,065,974.70（真实数据，不参与模拟、不改动）→ 宿松+怀宁需合计 29,290,575.30
--   按两县申报量 1200 : 550 等比分配（各 167.37%）：
--     章大进水闸   2026-09-30 23:00（基准）→ 2026-10-19 23:00，区间增量 20,084,965.92（q≈12.2350 m³/s）
--     太怀干渠     2026-10-10 23:00（基准）→ 2026-10-19 23:00，区间增量  9,205,609.38（q≈11.8385 m³/s）
--   补后 actual = 20,084,965.92 + 9,205,609.38 + 1,065,974.70 = 30,356,550.00
--       保证率 = 96.37%
--   注：望江真实放水仅 106.6 万（申报 1400 万），三条中只有它偏低；为凑到整体 96.37%
--       且不碰真实数据，两条模拟申请的「已完成供水量」会是申报量的 1.67 倍（页面允许 >100%）。
--
-- 影响面：
--   1) 用水计划列表「已完成供水量」/ 灌溉进度、水费出账计量：这两个闸口有数了；
--   2) 灌溉用水页：怀宁候选标识含 jqEawVNMpMgzawQKXJb，怀宁供水量会同步出现 922.6 万 m³；
--      宿松取数用章大测流站 1YCfIVHtH49zlrBQJadm（非本脚本闸口 id），仍维持缺失；
--   3) 流量/闸门监测页：两站会出现 2026-09-30 ~ 2026-10-19 的历史曲线，最新时间即 10-19（停更标红属预期）；
--   4) 不改任何需水申请、用水户、计划、账单数据；不留"当前实时"假流量（模拟段止于 10-19）。
--
-- 风险与卸载：
--   宿松/怀宁这两闸口目前设备未接入、流量表 0 行，模拟行是唯一数据源，可安全使用；
--   设备真正接入并开始落真实数据后，必须执行末尾回滚删除模拟行——否则真实行与模拟行
--   会在同一闸口下混排（ttf 跳变），区间计量会失真。
--
-- 兼容性（2026-11-05 实测踩坑）：
--   KingbaseES 中 `'text' || to_char(...)` 会被推断为布尔 OR（取值 = true），
--   导致 id 全部写成 'true' 而报 duplicate key (id)=(true)；
--   因此 id 拼接一律用 concat()，禁止用 ||。
--   tf / ytf / ttf 自洽：tf = 当日增量，ttf = 基准 + 区间增量，ytf = ttf - tf（仅展示，计量只用 ttf）。
--
-- 幂等：先删 id 前缀 SIMGT- 的旧行再插，可重复执行。
-- 回滚：执行文件末尾注释块（或 DELETE ... WHERE id LIKE 'SIMGT-%'）。
-- 执行：psql -v ON_ERROR_STOP=1 -f water-plan-guarantee-sim-data.sql
-- ============================================================================

BEGIN;

-- 前置检查：两个闸口必须在站档表存在（页面闸口名称/下拉依赖），且 3 条已通过申请仍在
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_5nw74_vnqqef
         WHERE id IN ('ZhangdaGate20261027', 'jqEawVNMpMgzawQKXJb')) <> 2 THEN
        RAISE EXCEPTION '前置检查失败：章大进水闸 / 太怀干渠长喉槽 不在站档表，脚本终止';
    END IF;
    IF (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_demand
         WHERE corp_code = 'hltgq' AND status = '#2#'
           AND demand_no IN ('XS0002', 'XS0003', 'XS0004')) <> 3 THEN
        RAISE EXCEPTION '前置检查失败：XS0002/XS0003/XS0004 三条已通过申请缺失，脚本终止';
    END IF;
END $$;

-- 清理既有模拟行（幂等）
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo
 WHERE corp_code = 'hltgq' AND id LIKE 'SIMGT-%';

-- 一、宿松县 XS0002：章大进水闸 2026-10-01 ~ 2026-10-20，区间增量 20,084,965.92 m³（保证率 167.37%）
--     基准行 09-30 23:00（tm < 起点 10-01 00:00），末行 10-19 23:00（tm <= 终点 10-20 00:00）
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_wt_nfo
    (id, corp_code, created_at, created_by, updated_at, updated_by,
     site, tm, q, tf, ytf, ttf)
SELECT concat('SIMGT-ZD-', to_char(s.tm, 'MMDDHH24MI')),
       'hltgq', now(), 'SYSTEM', now(), 'SYSTEM',
       'ZhangdaGate20261027', s.tm, s.q, s.tf, s.ttf - s.tf, s.ttf
FROM (
    SELECT g.tm,
           12.2350 AS q,
           ROUND((12.2350 * (EXTRACT(EPOCH FROM g.tm) - EXTRACT(EPOCH FROM date_trunc('day', g.tm))))::numeric, 2) AS tf,
           42000000 + ROUND((20084965.92::numeric
               * (EXTRACT(EPOCH FROM g.tm) - EXTRACT(EPOCH FROM TIMESTAMP '2026-09-30 23:00:00'))
               / (EXTRACT(EPOCH FROM TIMESTAMP '2026-10-19 23:00:00') - EXTRACT(EPOCH FROM TIMESTAMP '2026-09-30 23:00:00'))), 2) AS ttf
    FROM generate_series(TIMESTAMP '2026-09-30 23:00:00', TIMESTAMP '2026-10-19 23:00:00',
                         INTERVAL '1 hour') AS g(tm)
) s;

-- 二、怀宁县 XS0003：太怀干渠长喉槽 2026-10-11 ~ 2026-10-20，区间增量 9,205,609.38 m³（保证率 167.37%）
--     基准行 10-10 23:00（tm < 起点 10-11 00:00），末行 10-19 23:00
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_wt_nfo
    (id, corp_code, created_at, created_by, updated_at, updated_by,
     site, tm, q, tf, ytf, ttf)
SELECT concat('SIMGT-TH-', to_char(s.tm, 'MMDDHH24MI')),
       'hltgq', now(), 'SYSTEM', now(), 'SYSTEM',
       'jqEawVNMpMgzawQKXJb', s.tm, s.q, s.tf, s.ttf - s.tf, s.ttf
FROM (
    SELECT g.tm,
           11.8385 AS q,
           ROUND((11.8385 * (EXTRACT(EPOCH FROM g.tm) - EXTRACT(EPOCH FROM date_trunc('day', g.tm))))::numeric, 2) AS tf,
           18000000 + ROUND((9205609.38::numeric
               * (EXTRACT(EPOCH FROM g.tm) - EXTRACT(EPOCH FROM TIMESTAMP '2026-10-10 23:00:00'))
               / (EXTRACT(EPOCH FROM TIMESTAMP '2026-10-19 23:00:00') - EXTRACT(EPOCH FROM TIMESTAMP '2026-10-10 23:00:00'))), 2) AS ttf
    FROM generate_series(TIMESTAMP '2026-10-10 23:00:00', TIMESTAMP '2026-10-19 23:00:00',
                         INTERVAL '1 hour') AS g(tm)
) s;

COMMIT;

-- ============================================================================
-- 执行后自检：按 WaterUsageMeter 口径复算（与接口同 SQL 结构；未插数据时同样可跑，用于回滚后核对）
-- ============================================================================
WITH s AS (
    SELECT
      COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = 'ZhangdaGate20261027' OR f.site = 'ZhangdaGate20261027')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm >= TIMESTAMP '2026-10-01 00:00:00' AND f.tm <= TIMESTAMP '2026-10-20 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0)
      - COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = 'ZhangdaGate20261027' OR f.site = 'ZhangdaGate20261027')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm < TIMESTAMP '2026-10-01 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0) AS susong_actual
), h AS (
    SELECT
      COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = 'jqEawVNMpMgzawQKXJb' OR f.site = 'jqEawVNMpMgzawQKXJb')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm >= TIMESTAMP '2026-10-11 00:00:00' AND f.tm <= TIMESTAMP '2026-10-20 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0)
      - COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = 'jqEawVNMpMgzawQKXJb' OR f.site = 'jqEawVNMpMgzawQKXJb')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm < TIMESTAMP '2026-10-11 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0) AS huaining_actual
), w AS (
    SELECT
      COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = 'cOGko9nLfFz6rRAvKvY' OR f.site = 'cOGko9nLfFz6rRAvKvY')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm >= TIMESTAMP '2026-10-21 00:00:00' AND f.tm <= TIMESTAMP '2026-10-31 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0)
      - COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = 'cOGko9nLfFz6rRAvKvY' OR f.site = 'cOGko9nLfFz6rRAvKvY')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm < TIMESTAMP '2026-10-21 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0)
      + COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = '4Ieu32Stnz8dTD8S8GD' OR f.site = '4Ieu32Stnz8dTD8S8GD')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm >= TIMESTAMP '2026-10-21 00:00:00' AND f.tm <= TIMESTAMP '2026-10-31 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0)
      - COALESCE((SELECT f.ttf FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo f
        WHERE (f.stcd = '4Ieu32Stnz8dTD8S8GD' OR f.site = '4Ieu32Stnz8dTD8S8GD')
          AND f.ttf IS NOT NULL AND f.ttf NOT IN (-999, -9991)
          AND f.tm < TIMESTAMP '2026-10-21 00:00:00'
        ORDER BY f.tm DESC LIMIT 1), 0) AS wangjiang_actual
)
SELECT s.susong_actual, h.huaining_actual, w.wangjiang_actual,
       s.susong_actual + h.huaining_actual + w.wangjiang_actual AS actual_total,
       31500000.00 AS need_total,   -- 2026 三条已通过申请合计，申请数据变动时需同步
       ROUND((s.susong_actual + h.huaining_actual + w.wangjiang_actual) / 31500000.00 * 100, 2) AS guarantee_rate
FROM s, h, w;

-- 模拟行数（应为 457 + 217 = 674）
SELECT split_part(id, '-', 2) AS gate_tag, COUNT(*) AS rows
FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo
WHERE corp_code = 'hltgq' AND id LIKE 'SIMGT-%'
GROUP BY 1 ORDER BY 1;

-- ============================================================================
-- 回滚（清理全部模拟行；真实流量数据不受影响）
-- ============================================================================
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_wt_nfo
--  WHERE corp_code = 'hltgq' AND id LIKE 'SIMGT-%';
