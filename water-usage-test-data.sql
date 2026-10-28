-- ============================================================================
-- 灌区用水 · 测试数据（六模块，基于现有 4 个县用水户）
-- ----------------------------------------------------------------------------
-- 背景：用水户表已有 4 条（宿松县 YH006 / 怀宁县 YH007 / 望江县 YH008 / 太湖县 YH009），
--       本脚本只读取其 id、user_no、灌溉面积、水价、闸口配置，**不修改用水户任何字段**。
-- 生成：用水定额标准库、用水计划（含明细）、需水上报、调度指令、调度记录、水费账单。
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS，按业务唯一键去重，可重复执行。
-- 编号：与页面新建同规则（计划 YJ+3 位 / 需水 XS+4 位 / 调度 DD+4 位 / 台账 TZ+期间+户号+4 位 /
--       账单 SF+期间+户号+4 位），序号均为全局序号，与现有 0 条数据不冲突。
-- 单位：库内水量一律 m³、金额一律元；页面展示时由接口换算为万m³。
-- 口径：水费账单走「手工台账出账」路径（quantity = 台账用水量），不依赖闸门监测数据；
--       调度指令不挂账单（bill_id 为空），避免与实时计量口径打架。
--       手工台账是「县级上报的独立录入口径」，与调度指令的实时计量口径互不换算，
--       两者水量量级因此不需要对齐（代码中本就是两条并列的出账路径）。
--
-- 执行：psql -v ON_ERROR_STOP=1 -f water-usage-test-data.sql（或按整段粘贴执行）。
--       执行前建议先备份六张表；回滚语句见文件末尾注释。
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 前置检查：缺失预期用水户或引用定额时直接终止（整笔事务回滚，不留脏数据）
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_user
         WHERE corp_code = 'hltgq'
           AND id IN ('WaterUserSusong2026', 'WaterUserHuaining26',
                      'WaterUserWangjiang26', 'WaterUserTaihu2026')) <> 4 THEN
        RAISE EXCEPTION '前置检查失败：未找到预期的 4 个县用水户，脚本终止（用水户未被修改）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_quota
                    WHERE corp_code = 'hltgq' AND id = 'HSWxM6KynMQVQTECGV4mR2') THEN
        RAISE EXCEPTION '前置检查失败：缺少「水稻 控制灌溉 450」定额（id=HSWxM6KynMQVQTECGV4mR2），脚本终止';
    END IF;
    -- 台账/账单的结算周期取自档案（必须为「年度」），水价直接在 INSERT 里 JOIN 档案取值，此处断言可用
    IF (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_user
         WHERE corp_code = 'hltgq'
           AND id IN ('WaterUserSusong2026', 'WaterUserHuaining26',
                      'WaterUserWangjiang26', 'WaterUserTaihu2026')
           AND settle_cycle = '#3#' AND water_price > 0 AND status = '#1#') <> 4 THEN
        RAISE EXCEPTION '前置检查失败：四户的结算周期(年度)/水价(正数)/状态(正常)与脚本假设不符，脚本终止（用水户未被修改）';
    END IF;
    -- 需水/调度/记录把 5 个闸口写死了，断言每个闸口确实属于对应用户的 gate_config
    IF (SELECT COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_user u
         WHERE u.corp_code = 'hltgq'
           AND ((u.id = 'WaterUserSusong2026'  AND u.gate_config LIKE '%ZhangdaGate20261027%')
             OR (u.id = 'WaterUserHuaining26'  AND u.gate_config LIKE '%jqEawVNMpMgzawQKXJb%')
             OR (u.id = 'WaterUserWangjiang26' AND u.gate_config LIKE '%cOGko9nLfFz6rRAvKvY%'
                                               AND u.gate_config LIKE '%4Ieu32Stnz8dTD8S8GD%')
             OR (u.id = 'WaterUserTaihu2026'   AND u.gate_config LIKE '%CAYQ739MiBWMg9gQvyi%'))) <> 4 THEN
        RAISE EXCEPTION '前置检查失败：四户的闸口配置未覆盖脚本引用的 5 个闸口，脚本终止（用水户未被修改）';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 一、用水定额标准库（补 8 条；现有「水稻 控制灌溉 450」「冬小麦 滴灌 340.5」不动）
--     唯一键：作物种类 + 灌溉方式
--     灌溉方式字典：#1# 淹灌 / #2# 控制灌溉 / #3# 畦灌 / #4# 沟灌 / #5# 喷灌
--                    #6# 滴灌 / #7# 膜下滴灌 / #8# 微喷 / #9# 泛水补水 / #10# 计划直供
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_quota
    (id, crop_type, irrigation_method, quota_value, remark, corp_code, created_at, created_by)
