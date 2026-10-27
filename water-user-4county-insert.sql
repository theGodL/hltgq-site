-- =============================================================================
-- 四县用水户录入（方案 A：严格按业主口径截图，缺数留空不代用）
-- 生成时间：2026-10-27
--
-- 口径依据（业主资料原文）：
--   1. 宿松县 = 章大进水闸过闸流量            → 站档无此站，本脚本先建站（无设备、离线）
--   2. 怀宁县 = 长河槽进水闸流量              → 站档为「太怀干渠长喉槽」(jqEawVNMpMgzawQKXJb)，停用无数据
--   3. 望江县 = 南进水闸 + 北进水闸           → 站档「南干渠进水闸」「北干渠进水闸」，均有数据
--   4. 太湖县 = 总流量 − 上述几站              → 渠首进水闸为主闸口，上述四站为扣减
--      （截图中"三个水电站的流量以及损耗的流量"已划掉，故不纳入）
--
-- 预期结果（大屏 /h5/water-resource/overview）：
--   宿松 null、怀宁 null、望江 ≈4912.657 万m³、太湖 null（扣减项缺读数）
--
-- 执行前必读：
--   * contact_name 列是 NOT NULL，无法真正留空，本脚本填「待补充」占位
--   * 页面上「编辑」这四个户时，后端强制要求从下拉选择联系人，否则保存报"请选择联系人"
--   * 水价 0.10 元/m³ 为占位值，可在用水户管理页随时改；实际账单由业务在手工台账里自填水量与单价
--   * 面积单位「亩」，来源为片区面积表（万亩）× 10000
--   * 本脚本幂等：站点/用水户按 id 判重，重复执行不会重复插入
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 第 1 步：新建站点「章大进水闸」（无设备、站码留空，待设备接入后补 iofhpi）
--   站点类型 #3#=流量站、#4#=闸站 —— 两者必须至少有一个，否则不进闸口下拉
--   站点状态 #1#=启用；离线由设备侧判定，站点本身不标停用
--   上级站点 = 宿松县管理所（FromJ3KVVC2d5PwwrRY）
-- -----------------------------------------------------------------------------
INSERT INTO "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
    ("id", "corp_code", "created_at", "created_by", "updated_at", "updated_by",
     "zzkaec", "mivbcz", "epjutj", "zebpsu", "ahieto")
SELECT 'ZhangdaGate20261027', 'hltgq', now(), 'SYSTEM', now(), 'SYSTEM',
       '章大进水闸', '章大进水闸', '#3#|#4#', '#1#', 'FromJ3KVVC2d5PwwrRY'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" WHERE "id" = 'ZhangdaGate20261027'
);

-- -----------------------------------------------------------------------------
-- 第 2 步：四县用水户
--   user_type  #1#=农业（农业/生态必须填灌溉面积）
--   calc_mode  #1#=指定闸口累加  #2#=闸口差额计量
--   settle_cycle #3#=年度结算
--   status     #1#=正常
--   gate_config 内 name 为站档名称快照（与站档 zzkaec 一致，便于列表直读）
-- -----------------------------------------------------------------------------

-- 宿松县：章大进水闸（新建站，无设备 → 供水为 null）
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_user"
    ("id", "user_no", "name", "user_type", "contact_name", "contact_phone",
     "irrigation_area", "water_price", "calc_mode", "gate_config", "settle_cycle", "status",
     "corp_code", "created_at", "created_by", "updated_at", "updated_by")
SELECT 'WaterUserSusong2026', 'YH006', '宿松县', '#1#', '待补充', NULL::varchar(20),
       370000.00, 0.100, '#1#',
       '{"mode":"SUM","gates":[{"id":"ZhangdaGate20261027","name":"章大进水闸"}]}',
       '#3#', '#1#', 'hltgq', now(), 'SYSTEM', now(), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_user" WHERE "id" = 'WaterUserSusong2026'
);

-- 怀宁县：太怀干渠长喉槽（业主称"长河槽进水闸"，站档停用无数据 → 供水为 null）
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_user"
    ("id", "user_no", "name", "user_type", "contact_name", "contact_phone",
     "irrigation_area", "water_price", "calc_mode", "gate_config", "settle_cycle", "status",
     "corp_code", "created_at", "created_by", "updated_at", "updated_by")
