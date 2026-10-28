-- 节水管理数据整备（节水方案和措施 / 节水指标体系）
-- ============================================================
-- 问题（2026-10-28 只读探测实测）：
--   ① 节水方案 3 条全为联调期测试数据：
--        "1111"（措施 "1111111111"）、"11"（措施 "11"）、
--        "测试_BS20261020_节水闭环"（措施/核验说明均写明"授权测试、合成测试数据"，is_demo=#1# 虚拟演示）
--   ② 节水指标 3 条同样带测试痕迹：
--        "测试_BS20261020_节水量"（用途"授权闭环测试，虚拟核验"）、"额"（用途"4"）、
--        "渠系水利用系数"（名称是系数，计算方式却选了 #3# 目标完成率，语义错配）
--   ③ 年度水量量级失真：库内 2025 行 计划 36 / 实际 50 万m³，2026 行 计划 47.196 / 实际 120 万m³；
--      而系统内真实年度供水计划为 2025 年 45200 万m³（YJ001）、2026 年 47623.5 万m³（YJ002），
--      差约 1000 倍，与其他模块（用水计划/调度/账单）口径对不上。
--
-- 处理：清空三表后按真实口径重建
--   年度水量 2 行（2025 潜力年度依据 2024；2026 潜力年度依据 2025，计划自动取 YJ001）
--   节水指标 4 个（严格对应三种计算方式，#1#节水量 / #2#节水率 / #3#目标完成率）
--   节水方案 6 个（2025 完成 2 个 + 2026 实施中 2 个 / 草稿 1 个 / 待复核 1 个，覆盖全部状态）
--
-- 口径校验（潜力 = 上一年实际 − 上一年计划，超出计划的部分即当年可压减空间，与
--   SavingSchemeService.requireUsablePotential 一致：潜力 ≤ 0 则不能建方案）：
--     2025 年度：46380 − 45600 = 780 万m³，方案目标合计 260 + 190 = 450 ≤ 780
--     2026 年度：45930 − 45200 = 730 万m³，方案目标合计 240 + 150 + 120 + 90 = 600 ≤ 730
--   年度水量首尾相接（避免同一年的“实际”出现多个值）：
--     2024 实际 46380 −（2025 两个方案 260 + 190）= 45930 = 2026 记录的“上一年实际”
--   已完成的 2 个方案核验值闭合：
--     方案1：46380 − 46120 = 260 = 目标；年度节水量 260 ≥ 目标 240 达标；目标完成率 100% 达标
--     方案2：46380 − 46190 = 190 = 目标；年度节水率 190/46380 = 0.4096% ≥ 目标 0.35% 达标
--
-- plan_id 说明：'PlanY2025' 为库内 t_auto_hltgq_water_plan.id 实测值（对应 plan_no=YJ001）；
--   该表 id 并非 YJ 编号规则，二者不可混用，执行前可按本文末尾自查 SQL 复核。
--
-- 影响范围：仅 "qixiao-apaas" 下三张自研表（water_saving_annual / scheme / indicator），
--   不涉及用水计划、调度、账单等模块；三表无外部引用（scheme 仅自引 annual 与 indicator）。
-- 副作用：原方案 "11" 关联的附件记录将随行删除，磁盘文件（saving/2026-10/ 下）需另行手工清理；
--   附件路径已随备份表 bak_saving_20261028_scheme 留存，可据此定位。
-- 幂等性：DELETE 改白名单（只删本次确认过的联调测试行，不碰他人后续录入的真实数据）；
--   备份表用 IF NOT EXISTS；固定主键插入，重复执行会因主键冲突显式报错而不静默覆盖，
--   需先回滚（INSERT INTO ... SELECT * FROM bak_...）再重跑。
-- 执行方式：以具备 "qixiao-apaas" schema 写权限的账号整体执行（BEGIN/COMMIT 事务包裹）。
-- ============================================================
-- 0. 执行前备份（DDL 在事务外执行，后续失败备份仍在；重复执行时 IF NOT EXISTS 保留首次快照）
--    需具备 "qixiao-apaas" schema 的 CREATE 权限；无此权限可注释本节、改在库里手工导出备份。
CREATE TABLE IF NOT EXISTS "qixiao-apaas".bak_saving_20261028_scheme
    AS SELECT * FROM "qixiao-apaas".t_auto_hltgq_water_saving_scheme;