SELECT v.id, v.crop_type, v.irrigation_method, v.quota_value, v.remark, 'hltgq',
       TIMESTAMP '2026-10-28 09:00:00', 'SYSTEM'
FROM (VALUES
    ('QuotaRiceFlood',    '水稻',   '#1#',  780.00, '泡田+生育期全周期定额'),
    ('QuotaRiceBorder',   '水稻',   '#3#',  660.00, '泡田+生育期全周期定额'),
    ('QuotaRiceDirect',   '水稻',   '#10#', 600.00, '渠首直供口径定额'),
    ('QuotaWheatBorder',  '冬小麦', '#3#',  380.00, '生育期灌溉定额'),
    ('QuotaRapeBorder',   '油菜',   '#3#',  300.00, '生育期灌溉定额'),
    ('QuotaCottonFurrow', '棉花',   '#4#',  360.00, '生育期灌溉定额'),
    ('QuotaVegSpray',     '蔬菜',   '#5#',  420.00, '设施蔬菜年定额'),
    ('QuotaSeedlingMicro','苗木',   '#8#',  300.00, '苗圃年定额')
) AS v(id, crop_type, irrigation_method, quota_value, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_quota q
    WHERE q.corp_code = 'hltgq'
      AND q.crop_type = v.crop_type
      AND q.irrigation_method = v.irrigation_method
);

-- ----------------------------------------------------------------------------
-- 二、用水计划（4 个，覆盖 编制中 / 已生效 / 已归档 / 已作废 四种状态）
--     明细测算水量 = 灌溉面积 × 定额标准（水稻 控制灌溉 450 m³/亩）
--     总水量 = 明细计划水量汇总；已调度水量 = 该计划下未取消调度指令的计划放水量合计
--     编号按创建时间递增：列表按 plan_no DESC 排序，取号是全局 MAX+1，真实数据不会倒挂
-- ----------------------------------------------------------------------------

-- 2.1 2025 年度（已归档，历史留存）
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_plan
    (id, plan_no, plan_name, plan_type, period_start, period_end, total_water, scheduled_qty,
     remark, status, corp_code, created_at, created_by)
SELECT 'PlanY2025', 'YJ001', '2025年度花凉亭灌区供水计划', '#1#',
       DATE '2025-01-01', DATE '2025-12-31', 452000000.00, 0.00,
       '2025 年度供水计划，已执行完毕归档', '#3#', 'hltgq',
       TIMESTAMP '2025-01-05 09:00:00', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_plan p
    WHERE p.corp_code = 'hltgq' AND p.plan_no = 'YJ001'
);

-- 2.2 2026 年度（已生效，调度指令关联此计划）
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_plan
    (id, plan_no, plan_name, plan_type, period_start, period_end, total_water, scheduled_qty,
     remark, status, corp_code, created_at, created_by)
SELECT 'PlanY2026', 'YJ002', '2026年度花凉亭灌区供水计划', '#1#',
       DATE '2026-01-01', DATE '2026-12-31', 476235000.00, 34000000.00,
       '2026 年度供水计划，按四县灌溉面积核定', '#2#', 'hltgq',
       TIMESTAMP '2026-01-05 09:00:00', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_plan p
    WHERE p.corp_code = 'hltgq' AND p.plan_no = 'YJ002'
);

-- 2.3 2026 年 10 月（编制中，用于演示编辑 / 生效流程）
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_plan
    (id, plan_no, plan_name, plan_type, period_start, period_end, total_water, scheduled_qty,
     remark, status, corp_code, created_at, created_by)
SELECT 'PlanM202610', 'YJ003', '2026年10月月度供水计划', '#2#',
       DATE '2026-10-01', DATE '2026-10-31', 39686250.00, 0.00,
       '10 月月度计划，按年度计划月均分解', '#1#', 'hltgq',
       TIMESTAMP '2026-09-25 09:00:00', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_plan p
    WHERE p.corp_code = 'hltgq' AND p.plan_no = 'YJ003'
);

-- 2.4 2027 年度（已作废，用于演示作废留痕）
--     void_by 存平台用户 id（列表按该 id JOIN t_apaas_uc_user 取姓名），取真实用户「万江淮」
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_plan
    (id, plan_no, plan_name, plan_type, period_start, period_end, total_water, scheduled_qty,
     remark, status, void_by, void_at, void_reason, corp_code, created_at, created_by)
