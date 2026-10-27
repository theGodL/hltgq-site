-- =============================================================================
-- 测试用水户及关联测试数据清理
-- 生成时间：2026-10-27（基于库内实际引用核对结果）
--
-- 待删测试户（5 个）：
--   YH001 111                          Y4SBvcvEE6fgCObvTeP0FR
--   YH002 222                          Yk2rUuHPzBwcOzV7aTPNR6
--   YH003 333启用                      RQguirrkkNohPfubWR2caR
--   YH004 测试停用                     ORaX02x3o25RUExhjKSQt9
--   YH005 测试_BS20261020_闭环用水户   rQmUOKM5Lq3sVuQANTHPMj
--
-- 关联数据核对（截至生成时，全部为测试户产生）：
--   t_auto_hltgq_water_plan_item          17 行
--   t_auto_hltgq_water_dispatch           11 行
--   t_auto_hltgq_water_demand              8 行
--   t_auto_hltgq_water_dispatch_record     4 行
--   t_auto_hltgq_water_bill                0 行
--   t_auto_hltgq_water_ledger              0 行
--   计划 12 条（YJ001/002/003/005/006/007/008/009/010/011/012/013）共 17 条明细，
--   明细全部属于测试户 —— 删完即空壳，故一并删除。
--
-- 唯一需要「保留但断引用」的外部数据：
--   t_auto_hltgq_water_saving_annual 有 1 行（2026 潜力年 / 基准 2025，
--   计划量 47.196 万m³、实际量 120 万m³），引用待删计划 YJ013；
--   该行又被 2 条节水方案作为依据（evidence_id）引用，直接删会破坏方案证据链，
--   因此只把 plan_id 置空、plan_source 改为 manual（数值原样保留）。
--
-- 保留（不动）：
--   四县户 YH006 宿松县 / YH007 怀宁县 / YH008 望江县 / YH009 太湖县
--   站点 章大进水闸 ZhangdaGate20261027
--   用水户表以外的一切档案（站点/设备/流量数据 均不涉及）
--
-- 执行顺序说明：先断引用（记录→方案→需水→账单/台账→计划），再删明细，最后删户。
-- 计划删除放在明细删除之前，因为要用「明细是否全部属于测试户」作为判定条件。
--
-- 幂等：全部按 id / 条件删除，重复执行不会报错。
-- 回滚：本操作不可逆，如需保留请先备份以下 7 张表。
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 第 0 步：执行前快照（先看清要删什么，再往下执行）
-- -----------------------------------------------------------------------------
SELECT '删除前-测试户' AS stage, u.user_no, u.name, u.status
FROM "qixiao-apaas"."t_auto_hltgq_water_user" u
WHERE u.id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
               'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
               'rQmUOKM5Lq3sVuQANTHPMj')
ORDER BY u.user_no;

SELECT '删除前-关联行数' AS stage,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_plan_item"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS plan_item,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_dispatch"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS dispatch,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_demand"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS demand,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_dispatch_record"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS dispatch_record,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_bill"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS bill,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_ledger"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS ledger;

-- -----------------------------------------------------------------------------
-- 第 1 步：调度记录（最下游，先删）
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_dispatch_record"
WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                        'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                        'rQmUOKM5Lq3sVuQANTHPMj');

-- -----------------------------------------------------------------------------
-- 第 2 步：调度方案
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_dispatch"
WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                        'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                        'rQmUOKM5Lq3sVuQANTHPMj');

-- -----------------------------------------------------------------------------
-- 第 3 步：需水上报
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_demand"
WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                        'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                        'rQmUOKM5Lq3sVuQANTHPMj');

-- -----------------------------------------------------------------------------
-- 第 4 步：水费账单 / 手工台账（当前均为 0 行，保留语句以防后补数据）
--   注意：若账单有已收款记录，建议先人工确认再删
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_bill"
WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                        'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                        'rQmUOKM5Lq3sVuQANTHPMj');

DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_ledger"
WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                        'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                        'rQmUOKM5Lq3sVuQANTHPMj');