CREATE TABLE IF NOT EXISTS "qixiao-apaas".bak_saving_20261028_indicator
    AS SELECT * FROM "qixiao-apaas".t_auto_hltgq_water_saving_indicator;
CREATE TABLE IF NOT EXISTS "qixiao-apaas".bak_saving_20261028_annual
    AS SELECT * FROM "qixiao-apaas".t_auto_hltgq_water_saving_annual;

BEGIN;

-- 1. 清空（白名单，顺序：方案 → 指标 → 年度记录，避免自引用悬空）
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_saving_scheme
 WHERE corp_code = 'hltgq' AND name IN ('1111', '11', '测试_BS20261020_节水闭环');
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_saving_indicator
 WHERE corp_code = 'hltgq' AND name IN ('测试_BS20261020_节水量', '额', '渠系水利用系数');
-- 年度记录必须按年份删，否则撞 uk_water_saving_annual_year
DELETE FROM "qixiao-apaas".t_auto_hltgq_water_saving_annual
 WHERE corp_code = 'hltgq' AND potential_year IN (2025, 2026);

-- 2. 年度水量记录
--    2025 潜力年度：2024 年度计划系统内无对应计划单，来源记 manual
--    2026 潜力年度：计划由 YJ001（2025 年度供水计划 45200 万m³）自动带出，plan_id 留痕
--    两行潜力均为正（实际超计划 780 / 730），实际低于计划则为负，负潜力无法建方案
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_saving_annual
    ("id", "potential_year", "basis_year", "prev_plan_volume", "prev_actual_volume", "plan_source", "plan_id",
     "actual_source", "is_demo", "corp_code", "created_at", "created_by", "updated_at", "updated_by")
VALUES
    ('SavAnnual2025Aa00000001', 2025, 2024, 45600.000000, 46380.000000, 'manual', NULL, 'manual', '#2#', 'hltgq',
     TIMESTAMP '2025-01-20 09:30:00', 'A9jEKZmJhcmUEK4RCZo', TIMESTAMP '2025-01-20 09:30:00', 'A9jEKZmJhcmUEK4RCZo'),
    ('SavAnnual2026Aa00000002', 2026, 2025, 45200.000000, 45930.000000, 'auto_plan', 'PlanY2025', 'manual', '#2#', 'hltgq',
     TIMESTAMP '2026-01-12 10:15:00', 'A9jEKZmJhcmUEK4RCZo', TIMESTAMP '2026-01-12 10:15:00', 'A9jEKZmJhcmUEK4RCZo');

-- 3. 节水指标（名称与计算方式严格对应：节水量 / 节水率 / 目标完成率）
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_saving_indicator
    ("id", "name", "method", "target_value", "purpose", "enabled",
     "corp_code", "created_at", "created_by", "updated_at", "updated_by")
VALUES
    ('SavIndYearSave000000001', '年度节水量', '#1#', 240.000000,
     '年度用水效率考核，按灌区实际放水量核减', '#1#',
     'hltgq', TIMESTAMP '2025-01-20 10:00:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2025-01-20 10:00:00', 'A9jEKZmJhcmUEK4RCZo'),
    ('SavIndYearRate000000002', '年度节水率', '#2#', 0.350000,
     '节水型灌区创建年度指标，按节水量占实施前用水量比例计', '#1#',
     'hltgq', TIMESTAMP '2025-01-20 10:05:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2025-01-20 10:05:00', 'A9jEKZmJhcmUEK4RCZo'),
    ('SavIndTargetDone0000003', '节水目标完成率', '#3#', 100.000000,
     '方案节水目标达成判定，100% 视为达标', '#1#',
     'hltgq', TIMESTAMP '2025-01-20 10:10:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2025-01-20 10:10:00', 'A9jEKZmJhcmUEK4RCZo'),
    ('SavIndHeadSave0000000004', '渠首供水节水量', '#1#', 120.000000,
     '渠首计量断面节水量，用于上报市水利局', '#1#',
     'hltgq', TIMESTAMP '2026-01-12 10:30:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-01-12 10:30:00', 'A9jEKZmJhcmUEK4RCZo');