SELECT 'PlanY2027', 'YJ004', '2027年度花凉亭灌区供水计划', '#1#',
       DATE '2027-01-01', DATE '2027-12-31', 476235000.00, 0.00,
       '2027 年度预编制计划', '#4#', 'sN1whojZaDfmArRp7GA', TIMESTAMP '2026-10-20 15:30:00',
       '灌溉面积核定数据待更新，本版作废重编', 'hltgq',
       TIMESTAMP '2026-10-10 09:00:00', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_plan p
    WHERE p.corp_code = 'hltgq' AND p.plan_no = 'YJ004'
);

-- 2.5 计划明细（行 = 用水户；同一计划内每户仅一行）
--     计划水量按各县灌溉面积核定：2026 年度全额、2025 年度略低于测算、月度按年/12 分解
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_plan_item
    (id, plan_id, water_user_id, irrigation_area, quota_id, calc_water, plan_water,
     override_reason, stage_tag, sort_order, corp_code, created_at, created_by)
SELECT v.id, v.plan_id, v.water_user_id, v.irrigation_area, 'HSWxM6KynMQVQTECGV4mR2',
       v.calc_water, v.plan_water, NULL, v.stage_tag, v.sort_order, 'hltgq',
       TIMESTAMP '2026-01-05 09:00:00', 'SYSTEM'
FROM (VALUES
    -- 2026 年度（已生效）：测算水量 = 面积 × 450，计划水量 = 测算水量
    ('PI_2026_SUSONG',    'PlanY2026',   'WaterUserSusong2026',   370000.00, 166500000.00, 166500000.00, '春灌/夏灌', 1),
    ('PI_2026_HUAINING',  'PlanY2026',   'WaterUserHuaining26',   128900.00,  58005000.00,  58005000.00, '春灌/夏灌', 2),
    ('PI_2026_WANGJIANG', 'PlanY2026',   'WaterUserWangjiang26',  386100.00, 173745000.00, 173745000.00, '春灌/夏灌', 3),
    ('PI_2026_TAIHU',     'PlanY2026',   'WaterUserTaihu2026',    173300.00,  77985000.00,  77985000.00, '春灌/夏灌', 4),
    -- 2025 年度（已归档）：实际核定略低于测算水量
    ('PI_2025_SUSONG',    'PlanY2025',   'WaterUserSusong2026',   370000.00, 166500000.00, 158000000.00, '春灌/夏灌', 1),
    ('PI_2025_HUAINING',  'PlanY2025',   'WaterUserHuaining26',   128900.00,  58005000.00,  55000000.00, '春灌/夏灌', 2),
    ('PI_2025_WANGJIANG', 'PlanY2025',   'WaterUserWangjiang26',  386100.00, 173745000.00, 165000000.00, '春灌/夏灌', 3),
    ('PI_2025_TAIHU',     'PlanY2025',   'WaterUserTaihu2026',    173300.00,  77985000.00,  74000000.00, '春灌/夏灌', 4),
    -- 2026 年 10 月（编制中）：按年度计划月均分解
    ('PI_202610_SUSONG',    'PlanM202610', 'WaterUserSusong2026',   370000.00, 166500000.00, 13875000.00, '秋灌', 1),
    ('PI_202610_HUAINING',  'PlanM202610', 'WaterUserHuaining26',   128900.00,  58005000.00,  4833750.00, '秋灌', 2),
    ('PI_202610_WANGJIANG', 'PlanM202610', 'WaterUserWangjiang26',  386100.00, 173745000.00, 14478750.00, '秋灌', 3),
    ('PI_202610_TAIHU',     'PlanM202610', 'WaterUserTaihu2026',    173300.00,  77985000.00,  6498750.00, '秋灌', 4),
    -- 2027 年度（已作废）：与 2026 同规模
    ('PI_2027_SUSONG',    'PlanY2027',   'WaterUserSusong2026',   370000.00, 166500000.00, 166500000.00, '春灌/夏灌', 1),
    ('PI_2027_HUAINING',  'PlanY2027',   'WaterUserHuaining26',   128900.00,  58005000.00,  58005000.00, '春灌/夏灌', 2),
    ('PI_2027_WANGJIANG', 'PlanY2027',   'WaterUserWangjiang26',  386100.00, 173745000.00, 173745000.00, '春灌/夏灌', 3),
    ('PI_2027_TAIHU',     'PlanY2027',   'WaterUserTaihu2026',    173300.00,  77985000.00,  77985000.00, '春灌/夏灌', 4)
) AS v(id, plan_id, water_user_id, irrigation_area, calc_water, plan_water, stage_tag, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_plan_item i
    WHERE i.plan_id = v.plan_id AND i.water_user_id = v.water_user_id
)
  AND EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_plan p
    WHERE p.corp_code = 'hltgq' AND p.id = v.plan_id
);