SELECT 'WaterUserHuaining26', 'YH007', '怀宁县', '#1#', '待补充', NULL::varchar(20),
       128900.00, 0.100, '#1#',
       '{"mode":"SUM","gates":[{"id":"jqEawVNMpMgzawQKXJb","name":"太怀干渠长喉槽"}]}',
       '#3#', '#1#', 'hltgq', now(), 'SYSTEM', now(), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_user" WHERE "id" = 'WaterUserHuaining26'
);

-- 望江县：南干渠进水闸 + 北干渠进水闸（均有数据）
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_user"
    ("id", "user_no", "name", "user_type", "contact_name", "contact_phone",
     "irrigation_area", "water_price", "calc_mode", "gate_config", "settle_cycle", "status",
     "corp_code", "created_at", "created_by", "updated_at", "updated_by")
SELECT 'WaterUserWangjiang26', 'YH008', '望江县', '#1#', '待补充', NULL::varchar(20),
       386100.00, 0.100, '#1#',
       '{"mode":"SUM","gates":[{"id":"cOGko9nLfFz6rRAvKvY","name":"南干渠进水闸"},{"id":"4Ieu32Stnz8dTD8S8GD","name":"北干渠进水闸"}]}',
       '#3#', '#1#', 'hltgq', now(), 'SYSTEM', now(), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_user" WHERE "id" = 'WaterUserWangjiang26'
);

-- 太湖县：渠首进水闸 − 宿松/怀宁/望江四站（差额计量）
--   main 单对象为主闸口兼容字段（旧版解析方读取），与 mains 首个保持一致
INSERT INTO "qixiao-apaas"."t_auto_hltgq_water_user"
    ("id", "user_no", "name", "user_type", "contact_name", "contact_phone",
     "irrigation_area", "water_price", "calc_mode", "gate_config", "settle_cycle", "status",
     "corp_code", "created_at", "created_by", "updated_at", "updated_by")
SELECT 'WaterUserTaihu2026', 'YH009', '太湖县', '#1#', '待补充', NULL::varchar(20),
       173300.00, 0.100, '#2#',
       '{"mode":"DIFF","mains":[{"id":"CAYQ739MiBWMg9gQvyi","name":"渠首进水闸"}],"main":{"id":"CAYQ739MiBWMg9gQvyi","name":"渠首进水闸"},"deduct":[{"id":"ZhangdaGate20261027","name":"章大进水闸"},{"id":"jqEawVNMpMgzawQKXJb","name":"太怀干渠长喉槽"},{"id":"cOGko9nLfFz6rRAvKvY","name":"南干渠进水闸"},{"id":"4Ieu32Stnz8dTD8S8GD","name":"北干渠进水闸"}]}',
       '#3#', '#1#', 'hltgq', now(), 'SYSTEM', now(), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM "qixiao-apaas"."t_auto_hltgq_water_user" WHERE "id" = 'WaterUserTaihu2026'
);

-- -----------------------------------------------------------------------------
-- 第 3 步：执行后自检（应各返回 1 行 / 合计 4 行）
-- -----------------------------------------------------------------------------
-- 站点是否进入闸口下拉范围（epjutj 含 #3# 或 #4#）
SELECT s.id, s.zzkaec, s.epjutj, s.zebpsu
FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef" s
WHERE s.id = 'ZhangdaGate20261027';

-- 四户档案
SELECT u.user_no, u.name, u.user_type, u.calc_mode, u.settle_cycle, u.status,
       u.irrigation_area, u.water_price, u.contact_name, u.gate_config
FROM "qixiao-apaas"."t_auto_hltgq_water_user" u
WHERE u.id IN ('WaterUserSusong2026', 'WaterUserHuaining26',
               'WaterUserWangjiang26', 'WaterUserTaihu2026')
ORDER BY u.user_no;

COMMIT;

-- =============================================================================
-- 回滚（如需撤销本次录入，单独执行以下两段）
-- =============================================================================
-- DELETE FROM "qixiao-apaas"."t_auto_hltgq_water_user"
--  WHERE "id" IN ('WaterUserSusong2026', 'WaterUserHuaining26',
--                 'WaterUserWangjiang26', 'WaterUserTaihu2026');
-- DELETE FROM "qixiao-apaas"."t_auto_hltgq_5nw74_vnqqef"
--  WHERE "id" = 'ZhangdaGate20261027';