-- 4. 节水方案
--    2025 年度：2 个已完成（含核验留痕，verify_invalid=#2# 有效）
--    2026 年度：实施中 2 个 + 草稿 1 个 + 待复核 1 个（核验作废样例：verify_invalid=#1#）
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_saving_scheme
    ("id", "name", "year", "evidence_id", "baseline_volume", "target_volume", "owner", "measure", "status",
     "indicator_ids", "verify_after_volume", "verify_source", "verify_note", "verify_date", "verify_comparable",
     "verify_invalid", "last_recalc_at", "last_recalc_reason", "is_demo",
     "attachment_name", "attachment_url", "corp_code", "created_at", "created_by", "updated_at", "updated_by")
VALUES
    -- 2025-① 总干渠渠系水利用系数提升（已完成，核验闭合 260 万m³）
    ('SavSch2025Canal00000001', '2025 年度总干渠渠系水利用系数提升方案', 2025, 'SavAnnual2025Aa00000001',
     46380.000000, 260.000000, '何俊杰',
     '对总干渠 12.6 km 渠段实施混凝土衬砌防渗，修复破损分水口 18 处；干渠沿线布设量水断面 6 处，按旬开展流量监测；配套渠道清淤 3.2 万 m³，减少输水损失。',
     '#3#', '["SavIndYearSave000000001","SavIndTargetDone0000003"]',
     46120.000000, '2025 年度供水台账（HLT-YH-2025-01）',
     '渠首计量断面全年累计放水 46120 万 m³，较实施前减少 260 万 m³。', DATE '2026-01-15', '#1#', '#2#',
     NULL, NULL, '#2#',
     NULL, NULL, 'hltgq', TIMESTAMP '2025-02-08 09:20:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-01-15 16:40:00', 'A9jEKZmJhcmUEK4RCZo'),
    -- 2025-② 双庙湖灌区灌溉制度优化（已完成，核验闭合 190 万m³）
    ('SavSch2025Schedule0000002', '2025 年度双庙湖灌区灌溉制度优化方案', 2025, 'SavAnnual2025Aa00000001',
     46380.000000, 190.000000, '万江淮',
     '推行水稻浅湿干交替灌溉，将灌水定额由 1050 m³/亩下调至 960 m³/亩；按生育期编制供水分旬计划，双庙湖节制闸实行轮灌调度，减少无效弃水。',
     '#3#', '["SavIndYearRate000000002","SavIndTargetDone0000003"]',
     46190.000000, '2025 年度用水统计年报（HLT-NB-2025）',
     '双庙湖片区全年用水 46190 万 m³，节水率 0.41%，达到年度指标。', DATE '2026-01-18', '#1#', '#2#',
     NULL, NULL, '#2#',
     NULL, NULL, 'hltgq', TIMESTAMP '2025-03-12 14:10:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-01-18 11:05:00', 'A9jEKZmJhcmUEK4RCZo'),
    -- 2026-③ 干支渠衬砌防渗工程（实施中）
    ('SavSch2026Lining00000003', '2026 年度干支渠衬砌防渗节水工程', 2026, 'SavAnnual2026Aa00000002',
     45930.000000, 240.000000, '万江淮',
     '实施支渠衬砌 28.4 km、斗渠防渗 15.7 km，配套建筑物改造 46 座；工程分两个标段推进，计划 2026 年 12 月完工，预计年减少输水损失 240 万 m³。',
     '#2#', '["SavIndYearSave000000001","SavIndYearRate000000002"]',
     NULL, NULL, NULL, NULL, NULL, '#2#',
     NULL, NULL, '#2#',
     NULL, NULL, 'hltgq', TIMESTAMP '2026-02-25 10:30:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-09-18 15:20:00', 'A9jEKZmJhcmUEK4RCZo'),
    -- 2026-④ 渠首计量设施自动化改造（实施中）
    ('SavSch2026Meter0000000004', '2026 年度渠首计量设施自动化改造方案', 2026, 'SavAnnual2026Aa00000002',
     45930.000000, 150.000000, '何文学',
     '渠首进水闸、太宿进水闸等 8 处计量断面改造为雷达波自动测流，接入灌区信息化平台；配套建设量水堰 4 座，实现按旬自动生成供水量报表。',
     '#2#', '["SavIndHeadSave0000000004"]',
     NULL, NULL, NULL, NULL, NULL, '#2#',
     NULL, NULL, '#2#',
     NULL, NULL, 'hltgq', TIMESTAMP '2026-03-06 09:45:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-08-22 10:15:00', 'A9jEKZmJhcmUEK4RCZo'),
    -- 2026-⑤ 水稻节水灌溉技术推广（草稿）
    ('SavSch2026Rice00000000005', '2026 年度水稻节水灌溉技术推广方案', 2026, 'SavAnnual2026Aa00000002',
     45930.000000, 120.000000, '张立华',
     '在双庙湖、毕岭两个片区推广水稻控制灌溉技术 3.8 万亩，配套土壤墒情监测点 12 处；组织技术培训 6 场次，覆盖用水户 320 户。',
     '#1#', '["SavIndYearSave000000001"]',
     NULL, NULL, NULL, NULL, NULL, '#2#',
     NULL, NULL, '#2#',
     NULL, NULL, 'hltgq', TIMESTAMP '2026-10-09 16:30:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-10-09 16:30:00', 'A9jEKZmJhcmUEK4RCZo'),
    -- 2026-⑥ 末级渠系量水设施配套（待复核：核验依据变更导致原结论作废的样例）
    ('SavSch2026Branch0000000006', '2026 年度末级渠系量水设施配套方案', 2026, 'SavAnnual2026Aa00000002',
     45930.000000, 90.000000, '汪中华',
     '为 4 个乡镇 26 个用水小组配套末级渠系量水设施 58 处，实行按方计量收费；同步完善用水户计量台账，实现末级渠系计量全覆盖。',
     '#4#', '["SavIndTargetDone0000003"]',
     45860.500000, '2026 年第三季度用水台账（季度数据，待年度台账替换）',
     '季度台账数据不完整，年度台账出具后需重新核验。', DATE '2026-10-10', '#2#', '#1#',
     TIMESTAMP '2026-10-16 09:00:00', '核验资料来源变更，原核验结论作废，待年度台账出具后复审', '#2#',
     NULL, NULL, 'hltgq', TIMESTAMP '2026-07-14 11:20:00', 'A9jEKZmJhcmUEK4RCZo',
     TIMESTAMP '2026-10-16 09:00:00', 'A9jEKZmJhcmUEK4RCZo');

COMMIT;

-- ============================================================
-- 执行后自查（应返回 2 行：2025 潜力 780 / 2026 潜力 730；若为负数说明实际低于计划，不能建方案）
-- SELECT potential_year, prev_plan_volume, prev_actual_volume,
--        (prev_actual_volume - prev_plan_volume) AS potential
--   FROM "qixiao-apaas".t_auto_hltgq_water_saving_annual ORDER BY potential_year;
--
-- 执行后自查（应返回 6 行，状态分布：#3# 2 条 / #2# 2 条 / #1# 1 条 / #4# 1 条）
-- SELECT name, "year", status, target_volume, is_demo
--   FROM "qixiao-apaas".t_auto_hltgq_water_saving_scheme ORDER BY "year", name;
--
-- 执行后自查（应返回 4 行，method 分布：#1# 2 条 / #2# 1 条 / #3# 1 条）
-- SELECT name, method, target_value, enabled
--   FROM "qixiao-apaas".t_auto_hltgq_water_saving_indicator ORDER BY created_at;
-- ============================================================