-- ----------------------------------------------------------------------------
-- 三、需水上报（5 条，覆盖 待审核 / 已通过 / 已驳回）
--     起止日期由「年 + 月 + 旬」派生：上旬 1-10 / 中旬 11-20 / 下旬 21-当月最后一天
--     用途取自字典 12 项白名单；编号按创建时间递增
--     audit_by 原样展示（无 JOIN），统一用「系统管理员」标识测试数据
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_demand
    (id, demand_no, water_user_id, gate_id, amount, start_date, end_date,
     period_year, period_month, xun_list, usage_desc, status, audit_opinion,
     audit_by, audit_at, remark, corp_code, created_at, created_by)
SELECT v.id, v.demand_no, v.water_user_id, v.gate_id, v.amount, v.start_date, v.end_date,
       v.period_year, v.period_month, v.xun_list, v.usage_desc, v.status, v.audit_opinion,
       v.audit_by, v.audit_at, v.remark, 'hltgq', v.created_at, 'SYSTEM'
FROM (VALUES
    -- 宿松县泡田申报（最早创建 → XS0001，已驳回）
    ('Demand20260901', 'XS0001', 'WaterUserSusong2026',   'ZhangdaGate20261027',
      3000000.00, DATE '2026-09-21', DATE '2026-09-30', 2026, 9, '3', '水稻泡田灌溉',
     '#3#', '申报水量已超出本年度计划剩余额度，请调整后重新上报', '系统管理员',
     TIMESTAMP '2026-09-20 09:30:00', '泡田用水申报',
     TIMESTAMP '2026-09-18 10:00:00'),
    -- 宿松县秋收后补水（已通过，关联 DD0002）
    ('Demand20261001', 'XS0002', 'WaterUserSusong2026',   'ZhangdaGate20261027',
     12000000.00, DATE '2026-10-01', DATE '2026-10-20', 2026, 10, '1,2', '秋收后补水',
     '#2#', NULL, '系统管理员', TIMESTAMP '2026-09-28 10:20:00', '秋收后渠道补水需求',
     TIMESTAMP '2026-09-26 09:15:00'),
    -- 怀宁县冬灌前补水（已通过，关联 DD0003）
    ('Demand20261002', 'XS0003', 'WaterUserHuaining26',   'jqEawVNMpMgzawQKXJb',
      5500000.00, DATE '2026-10-11', DATE '2026-10-20', 2026, 10, '2', '冬灌前补水',
     '#2#', NULL, '系统管理员', TIMESTAMP '2026-10-02 14:30:00', '冬灌前土壤底墒补水',
     TIMESTAMP '2026-09-30 16:40:00'),
    -- 望江县秋粮灌溉（已通过，关联 DD0004；审批 10-21 早于 10-25 开闸）
    ('Demand20261003', 'XS0004', 'WaterUserWangjiang26',  'cOGko9nLfFz6rRAvKvY',
     14000000.00, DATE '2026-10-21', DATE '2026-10-31', 2026, 10, '3', '秋粮灌溉',
     '#2#', NULL, '系统管理员', TIMESTAMP '2026-10-21 09:10:00', '秋粮灌溉用水申报',
     TIMESTAMP '2026-10-20 08:50:00'),
    -- 太湖县冬灌前补水（最晚创建 → XS0005，待审核）
    ('Demand20261101', 'XS0005', 'WaterUserTaihu2026',    'CAYQ739MiBWMg9gQvyi',
      8000000.00, DATE '2026-11-01', DATE '2026-11-10', 2026, 11, '1', '冬灌前补水',
     '#1#', NULL, NULL, NULL, '冬灌前渠首补水，待审核',
     TIMESTAMP '2026-10-26 11:05:00')
) AS v(id, demand_no, water_user_id, gate_id, amount, start_date, end_date,
       period_year, period_month, xun_list, usage_desc, status, audit_opinion,
       audit_by, audit_at, remark, created_at)
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_demand d
    WHERE d.corp_code = 'hltgq' AND d.demand_no = v.demand_no
);

-- ----------------------------------------------------------------------------
-- 四、调度指令（5 条，覆盖 待执行 / 执行中 / 已完成 / 已取消）
--     start_time = exec_time（新口径，历史列 end_time 留空）
--     闸口取自用水户 gate_config 且已在闸站表存在；plan_id 统一关联已生效的 2026 年度计划
--     配额校验：各户未取消计划放水量合计 ≤ 该户计划水量
--       宿松 800 万、怀宁 500 万、望江 1200 万、太湖 900 万（合计 3400 万 m³）
--     编号按创建时间递增：DD0001 已取消（09-18）→ DD0005 待执行（10-26）
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_dispatch
    (id, dispatch_no, plan_id, demand_id, water_user_id, gate_id, plan_amount,
     exec_time, finish_time, gate_height_cm, start_time, end_time, owner, status,
     bill_id, billed_qty, remark, corp_code, created_at, created_by)