-- -----------------------------------------------------------------------------
-- 第 4.5 步：节水潜力年度表断开计划引用（只改来源，不动数值）
--   该行被 2 条节水方案作为依据引用，不能删；plan_id 断引用后列表页不 JOIN 计划表，
--   功能不受影响。
-- -----------------------------------------------------------------------------
UPDATE "qixiao-apaas"."t_auto_hltgq_water_saving_annual"
   SET "plan_source" = 'manual', "plan_id" = NULL
 WHERE "plan_id" IN (
     SELECT i.plan_id
     FROM "qixiao-apaas"."t_auto_hltgq_water_plan_item" i
     GROUP BY i.plan_id
     HAVING COUNT(*) = SUM(CASE WHEN i.water_user_id IN
             ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
              'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
              'rQmUOKM5Lq3sVuQANTHPMj') THEN 1 ELSE 0 END)
 );

-- -----------------------------------------------------------------------------
-- 第 5 步：计划（仅删除「明细全部属于测试户」的计划，共 12 条）
--   判定基于当前明细，必须在第 6 步删明细之前执行。
--   如需保留某条计划，可在此处加 AND p.plan_no NOT IN ('YJ007')
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_plan" p
WHERE p.corp_code = 'hltgq'
  AND p.id IN (
      SELECT i.plan_id
      FROM "qixiao-apaas"."t_auto_hltgq_water_plan_item" i
      GROUP BY i.plan_id
      HAVING COUNT(*) = SUM(CASE WHEN i.water_user_id IN
              ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
               'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
               'rQmUOKM5Lq3sVuQANTHPMj') THEN 1 ELSE 0 END)
  );

-- -----------------------------------------------------------------------------
-- 第 6 步：计划明细
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_plan_item"
WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                        'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                        'rQmUOKM5Lq3sVuQANTHPMj');

-- -----------------------------------------------------------------------------
-- 第 7 步：用水户档案（最后删）
-- -----------------------------------------------------------------------------
DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_user"
WHERE "id" IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
               'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
               'rQmUOKM5Lq3sVuQANTHPMj');

-- -----------------------------------------------------------------------------
-- 第 8 步：执行后核对
-- -----------------------------------------------------------------------------
-- 剩余用水户（应只有 4 条：YH006~YH009）
SELECT '删除后-剩余用水户' AS stage, u.user_no, u.name, u.status
FROM "qixiao-apaas"."t_auto_hltgq_water_user" u
WHERE u.corp_code = 'hltgq'
ORDER BY u.user_no;

-- 残留检查（应全为 0）
SELECT '删除后-残留' AS stage,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_plan_item"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS plan_item,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_dispatch"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS dispatch,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_demand"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS demand,
       (SELECT COUNT(*) FROM "qixiao-apaas"."t_auto_hltgq_water_dispatch_record"
         WHERE water_user_id IN ('Y4SBvcvEE6fgCObvTeP0FR', 'Yk2rUuHPzBwcOzV7aTPNR6',
                                 'RQguirrkkNohPfubWR2caR', 'ORaX02x3o25RUExhjKSQt9',
                                 'rQmUOKM5Lq3sVuQANTHPMj')) AS dispatch_record;

-- 剩余计划（应为 0 条）
SELECT '删除后-剩余计划' AS stage, p.plan_no, p.plan_name, p.status
FROM "qixiao-apaas"."t_auto_hltgq_water_plan" p
WHERE p.corp_code = 'hltgq'
ORDER BY p.plan_no;

-- 节水年报（应为 2 行：2026 潜力行 plan_source=manual、plan_id 为空，数值 47.196/120 不变）
SELECT '删除后-节水年报' AS stage, a.potential_year, a.basis_year,
       a.prev_plan_volume, a.prev_actual_volume, a.plan_source, a.plan_id
FROM "qixiao-apaas"."t_auto_hltgq_water_saving_annual" a
ORDER BY a.potential_year DESC;

COMMIT;