SELECT v.id, v.dispatch_no, 'PlanY2026', v.demand_id, v.water_user_id, v.gate_id, v.plan_amount,
       v.exec_time, v.finish_time, v.gate_height_cm, v.exec_time, NULL, v.owner, v.status,
       NULL, NULL, v.remark, 'hltgq', v.created_at, 'SYSTEM'
FROM (VALUES
    -- 宿松县：9 月因上游来水不足取消 → 10 月重新下达并完成
    ('Dispatch20260901', 'DD0001', NULL,             'WaterUserSusong2026',   'ZhangdaGate20261027',
     4000000.00, TIMESTAMP '2026-09-20 08:00:00', NULL, 40, '张建国', '#4#',
     '因上游来水不足取消', TIMESTAMP '2026-09-18 11:00:00'),
    ('Dispatch20261001', 'DD0002', 'Demand20261001', 'WaterUserSusong2026',   'ZhangdaGate20261027',
     8000000.00, TIMESTAMP '2026-10-08 08:00:00', TIMESTAMP '2026-10-18 18:00:00', 45, '张建国', '#3#',
     '秋收后补水，按申报量下达', TIMESTAMP '2026-10-06 10:00:00'),
    ('Dispatch20261002', 'DD0003', 'Demand20261002', 'WaterUserHuaining26',   'jqEawVNMpMgzawQKXJb',
     5000000.00, TIMESTAMP '2026-10-12 08:00:00', TIMESTAMP '2026-10-22 17:00:00', 38, '李卫东', '#3#',
     '冬灌前底墒补水', TIMESTAMP '2026-10-10 09:30:00'),
    ('Dispatch20261003', 'DD0004', 'Demand20261003', 'WaterUserWangjiang26',  'cOGko9nLfFz6rRAvKvY',
     12000000.00, TIMESTAMP '2026-10-25 08:00:00', NULL, 50, '王海涛', '#2#',
     '南干渠秋粮灌溉放水，执行中', TIMESTAMP '2026-10-23 15:20:00'),
    ('Dispatch20261101', 'DD0005', NULL,             'WaterUserTaihu2026',    'CAYQ739MiBWMg9gQvyi',
     9000000.00, TIMESTAMP '2026-11-01 08:00:00', NULL, 30, '陈立新', '#1#',
     '渠首冬灌补水，待执行', TIMESTAMP '2026-10-26 16:00:00')
) AS v(id, dispatch_no, demand_id, water_user_id, gate_id, plan_amount,
       exec_time, finish_time, gate_height_cm, owner, status, remark, created_at)
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_dispatch d
    WHERE d.corp_code = 'hltgq' AND d.dispatch_no = v.dispatch_no
);

-- ----------------------------------------------------------------------------
-- 五、调度记录（8 条，闸口操作留痕；关联指令时户与闸口必须一致）
--     op_type 为历史列固定 #1#；新口径只写操作后闸门高度 gate_height_after
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_dispatch_record
    (id, dispatch_id, water_user_id, gate_id, op_time, op_type, height,
     gate_height_after, operator, remark, corp_code, created_at, created_by)
SELECT v.id, v.dispatch_id, v.water_user_id, v.gate_id, v.op_time, '#1#', NULL,
       v.gate_height_after, v.operator, v.remark, 'hltgq', v.op_time, 'SYSTEM'
FROM (VALUES
    ('Record2026100101', 'Dispatch20261001', 'WaterUserSusong2026',  'ZhangdaGate20261027',
     TIMESTAMP '2026-10-08 08:10:00', 45, '张建国', '接调度指令开闸，初始开度 45cm'),
    ('Record2026100102', 'Dispatch20261001', 'WaterUserSusong2026',  'ZhangdaGate20261027',
     TIMESTAMP '2026-10-13 09:00:00', 52, '张建国', '根据下游水情加大流量至 52cm'),
    ('Record2026100103', 'Dispatch20261001', 'WaterUserSusong2026',  'ZhangdaGate20261027',
     TIMESTAMP '2026-10-18 18:05:00',  0, '张建国', '按调度指令关闸'),
    ('Record2026100201', 'Dispatch20261002', 'WaterUserHuaining26',  'jqEawVNMpMgzawQKXJb',
     TIMESTAMP '2026-10-12 08:20:00', 38, '李卫东', '开闸放水，开度 38cm'),
    ('Record2026100202', 'Dispatch20261002', 'WaterUserHuaining26',  'jqEawVNMpMgzawQKXJb',
     TIMESTAMP '2026-10-22 17:40:00',  0, '李卫东', '完成放水关闸'),
    ('Record2026100301', 'Dispatch20261003', 'WaterUserWangjiang26', 'cOGko9nLfFz6rRAvKvY',
     TIMESTAMP '2026-10-25 08:15:00', 50, '王海涛', '南干渠开闸，开度 50cm'),
    ('Record2026102601', NULL,               'WaterUserTaihu2026',   'CAYQ739MiBWMg9gQvyi',
     TIMESTAMP '2026-10-26 09:00:00', 30, '陈立新', '渠首闸门调整，配合下游冬灌准备'),
    ('Record2026102701', NULL,               'WaterUserWangjiang26', '4Ieu32Stnz8dTD8S8GD',
     TIMESTAMP '2026-10-27 10:00:00', 20, '王海涛', '北干渠闸门例行调整，配合南干渠轮灌')
) AS v(id, dispatch_id, water_user_id, gate_id, op_time, gate_height_after, operator, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_dispatch_record r
    WHERE r.corp_code = 'hltgq' AND r.id = v.id
);

-- ----------------------------------------------------------------------------
-- 六、手工水费台账（8 条 = 4 县 × 2 个年度）
--     单价与结算周期直接 JOIN 用水户档案取值，金额 = ROUND(用水量 × 单价, 2)
--     已出账的 bill_id 在第七节回填
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_ledger
    (id, ledger_no, water_user_id, settle_cycle, cycle_key, volume, price, amount,
     bill_id, remark, corp_code, created_at, created_by)
SELECT v.id, v.ledger_no, v.water_user_id, u.settle_cycle, v.cycle_key, v.volume,
       u.water_price, ROUND(v.volume * u.water_price, 2),
       NULL, v.remark, 'hltgq', v.created_at, 'SYSTEM'
FROM (VALUES
    ('Ledger2025YH006', 'TZ2025-YH0060001', 'WaterUserSusong2026',   '2025',
     152000000.00, '2025 年度用水量结算', TIMESTAMP '2026-01-08 10:00:00'),
    ('Ledger2025YH007', 'TZ2025-YH0070002', 'WaterUserHuaining26',   '2025',
      52000000.00, '2025 年度用水量结算', TIMESTAMP '2026-01-08 10:05:00'),
    ('Ledger2025YH008', 'TZ2025-YH0080003', 'WaterUserWangjiang26',  '2025',
     158000000.00, '2025 年度用水量结算', TIMESTAMP '2026-01-08 10:10:00'),
    ('Ledger2025YH009', 'TZ2025-YH0090004', 'WaterUserTaihu2026',    '2025',
      71000000.00, '2025 年度用水量结算', TIMESTAMP '2026-01-08 10:15:00'),
    ('Ledger2026YH006', 'TZ2026-YH0060005', 'WaterUserSusong2026',   '2026',
     160000000.00, '2026 年度用水量结算', TIMESTAMP '2026-10-20 09:00:00'),
    ('Ledger2026YH007', 'TZ2026-YH0070006', 'WaterUserHuaining26',   '2026',
      55000000.00, '2026 年度用水量结算', TIMESTAMP '2026-10-20 09:05:00'),
    ('Ledger2026YH008', 'TZ2026-YH0080007', 'WaterUserWangjiang26',  '2026',
     166000000.00, '2026 年度用水量结算', TIMESTAMP '2026-10-20 09:10:00'),
    ('Ledger2026YH009', 'TZ2026-YH0090008', 'WaterUserTaihu2026',    '2026',
      74000000.00, '2026 年度用水量结算', TIMESTAMP '2026-10-20 09:15:00')
) AS v(id, ledger_no, water_user_id, cycle_key, volume, remark, created_at)
JOIN "qixiao-apaas".t_auto_hltgq_water_user u
  ON u.id = v.water_user_id AND u.corp_code = 'hltgq'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_ledger l
    WHERE l.corp_code = 'hltgq' AND l.ledger_no = v.ledger_no
);

-- ----------------------------------------------------------------------------
-- 七、水费账单（8 条 = 4 县 × 2 个年度；2025 年度已缴清、2026 年度已生成）
--     quantity = 台账用水量，price/amount 按用水户档案水价计算；ledger_id 指向台账
--     已缴清（#2#）时 paid = amount；未缴（#1#）paid = 0
--     账期区间按年度推导：当年 01-01 ~ 12-31
-- ----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas".t_auto_hltgq_water_bill
    (id, bill_no, water_user_id, settle_cycle, cycle_key, period_start, period_end,
     quantity, price, amount, paid, status, pay_time, ledger_id, corp_code, created_at, created_by)
SELECT v.id, v.bill_no, v.water_user_id, u.settle_cycle, v.cycle_key, v.period_start, v.period_end,
       v.quantity, u.water_price, ROUND(v.quantity * u.water_price, 2),
       CASE WHEN v.status = '#2#' THEN ROUND(v.quantity * u.water_price, 2) ELSE 0.00 END,
       v.status, v.pay_time, v.ledger_id, 'hltgq', v.created_at, 'SYSTEM'
FROM (VALUES
    -- 2025 年度：均已缴清，收款时间在 2026 年 1 月
    ('Bill2025YH006', 'SF2025-YH0060001', 'WaterUserSusong2026',  '2025',
     DATE '2025-01-01', DATE '2025-12-31', 152000000.00, '#2#',
     TIMESTAMP '2026-01-15 10:30:00', 'Ledger2025YH006', TIMESTAMP '2026-01-08 10:20:00'),
    ('Bill2025YH007', 'SF2025-YH0070002', 'WaterUserHuaining26',  '2025',
     DATE '2025-01-01', DATE '2025-12-31',  52000000.00, '#2#',
     TIMESTAMP '2026-01-15 10:35:00', 'Ledger2025YH007', TIMESTAMP '2026-01-08 10:25:00'),
    ('Bill2025YH008', 'SF2025-YH0080003', 'WaterUserWangjiang26', '2025',
     DATE '2025-01-01', DATE '2025-12-31', 158000000.00, '#2#',
     TIMESTAMP '2026-01-15 10:40:00', 'Ledger2025YH008', TIMESTAMP '2026-01-08 10:30:00'),
    ('Bill2025YH009', 'SF2025-YH0090004', 'WaterUserTaihu2026',   '2025',
     DATE '2025-01-01', DATE '2025-12-31',  71000000.00, '#2#',
     TIMESTAMP '2026-01-15 10:45:00', 'Ledger2025YH009', TIMESTAMP '2026-01-08 10:35:00'),
    -- 2026 年度：已生成待收款
    ('Bill2026YH006', 'SF2026-YH0060005', 'WaterUserSusong2026',  '2026',
     DATE '2026-01-01', DATE '2026-12-31', 160000000.00, '#1#',
     NULL, 'Ledger2026YH006', TIMESTAMP '2026-10-20 09:30:00'),
    ('Bill2026YH007', 'SF2026-YH0070006', 'WaterUserHuaining26',  '2026',
     DATE '2026-01-01', DATE '2026-12-31',  55000000.00, '#1#',
     NULL, 'Ledger2026YH007', TIMESTAMP '2026-10-20 09:35:00'),
    ('Bill2026YH008', 'SF2026-YH0080007', 'WaterUserWangjiang26', '2026',
     DATE '2026-01-01', DATE '2026-12-31', 166000000.00, '#1#',
     NULL, 'Ledger2026YH008', TIMESTAMP '2026-10-20 09:40:00'),
    ('Bill2026YH009', 'SF2026-YH0090008', 'WaterUserTaihu2026',   '2026',
     DATE '2026-01-01', DATE '2026-12-31',  74000000.00, '#1#',
     NULL, 'Ledger2026YH009', TIMESTAMP '2026-10-20 09:45:00')
) AS v(id, bill_no, water_user_id, cycle_key, period_start, period_end,
       quantity, status, pay_time, ledger_id, created_at)
JOIN "qixiao-apaas".t_auto_hltgq_water_user u
  ON u.id = v.water_user_id AND u.corp_code = 'hltgq'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas".t_auto_hltgq_water_bill b
    WHERE b.corp_code = 'hltgq' AND b.bill_no = v.bill_no
);

-- 回填台账 → 账单挂接（已出账标记，双向一致）
UPDATE "qixiao-apaas".t_auto_hltgq_water_ledger l
SET bill_id = b.id, updated_at = b.created_at, updated_by = 'SYSTEM'
FROM "qixiao-apaas".t_auto_hltgq_water_bill b
WHERE b.ledger_id = l.id AND l.corp_code = 'hltgq' AND l.bill_id IS NULL;

COMMIT;

-- ============================================================================
-- 执行后自检（应全部返回 0 或符合预期）
-- ============================================================================
-- 1) 计划总水量 / 已调度水量 与明细、调度指令是否一致（应返回 0 行）
SELECT p.plan_no, p.plan_name, p.total_water,
       (SELECT COALESCE(SUM(i.plan_water), 0) FROM "qixiao-apaas".t_auto_hltgq_water_plan_item i
         WHERE i.plan_id = p.id) AS item_sum,
       p.scheduled_qty,
       (SELECT COALESCE(SUM(d.plan_amount), 0) FROM "qixiao-apaas".t_auto_hltgq_water_dispatch d
         WHERE d.plan_id = p.id AND d.status <> '#4#') AS dispatch_sum
FROM "qixiao-apaas".t_auto_hltgq_water_plan p
WHERE p.corp_code = 'hltgq'
  AND (p.total_water <> (SELECT COALESCE(SUM(i.plan_water), 0)
                           FROM "qixiao-apaas".t_auto_hltgq_water_plan_item i WHERE i.plan_id = p.id)
    OR p.scheduled_qty <> (SELECT COALESCE(SUM(d.plan_amount), 0)
                             FROM "qixiao-apaas".t_auto_hltgq_water_dispatch d
                            WHERE d.plan_id = p.id AND d.status <> '#4#'));

-- 2) 账单金额 / 台账双向挂接 / 缴清三要素是否自洽（应返回 0 行）
SELECT b.bill_no, b.quantity, b.price, b.amount, b.status, b.paid, b.pay_time,
       b.ledger_id, l.id AS ledger_found, l.bill_id AS ledger_back_ref
FROM "qixiao-apaas".t_auto_hltgq_water_bill b
LEFT JOIN "qixiao-apaas".t_auto_hltgq_water_ledger l ON l.id = b.ledger_id
WHERE b.corp_code = 'hltgq'
  AND (b.amount <> ROUND(b.quantity * b.price, 2)
    OR b.ledger_id IS NULL OR l.id IS NULL OR l.bill_id IS DISTINCT FROM b.id
    OR (b.status = '#2#' AND (b.paid IS DISTINCT FROM b.amount OR b.pay_time IS NULL)));

-- 3) 调度指令闸口是否属于该用水户的闸口组（应返回 0 行）
SELECT d.dispatch_no, u.name AS user_name, d.gate_id
FROM "qixiao-apaas".t_auto_hltgq_water_dispatch d
JOIN "qixiao-apaas".t_auto_hltgq_water_user u ON u.id = d.water_user_id
WHERE d.corp_code = 'hltgq'
  AND u.gate_config NOT LIKE '%' || d.gate_id || '%';

-- 4) 各表数据量总览
SELECT '用水户'   AS "模块", COUNT(*) AS "条数" FROM "qixiao-apaas".t_auto_hltgq_water_user    WHERE corp_code = 'hltgq'
UNION ALL SELECT '定额标准', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_quota         WHERE corp_code = 'hltgq'
UNION ALL SELECT '用水计划', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_plan          WHERE corp_code = 'hltgq'
UNION ALL SELECT '计划明细', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_plan_item     WHERE corp_code = 'hltgq'
UNION ALL SELECT '需水上报', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_demand        WHERE corp_code = 'hltgq'
UNION ALL SELECT '调度指令', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_dispatch      WHERE corp_code = 'hltgq'
UNION ALL SELECT '调度记录', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_dispatch_record WHERE corp_code = 'hltgq'
UNION ALL SELECT '手工台账', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_ledger        WHERE corp_code = 'hltgq'
UNION ALL SELECT '水费账单', COUNT(*) FROM "qixiao-apaas".t_auto_hltgq_water_bill          WHERE corp_code = 'hltgq';

-- ============================================================================
-- 回滚（清空本脚本生成的数据；按引用逆序，用水户与原有 2 条定额不受影响）
-- 仅需清理时手工取消注释执行，正常情况不用跑
-- ============================================================================
-- BEGIN;
-- UPDATE "qixiao-apaas".t_auto_hltgq_water_ledger SET bill_id = NULL, updated_at = NULL, updated_by = NULL
--  WHERE corp_code = 'hltgq' AND (id LIKE 'Ledger2025YH%' OR id LIKE 'Ledger2026YH%');
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_bill                WHERE corp_code = 'hltgq' AND id LIKE 'Bill20%';
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_ledger              WHERE corp_code = 'hltgq' AND id LIKE 'Ledger20%';
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_dispatch_record     WHERE corp_code = 'hltgq' AND id LIKE 'Record2026%';
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_dispatch            WHERE corp_code = 'hltgq' AND id LIKE 'Dispatch2026%';
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_demand              WHERE corp_code = 'hltgq' AND id LIKE 'Demand2026%';
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_plan_item           WHERE corp_code = 'hltgq' AND id LIKE 'PI\_%';
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_plan                WHERE corp_code = 'hltgq'
--   AND id IN ('PlanY2025', 'PlanY2026', 'PlanM202610', 'PlanY2027');
-- DELETE FROM "qixiao-apaas".t_auto_hltgq_water_quota               WHERE corp_code = 'hltgq' AND id LIKE 'Quota%';
-- COMMIT;
